package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationStore.Revision;
import ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationStore.Settings;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreatePlanningSessionRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.PlanningSessionResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionSelection;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdatePlanningSessionRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SessionPreparationService {
    private static final Set<String> TIME_ZONES = Set.copyOf(ZoneId.getAvailableZoneIds());
    private static final int SLOT_MINUTES = TimeGrid.DEFAULT_SLOT_MINUTES;
    private static final Duration SLOT = Duration.ofMinutes(SLOT_MINUTES);
    private final SessionPreparationStore sessions;
    private final SessionMembership membership;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<PlanningSessionResponse> list(AccountPrincipal user, int page, int size) {
        return sessions.list(user.getAccountId(), canReadAsParticipant(user), page, size);
    }

    @Transactional(readOnly = true)
    public PlanningSessionResponse get(long id, AccountPrincipal user) {
        return sessions.visible(id, user.getAccountId(), canReadAsParticipant(user));
    }

    @Transactional
    public PlanningSessionResponse create(long owner, CreatePlanningSessionRequest request) {
        var settings = new Settings(request.name().strip(), request.startsAt(), request.endsAt(), request.timeZone(),
                request.workingDays(), request.workingDayStart(), request.workingDayEnd(), request.collectionStartsAt(),
                request.collectionEndsAt());
        validate(settings);
        return sessions.get(sessions.create(owner, settings));
    }

    @Transactional
    public PlanningSessionResponse update(long id, long owner, UpdatePlanningSessionRequest request) {
        requireVersion(sessions.owned(id, owner, true), request.expectedVersion());
        var settings = new Settings(request.name().strip(), request.startsAt(), request.endsAt(), request.timeZone(),
                request.workingDays(), request.workingDayStart(), request.workingDayEnd(), request.collectionStartsAt(),
                request.collectionEndsAt());
        var current = sessions.get(id);
        var collectionStart = settings.collectionStart().truncatedTo(ChronoUnit.MICROS);
        var collectionEnd = settings.collectionEnd().truncatedTo(ChronoUnit.MICROS);
        if (!collectionStart.equals(current.collectionStartsAt()) || !collectionEnd.equals(current.collectionEndsAt())) {
            CollectionWindow.requireEditable(current.collectionStatus(), current.collectionEndsAt(), clock.instant(),
                    collectionStart, collectionEnd, settings.startsAt().truncatedTo(ChronoUnit.MICROS));
        }
        validate(settings);
        try {
            sessions.update(id, settings);
        } catch (DuplicateKeyException duplicate) {
            throw SessionPreparationStore.duplicate();
        }
        return sessions.get(id);
    }

    @Transactional
    public SessionSelection.ParticipantsResponse participants(long id, long owner) {
        return membership.participants(id, sessions.owned(id, owner, false));
    }

    @Transactional
    public SessionSelection.ParticipantsResponse replaceParticipants(long id, long owner, SessionSelection.ParticipantsRequest request) {
        requireVersion(sessions.owned(id, owner, true), request.expectedVersion());
        membership.replaceParticipants(id, request.studyGroupIds(), request.accountIds());
        return membership.participants(id, sessions.bump(id));
    }

    @Transactional
    public SessionSelection.CommissionPoolResponse commission(long id, long owner) {
        return membership.commission(id, sessions.owned(id, owner, false));
    }

    @Transactional
    public SessionSelection.CommissionPoolResponse replaceCommission(long id, long owner, SessionSelection.CommissionPoolRequest request) {
        requireVersion(sessions.owned(id, owner, true), request.expectedVersion());
        membership.replaceCommission(id, request.candidates());
        return membership.commission(id, sessions.bump(id));
    }

    @Transactional
    public SessionSelection.RoomsResponse rooms(long id, long owner) {
        return membership.rooms(id, sessions.owned(id, owner, false));
    }

    @Transactional
    public SessionSelection.RoomsResponse replaceRooms(long id, long owner, SessionSelection.RoomsRequest request) {
        requireVersion(sessions.owned(id, owner, true), request.expectedVersion());
        membership.replaceRooms(id, request.roomIds());
        return membership.rooms(id, sessions.bump(id));
    }

    private static boolean canReadAsParticipant(AccountPrincipal user) {
        return user.getRole() == AccountRole.STUDENT || user.getRole() == AccountRole.TEACHER;
    }

    private static void validate(Settings s) {
        if (!TIME_ZONES.contains(s.timeZone())) {
            throw invalid("INVALID_TIME_ZONE", "Укажите часовой пояс IANA");
        }
        // PostgreSQL stores timestamps at microsecond precision.
        var start = s.startsAt().truncatedTo(ChronoUnit.MICROS);
        var end = s.endsAt().truncatedTo(ChronoUnit.MICROS);
        var collectionStart = s.collectionStart().truncatedTo(ChronoUnit.MICROS);
        var collectionEnd = s.collectionEnd().truncatedTo(ChronoUnit.MICROS);
        if (!start.isBefore(end) || !s.workingStart().isBefore(s.workingEnd())) {
            throw invalid("INVALID_SESSION_PERIOD", "Начало периода и рабочего дня должно быть раньше конца");
        }
        if (!collectionStart.isBefore(collectionEnd) || !collectionEnd.isBefore(start)) {
            throw invalid("INVALID_COLLECTION_PERIOD", "Сбор должен закончиться до начала сессии");
        }
        var zone = ZoneId.of(s.timeZone());
        var localStart = start.atZone(zone);
        var origin = localStart.truncatedTo(ChronoUnit.MINUTES)
                .minusMinutes(localStart.getMinute() % SLOT_MINUTES).toInstant();
        if (Duration.between(origin, end).compareTo(SLOT.multipliedBy(Integer.MAX_VALUE)) > 0) {
            throw invalid("INVALID_SESSION_PERIOD", "Период сессии превышает диапазон сетки расчёта");
        }
        var last = end.atZone(zone).toLocalDate();
        var first = localStart.toLocalDate();
        // Check the first occurrence of each working day plus one more week for partial days and DST.
        for (var day = first; !day.isAfter(last) && day.isBefore(first.plusDays(14)); day = day.plusDays(1)) {
            if (!s.days().contains(day.getDayOfWeek())) {
                continue;
            }
            var windowStart = day.atTime(s.workingStart()).atZone(zone).toInstant();
            var windowEnd = day.atTime(s.workingEnd()).atZone(zone).toInstant();
            var firstInstant = windowStart.isBefore(start) ? start : windowStart;
            var lastInstant = windowEnd.isAfter(end) ? end : windowEnd;
            var offset = Duration.between(origin, firstInstant);
            long slot = offset.dividedBy(SLOT);
            if (!offset.equals(SLOT.multipliedBy(slot))) {
                slot++;
            }
            if (!origin.plus(SLOT.multipliedBy(slot + 1)).isAfter(lastInstant)) {
                return;
            }
        }
        throw invalid("NO_WORKING_TIME", "В периоде сессии нет рабочего времени");
    }

    static void requireVersion(Revision revision, long expected) {
        if (revision.version() != expected) {
            throw conflict("STALE_SESSION_VERSION", "Данные сессии изменились; обновите их и повторите запрос");
        }
    }

    static PlanningException conflict(String code, String message) {
        return new PlanningException(HttpStatus.CONFLICT, code, message);
    }

    static PlanningException invalid(String code, String message) {
        return new PlanningException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
