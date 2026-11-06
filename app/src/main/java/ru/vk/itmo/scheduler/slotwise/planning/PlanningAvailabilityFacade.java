package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.account.AccountRepository;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.*;

import java.util.List;
import java.util.Map;

/** Maps the published API contract to collection operations within the same transaction. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanningAvailabilityFacade {
    private final PlanningAvailabilityService service;
    private final PlanningSessionRepository sessions;
    private final BusyIntervalRepository intervals;
    private final AccountRepository accounts;

    public BusyCalendar.Response busy(long id, long user) {
        return calendar(id, user, service.busy(id, user));
    }

    @Transactional
    public BusyCalendar.Response saveBusy(long id, long user, BusyCalendar.ReplaceRequest request) {
        var session = lock(id);
        // Check membership before returning information about a revision.
        service.busy(id, user);
        requireRevision(session, request.expectedRevision());
        var input = request.intervals().stream()
                .map(i -> new PlanningApi.Interval(i.startsAt(), i.endsAt(), i.importance())).toList();
        return calendar(id, user, service.saveBusy(id, user,
                new PlanningApi.SaveBusyRequest(session.getInputVersion(), input)));
    }

    @Transactional
    public BusyCalendar.Response submit(long id, long user, BusyCalendar.SubmitRequest request) {
        var session = lock(id);
        service.busy(id, user);
        requireRevision(session, request.expectedRevision());
        return calendar(id, user, service.submit(id, user));
    }

    public CollectionResponse collection(long id, long user) {
        return collectionResponse(id, service.collection(id, user));
    }

    @Transactional
    public CollectionResponse close(long id, long user, SessionVersionRequest request) {
        requireOwnerVersion(lock(id), user, request.expectedVersion());
        var result = service.close(id, user);
        sessions.flush();
        return collectionResponse(id, result);
    }

    @Transactional
    public CollectionResponse changeCollection(long id, long user, ChangeCollectionRequest request) {
        var session = lock(id);
        requireOwnerVersion(session, user, request.expectedVersion());
        var result = service.changeCollection(id, user,
                new PlanningApi.ChangeCollectionRequest(session.getInputVersion(), request.endsAt()));
        sessions.flush();
        return collectionResponse(id, result);
    }

    public SessionRules.Response rules(long id, long user) {
        return rulesResponse(id, service.rules(id, user));
    }

    @Transactional
    public SessionRules.Response saveRules(long id, long user, SessionRules.UpdateRequest request) {
        var session = lock(id);
        requireOwnerVersion(session, user, request.expectedVersion());
        var w = request.weights();
        var result = service.saveRules(id, user, new PlanningApi.SaveRulesRequest(session.getInputVersion(),
                Map.of("S1", w.teacherGaps(), "S2", w.commissionLoadSpread(), "S3", w.undesirableTime(),
                        "S4", w.teacherRoomChanges(), "S5", w.movedEvents()),
                request.roomChangeBufferMinutes(), request.defaultAlgorithm(), request.timeLimitSeconds(), request.seed()));
        sessions.flush();
        return rulesResponse(id, result);
    }

    private BusyCalendar.Response calendar(long id, long user, PlanningApi.BusyResponse result) {
        var session = sessions.findById(id).orElseThrow();
        var personal = intervals.findByPlanningSessionIdAndAccountIdOrderByStartsAt(id, user).stream()
                .map(i -> new BusyCalendar.Interval(i.getId(), i.getStartsAt(), i.getEndsAt(), i.getImportance())).toList();
        return new BusyCalendar.Response(id, revision(result.inputVersion()), session.getTimeZone(),
                result.responseStatus(), result.submittedAt(), result.collectionStatus() == CollectionStatus.OPEN,
                !result.knownIntervals().isEmpty(), personal,
                result.knownIntervals().stream().map(i -> new BusyCalendar.KnownBusy(i.startsAt(), i.endsAt(), i.description())).toList(),
                result.warnings().stream().map(w -> new ApiIssue(w.name(), warningMessage(w), IssueSeverity.WARNING,
                        List.of(), List.of(user), List.of())).toList());
    }

    private CollectionResponse collectionResponse(long id, PlanningApi.CollectionResponse result) {
        var people = accounts.findAllById(result.participants().stream().map(PlanningApi.ParticipantResponse::accountId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(a -> a.getId(), a -> a));
        return new CollectionResponse(result.status(), result.startsAt(), result.endsAt(),
                sessions.findById(id).orElseThrow().getVersion(), result.inputVersion(),
                result.participants().stream().map(p -> {
                    var a = people.get(p.accountId());
                    return new CollectionResponse.ParticipantResponse(
                            new PersonSummary(a.getId(), a.getLastName(), a.getFirstName(), a.getPatronymic(), a.getRole()),
                            p.responseStatus(), p.submittedAt(), p.noFreeTime());
                }).toList());
    }

    private SessionRules.Response rulesResponse(long id, PlanningApi.RulesResponse result) {
        var w = result.weights();
        return new SessionRules.Response(sessions.findById(id).orElseThrow().getVersion(), result.inputVersion(), 5,
                new SessionRules.Weights(w.get("S1"), w.get("S2"), w.get("S3"), w.get("S4"), w.get("S5")),
                result.roomChangeBufferMinutes(), result.defaultAlgorithm(), result.timeLimitSeconds(), result.seed());
    }

    private PlanningSessionEntity lock(long id) {
        return sessions.findForUpdate(id).orElseThrow(() ->
                new PlanningException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена"));
    }

    private static String revision(long version) { return "v" + version; }

    private static void requireRevision(PlanningSessionEntity session, String expected) {
        if (!revision(session.getInputVersion()).equals(expected))
            throw new PlanningException(HttpStatus.CONFLICT, "STALE_REVISION", "Занятость или параметры сессии изменились; обновите данные");
    }

    private static void requireOwnerVersion(PlanningSessionEntity session, long user, long expected) {
        if (session.getOrganizerId() != user) throw new AccessDeniedException("Не владелец сессии");
        if (session.getVersion() != expected)
            throw new PlanningException(HttpStatus.CONFLICT, "STALE_SESSION_VERSION", "Сессия изменилась; обновите данные");
    }

    private static String warningMessage(PlanningApi.Warning warning) {
        return switch (warning) {
            case NO_TIMETABLE -> "Учебное расписание отсутствует";
            case NO_FREE_TIME -> "В рабочих часах сессии нет свободного времени";
            case STUDENT_CRITICAL_IS_SOFT_FOR_GROUP_EVENTS -> "Критическая занятость студента учитывается как мягкое ограничение групповых событий";
        };
    }
}
