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
import java.util.*;
import java.util.stream.Collectors;

import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.*;

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
    private final PlanningProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    public BusyResponse busy(long id, long user) {
        var session = session(id, false);
        return busyResponse(session, participant(id, user));
    }

    @Transactional
    public BusyResponse saveBusy(long id, long user, SaveBusyRequest request) {
        var session = session(id, true);
        var participant = participant(id, user);
        requireOpen(session);
        requireVersion(session, request.inputVersion());
        for (Interval interval : request.intervals()) {
            if (!interval.startsAt().isBefore(interval.endsAt())) {
                throw invalid("INVALID_INTERVAL", "Начало интервала должно быть раньше конца");
            }
        }
        List<Interval> normalized = normalizer.normalize(request.intervals(), windows(session));
        List<Interval> previous = personal(id, user);
        if (!normalized.equals(previous)) {
            busyIntervals.deletePersonal(id, user);
            for (Interval interval : normalized) {
                var entity = new BusyIntervalEntity();
                entity.setPlanningSessionId(id);
                entity.setAccountId(user);
                entity.setStartsAt(interval.startsAt());
                entity.setEndsAt(interval.endsAt());
                entity.setImportance(interval.importance());
                busyIntervals.save(entity);
            }
            // Editing a submitted calendar keeps its submitted status until collection closes.
            bump(session);
        }
        return busyResponse(session, participant);
    }

    @Transactional
    public BusyResponse submit(long id, long user) {
        var session = session(id, true);
        var participant = participant(id, user);
        requireOpen(session);
        if (participant.getResponseStatus() != ResponseStatus.SUBMITTED) {
            participant.setResponseStatus(ResponseStatus.SUBMITTED);
            participant.setSubmittedAt(clock.instant());
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
        requireVersion(session, request.inputVersion());
        if (status(session) == CollectionStatus.CLOSED) throw closed();
        if (!request.endsAt().isAfter(clock.instant())
                || !request.endsAt().isAfter(session.getCollectionStartsAt())
                || !request.endsAt().isBefore(session.getStartsAt())) {
            throw invalid("INVALID_COLLECTION_DEADLINE", "Дедлайн должен быть после начала сбора, в будущем и до начала сессии");
        }
        if (!request.endsAt().equals(session.getCollectionEndsAt())) {
            session.setCollectionEndsAt(request.endsAt());
            bump(session);
        }
        return collectionResponse(session);
    }

    @Transactional
    public CollectionResponse close(long id, long user) {
        var session = session(id, true);
        requireOwner(session, user);
        if (status(session) == CollectionStatus.PENDING) {
            throw conflict("COLLECTION_NOT_OPEN", "Сбор ещё не начался");
        }
        if (session.getCollectionStatus() != CollectionStatus.CLOSED) {
            session.setCollectionStatus(CollectionStatus.CLOSED);
            bump(session);
        }
        return collectionResponse(session);
    }

    public RulesResponse rules(long id, long user) {
        var session = session(id, false);
        requireOwner(session, user);
        return rulesResponse(session, rules.findById(id).orElse(null));
    }

    @Transactional
    public RulesResponse saveRules(long id, long user, SaveRulesRequest request) {
        var session = session(id, true);
        requireOwner(session, user);
        requireVersion(session, request.inputVersion());
        if (!request.weights().keySet().equals(WEIGHT_KEYS)) {
            throw invalid("INVALID_WEIGHTS", "Нужно передать все веса S1, S2, S3, S4, S5");
        }
        if (request.roomChangeBufferMinutes() % properties.getSlotMinutes() != 0) {
            throw invalid("INVALID_BUFFER", "Буфер должен быть кратен длине слота");
        }
        var entity = rules.findById(id).orElse(null);
        var before = rulesResponse(session, entity);
        var desired = new RulesResponse(session.getInputVersion(), Map.copyOf(request.weights()),
                request.roomChangeBufferMinutes(), request.defaultAlgorithm(), request.timeLimitSeconds(), request.seed());
        if (entity == null || !before.equals(desired)) {
            if (entity == null) {
                entity = new SessionRuleEntity();
                entity.setPlanningSessionId(id);
            }
            entity.setWeights(json.writeValueAsString(request.weights()));
            entity.setRoomChangeBufferMinutes(request.roomChangeBufferMinutes());
            entity.setDefaultAlgorithm(request.defaultAlgorithm());
            entity.setTimeLimitSeconds(request.timeLimitSeconds());
            entity.setSeed(request.seed());
            entity = rules.save(entity);
            bump(session);
        }
        return rulesResponse(session, entity);
    }

    private BusyResponse busyResponse(PlanningSessionEntity session, SessionParticipantEntity participant) {
        List<Interval> personal = personal(session.getId(), participant.getAccountId());
        List<TimetableEntryEntity> known = known(session, List.of(participant.getAccountId()));
        List<Warning> warnings = new ArrayList<>();
        if (known.isEmpty()) warnings.add(Warning.NO_TIMETABLE);
        if (normalizer.noFreeTime(withKnown(personal, known), windows(session))) warnings.add(Warning.NO_FREE_TIME);
        AccountEntity account = accounts.findById(participant.getAccountId()).orElseThrow();
        if (account.getRole() == AccountRole.STUDENT
                && personal.stream().anyMatch(i -> i.importance() == BusyImportance.CRITICAL)) {
            warnings.add(Warning.STUDENT_CRITICAL_IS_SOFT_FOR_GROUP_EVENTS);
        }
        return new BusyResponse(session.getInputVersion(), status(session), participant.getResponseStatus(),
                participant.getSubmittedAt(), personal, known.stream()
                .map(t -> new KnownInterval(t.getId(), t.getStartsAt(), t.getEndsAt(), t.getDescription())).toList(),
                List.copyOf(warnings));
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
            result.add(new ParticipantResponse(account.getId(), account.getFirstName(), account.getLastName(),
                    account.getRole(), member.getResponseStatus(), member.getSubmittedAt(), noFreeTime));
        }
        return new CollectionResponse(session.getInputVersion(), status(session),
                session.getCollectionStartsAt(), session.getCollectionEndsAt(), List.copyOf(result));
    }

    private RulesResponse rulesResponse(PlanningSessionEntity session, SessionRuleEntity entity) {
        Map<String, Integer> weights = new TreeMap<>(properties.getDefaultWeights());
        if (!weights.keySet().equals(WEIGHT_KEYS)) {
            throw new IllegalStateException("slotwise.planning.default-weights must contain S1 through S5");
        }
        if (entity == null) return new RulesResponse(session.getInputVersion(), Map.copyOf(weights), 15, SolverAlgorithm.GREEDY, 60, 0);
        var stored = json.readTree(entity.getWeights());
        for (String key : WEIGHT_KEYS) {
            if (stored.has(key)) weights.put(key, stored.get(key).intValue());
        }
        return new RulesResponse(session.getInputVersion(), Map.copyOf(weights), entity.getRoomChangeBufferMinutes(),
                entity.getDefaultAlgorithm(), entity.getTimeLimitSeconds(), entity.getSeed());
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
        if (session.getOrganizerId() != user) throw new AccessDeniedException("Не владелец сессии");
    }

    private CollectionStatus status(PlanningSessionEntity session) {
        Instant now = clock.instant();
        if (session.getCollectionStatus() == CollectionStatus.CLOSED || !now.isBefore(session.getCollectionEndsAt()))
            return CollectionStatus.CLOSED;
        if (now.isBefore(session.getCollectionStartsAt())) return CollectionStatus.PENDING;
        return CollectionStatus.OPEN;
    }

    private void requireOpen(PlanningSessionEntity session) {
        var status = status(session);
        if (status == CollectionStatus.CLOSED) throw closed();
        if (status != CollectionStatus.OPEN) throw conflict("COLLECTION_NOT_OPEN", "Сбор ещё не начался");
    }

    private static void requireVersion(PlanningSessionEntity session, long expected) {
        if (session.getInputVersion() != expected)
            throw conflict("STALE_INPUT_VERSION", "Данные сессии изменились; обновите их и повторите запрос");
    }

    private static void bump(PlanningSessionEntity session) {
        session.setInputVersion(Math.incrementExact(session.getInputVersion()));
    }

    private List<BusyIntervalNormalizer.Window> windows(PlanningSessionEntity session) {
        Set<Integer> weekdays = workingDays.findByPlanningSessionId(session.getId()).stream()
                .map(day -> day.getDayOfWeek().intValue()).collect(Collectors.toSet());
        return normalizer.workingWindows(session, weekdays);
    }

    private List<Interval> personal(long session, long user) {
        return busyIntervals.findByPlanningSessionIdAndAccountIdOrderByStartsAt(session, user).stream()
                .map(PlanningAvailabilityService::interval).toList();
    }

    private List<TimetableEntryEntity> known(PlanningSessionEntity session, List<Long> users) {
        return users.isEmpty() ? List.of() : timetable.findByAccountIdInAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
                users, session.getEndsAt(), session.getStartsAt());
    }

    private static Interval interval(BusyIntervalEntity entity) {
        return new Interval(entity.getStartsAt(), entity.getEndsAt(), entity.getImportance());
    }

    private static List<Interval> withKnown(List<Interval> personal, List<TimetableEntryEntity> known) {
        List<Interval> result = new ArrayList<>(personal);
        known.forEach(t -> result.add(new Interval(t.getStartsAt(), t.getEndsAt(), BusyImportance.CRITICAL)));
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
