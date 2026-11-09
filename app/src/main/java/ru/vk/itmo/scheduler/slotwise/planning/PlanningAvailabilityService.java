package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.account.AccountEntity;
import ru.vk.itmo.scheduler.slotwise.account.AccountRepository;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;
import ru.vk.itmo.scheduler.slotwise.university.TimetableEntryEntity;
import ru.vk.itmo.scheduler.slotwise.university.TimetableEntryRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import ru.vk.itmo.scheduler.slotwise.planning.api.dto.BusyCalendar;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.BusyCalendar.IntervalRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.ChangeCollectionRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CollectionResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CollectionResponse.ParticipantResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionRules;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionVersionRequest;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanningAvailabilityService {
    private static final Set<String> WEIGHT_KEYS = Set.of("S1", "S2", "S3", "S4", "S5");
    private final PlanningSessionRepository sessions;
    private final SessionParticipantRepository participants;
    private final BusyIntervalRepository busyIntervals;
    private final SessionWorkingDayRepository workingDays;
    private final SessionRuleRepository rules;
    private final AccountRepository accounts;
    private final TimetableEntryRepository timetable;
    private final BusyIntervalNormalizer normalizer;
    private final JsonMapper json;
    private final Clock clock;

    public BusyCalendar.Response busy(long id, long user) {
        var session = session(id, false);
        return busyResponse(session, participant(id, user));
    }

    @Transactional
    public BusyCalendar.Response saveBusy(long id, long user, BusyCalendar.ReplaceRequest request) {
        var session = session(id, true);
        var participant = participant(id, user);
        requireOpen(session);
        requireRevision(participant, request.expectedRevision());
        for (IntervalRequest interval : request.intervals()) {
            if (!interval.startsAt().isBefore(interval.endsAt())) {
                throw invalid("INVALID_INTERVAL", "Начало интервала должно быть раньше конца");
            }
        }
        List<IntervalRequest> normalized = normalizer.normalize(request.intervals(), windows(session));
        List<IntervalRequest> previous = personal(id, user);
        if (!normalized.equals(previous)) {
            busyIntervals.deletePersonal(id, user);
            for (IntervalRequest interval : normalized) {
                var entity = new BusyIntervalEntity();
                entity.setPlanningSessionId(id);
                entity.setAccountId(user);
                entity.setStartsAt(interval.startsAt());
                entity.setEndsAt(interval.endsAt());
                entity.setImportance(interval.importance());
                busyIntervals.save(entity);
            }
            // Editing a submitted calendar keeps its submitted status until collection closes.
            bumpCalendar(participant);
            bump(session);
        }
        return busyResponse(session, participant);
    }

    @Transactional
    public BusyCalendar.Response submit(long id, long user, BusyCalendar.SubmitRequest request) {
        var session = session(id, true);
        var participant = participant(id, user);
        requireOpen(session);
        requireRevision(participant, request.expectedRevision());
        if (participant.getResponseStatus() != ResponseStatus.SUBMITTED) {
            participant.setResponseStatus(ResponseStatus.SUBMITTED);
            participant.setSubmittedAt(clock.instant());
            bumpCalendar(participant);
            bump(session);
        }
        return busyResponse(session, participant);
    }

    public CollectionResponse collection(long id, long user) {
        var session = session(id, false);
        requireOwner(session, user);
        return collectionResponse(session);
    }

    @Transactional
    public CollectionResponse changeCollection(long id, long user, ChangeCollectionRequest request) {
        var session = session(id, true);
        requireOwner(session, user);
        requireVersion(session, request.expectedVersion());
        if (status(session) == CollectionStatus.CLOSED) {
            throw closed();
        }
        if (!request.endsAt().isAfter(clock.instant())
                || !request.endsAt().isAfter(session.getCollectionStartsAt())
                || !request.endsAt().isBefore(session.getStartsAt())) {
            throw invalid("INVALID_COLLECTION_DEADLINE", "Дедлайн должен быть после начала сбора, в будущем и до начала сессии");
        }
        if (!request.endsAt().equals(session.getCollectionEndsAt())) {
            session.setCollectionEndsAt(request.endsAt());
            bump(session);
        }
        sessions.flush();
        return collectionResponse(session);
    }

    @Transactional
    public CollectionResponse close(long id, long user, SessionVersionRequest request) {
        var session = session(id, true);
        requireOwner(session, user);
        requireVersion(session, request.expectedVersion());
        if (status(session) == CollectionStatus.PENDING) {
            throw conflict("COLLECTION_NOT_OPEN", "Сбор ещё не начался");
        }
        if (session.getCollectionStatus() != CollectionStatus.CLOSED) {
            session.setCollectionStatus(CollectionStatus.CLOSED);
            bump(session);
        }
        sessions.flush();
        return collectionResponse(session);
    }

    public SessionRules.Response rules(long id, long user) {
        var session = session(id, false);
        requireOwner(session, user);
        return rulesResponse(session, rules.findById(id).orElse(null));
    }

    @Transactional
    public SessionRules.Response saveRules(long id, long user, SessionRules.UpdateRequest request) {
        var session = session(id, true);
        requireOwner(session, user);
        requireVersion(session, request.expectedVersion());
        if (request.roomChangeBufferMinutes() % TimeGrid.DEFAULT_SLOT_MINUTES != 0) {
            throw invalid("INVALID_BUFFER", "Буфер должен быть кратен длине слота");
        }
        if (request.timeLimitSeconds() < 1 || request.timeLimitSeconds() > 300) {
            throw invalid("INVALID_TIME_LIMIT", "Лимит времени должен быть от 1 до 300 секунд");
        }
        var entity = rules.findById(id).orElse(null);
        var before = rulesResponse(session, entity);
        var desired = new SessionRules.Response(session.getVersion(), session.getInputVersion(), TimeGrid.DEFAULT_SLOT_MINUTES, request.weights(),
                request.roomChangeBufferMinutes(), request.defaultAlgorithm(), request.timeLimitSeconds(), request.seed());
        if (entity == null || !before.equals(desired)) {
            if (entity == null) {
                entity = new SessionRuleEntity();
                entity.setPlanningSessionId(id);
            }
            entity.setWeights(json.writeValueAsString(Map.of("S1", request.weights().teacherGaps(),
                    "S2", request.weights().commissionLoadSpread(), "S3", request.weights().undesirableTime(),
                    "S4", request.weights().teacherRoomChanges(), "S5", request.weights().movedEvents())));
            entity.setRoomChangeBufferMinutes(request.roomChangeBufferMinutes());
            entity.setDefaultAlgorithm(request.defaultAlgorithm());
            entity.setTimeLimitSeconds(request.timeLimitSeconds());
            entity.setSeed(request.seed());
            entity = rules.save(entity);
            bump(session);
        }
        sessions.flush();
        return rulesResponse(session, entity);
    }

    private BusyCalendar.Response busyResponse(PlanningSessionEntity session, SessionParticipantEntity participant) {
        List<IntervalRequest> personal = personal(session.getId(), participant.getAccountId());
        List<TimetableEntryEntity> known = known(session, List.of(participant.getAccountId()));
        List<ApiIssue> issues = new ArrayList<>();
        if (known.isEmpty()) {
            issues.add(warning("NO_TIMETABLE", "Учебное расписание отсутствует", participant.getAccountId()));
        }
        if (normalizer.noFreeTime(withKnown(personal, known), windows(session))) {
            issues.add(warning("NO_FREE_TIME", "В рабочих часах сессии нет свободного времени", participant.getAccountId()));
        }
        AccountEntity account = accounts.findById(participant.getAccountId()).orElseThrow();
        if (account.getRole() == AccountRole.STUDENT
                && personal.stream().anyMatch(i -> i.importance() == BusyImportance.CRITICAL)) {
            issues.add(warning("STUDENT_CRITICAL_IS_SOFT_FOR_GROUP_EVENTS",
                    "Критическая занятость студента — мягкое ограничение групповых событий", participant.getAccountId()));
        }
        var saved = busyIntervals.findByPlanningSessionIdAndAccountIdOrderByStartsAt(session.getId(), participant.getAccountId());
        return new BusyCalendar.Response(session.getId(), revision(participant), session.getTimeZone(),
                participant.getResponseStatus(), participant.getSubmittedAt(), status(session) == CollectionStatus.OPEN,
                !known.isEmpty(), saved.stream().map(i -> new BusyCalendar.Interval(i.getId(), i.getStartsAt(), i.getEndsAt(), i.getImportance())).toList(),
                known.stream().map(t -> new BusyCalendar.KnownBusy(t.getStartsAt(), t.getEndsAt(), t.getDescription())).toList(),
                List.copyOf(issues));
    }

    private static ApiIssue warning(String code, String message, long user) {
        return new ApiIssue(code, message, IssueSeverity.WARNING, List.of(), List.of(user), List.of());
    }

    private CollectionResponse collectionResponse(PlanningSessionEntity session) {
        var members = participants.findByPlanningSessionIdOrderByAccountId(session.getId());
        var ids = members.stream().map(SessionParticipantEntity::getAccountId).toList();
        var accountMap = accounts.findAllById(ids).stream().collect(Collectors.toMap(AccountEntity::getId, a -> a));
        var personal = busyIntervals.findByPlanningSessionIdOrderByStartsAt(session.getId()).stream()
                .collect(Collectors.groupingBy(BusyIntervalEntity::getAccountId));
        var known = known(session, ids).stream().collect(Collectors.groupingBy(TimetableEntryEntity::getAccountId));
        var windows = windows(session);
        List<ParticipantResponse> result = new ArrayList<>();
        for (var member : members) {
            var account = accountMap.get(member.getAccountId());
            var intervals = personal.getOrDefault(member.getAccountId(), List.of()).stream()
                    .map(PlanningAvailabilityService::interval).toList();
            boolean noFreeTime = normalizer.noFreeTime(
                    withKnown(intervals, known.getOrDefault(member.getAccountId(), List.of())), windows);
            result.add(new ParticipantResponse(new PersonSummary(account.getId(), account.getLastName(), account.getFirstName(),
                    account.getPatronymic(), account.getRole()), member.getResponseStatus(), member.getSubmittedAt(), noFreeTime));
        }
        return new CollectionResponse(status(session), session.getCollectionStartsAt(), session.getCollectionEndsAt(),
                session.getVersion(), session.getInputVersion(), List.copyOf(result));
    }

    private SessionRules.Response rulesResponse(PlanningSessionEntity session, SessionRuleEntity entity) {
        var defaults = Weights.DEFAULT;
        Map<String, Integer> weights = new TreeMap<>(Map.of("S1", defaults.gaps(), "S2", defaults.unfairness(),
                "S3", defaults.undesirable(), "S4", defaults.roomSwitches(), "S5", defaults.moves()));
        if (entity != null) {
            var stored = json.readTree(entity.getWeights());
            for (String key : WEIGHT_KEYS) {
                if (stored.has(key)) {
                    weights.put(key, stored.get(key).intValue());
                }
            }
        }
        var contractWeights = new SessionRules.Weights(weights.get("S1"), weights.get("S2"), weights.get("S3"),
                weights.get("S4"), weights.get("S5"));
        return new SessionRules.Response(session.getVersion(), session.getInputVersion(), TimeGrid.DEFAULT_SLOT_MINUTES,
                contractWeights, entity == null ? 15 : entity.getRoomChangeBufferMinutes(),
                entity == null ? SolverAlgorithm.GREEDY : entity.getDefaultAlgorithm(),
                entity == null ? 60 : entity.getTimeLimitSeconds(), entity == null ? 0 : entity.getSeed());
    }

    private PlanningSessionEntity session(long id, boolean lock) {
        return (lock ? sessions.findForUpdate(id) : sessions.findById(id))
                .orElseThrow(() -> new PlanningException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена"));
    }

    private SessionParticipantEntity participant(long id, long user) {
        var key = new SessionParticipantEntity.Key();
        key.setPlanningSessionId(id);
        key.setAccountId(user);
        return participants.findById(key).orElseThrow(() -> new AccessDeniedException("Не участник сессии"));
    }

    private static void requireOwner(PlanningSessionEntity session, long user) {
        if (session.getOrganizerId() != user) {
            throw new AccessDeniedException("Не владелец сессии");
        }
    }

    private CollectionStatus status(PlanningSessionEntity session) {
        Instant now = clock.instant();
        if (session.getCollectionStatus() == CollectionStatus.CLOSED || !now.isBefore(session.getCollectionEndsAt())) {
            return CollectionStatus.CLOSED;
        }
        if (now.isBefore(session.getCollectionStartsAt())) {
            return CollectionStatus.PENDING;
        }
        return CollectionStatus.OPEN;
    }

    private void requireOpen(PlanningSessionEntity session) {
        var status = status(session);
        if (status == CollectionStatus.CLOSED) {
            throw closed();
        }
        if (status != CollectionStatus.OPEN) {
            throw conflict("COLLECTION_NOT_OPEN", "Сбор ещё не начался");
        }
    }

    private static void requireVersion(PlanningSessionEntity session, long expected) {
        if (session.getVersion() != expected) {
            throw conflict("STALE_SESSION_VERSION", "Данные сессии изменились; обновите их и повторите запрос");
        }
    }

    private static String revision(SessionParticipantEntity participant) {
        return "v" + participant.getCalendarRevision();
    }

    private static void requireRevision(SessionParticipantEntity participant, String expected) {
        if (!revision(participant).equals(expected)) {
            throw conflict("STALE_REVISION", "Календарь изменился; обновите данные и повторите запрос");
        }
    }

    private static void bumpCalendar(SessionParticipantEntity participant) {
        participant.setCalendarRevision(Math.incrementExact(participant.getCalendarRevision()));
    }

    private static void bump(PlanningSessionEntity session) {
        session.setInputVersion(Math.incrementExact(session.getInputVersion()));
    }

    private List<BusyIntervalNormalizer.Window> windows(PlanningSessionEntity session) {
        Set<Integer> weekdays = workingDays.findByPlanningSessionId(session.getId()).stream()
                .map(day -> day.getDayOfWeek().intValue()).collect(Collectors.toSet());
        return normalizer.workingWindows(session, weekdays);
    }

    private List<IntervalRequest> personal(long session, long user) {
        return busyIntervals.findByPlanningSessionIdAndAccountIdOrderByStartsAt(session, user).stream()
                .map(PlanningAvailabilityService::interval).toList();
    }

    private List<TimetableEntryEntity> known(PlanningSessionEntity session, List<Long> users) {
        return users.isEmpty() ? List.of() : timetable.findByAccountIdInAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                users, session.getEndsAt(), session.getStartsAt());
    }

    private static IntervalRequest interval(BusyIntervalEntity entity) {
        return new IntervalRequest(entity.getStartsAt(), entity.getEndsAt(), entity.getImportance());
    }

    private static List<IntervalRequest> withKnown(List<IntervalRequest> personal, List<TimetableEntryEntity> known) {
        List<IntervalRequest> result = new ArrayList<>(personal);
        known.forEach(t -> result.add(new IntervalRequest(t.getStartsAt(), t.getEndsAt(), BusyImportance.CRITICAL)));
        return result;
    }

    private static PlanningException invalid(String code, String message) {
        return new PlanningException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static PlanningException conflict(String code, String message) {
        return new PlanningException(HttpStatus.CONFLICT, code, message);
    }

    private static PlanningException closed() {
        return conflict("COLLECTION_CLOSED", "Сбор закрыт; редактирование занятости недоступно");
    }
}
