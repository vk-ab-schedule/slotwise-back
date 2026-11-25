package ru.vk.itmo.scheduler.slotwise.solving;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.PrecheckIssue;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.Prechecks;
import ru.vk.itmo.scheduler.slotwise.planning.CollectionStatus;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionEntity;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverDescriptorResponse;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Prechecks and manual calculations of a session (UX-11). Only the organizer who owns the session may use them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SolvingService {
    private static final List<SolverDescriptorResponse> SOLVERS = List.of(
            new SolverDescriptorResponse(SolverAlgorithm.GREEDY, "Жадный алгоритм",
                    "Ставит события по очереди, от самых трудных: мгновенно, но оставляет больше окон", true, false),
            new SolverDescriptorResponse(SolverAlgorithm.CP_SAT, "CP-SAT",
                    "Ищет лучшее расписание в пределах лимита времени и оценивает, насколько можно улучшить штраф",
                    true, true),
            new SolverDescriptorResponse(SolverAlgorithm.TIMEFOLD, "Timefold",
                    "Локальный поиск для больших сессий; появится в спринте 3", false, false));

    private final PlanningSessionRepository sessions;
    private final ProblemLoader loader;
    private final Clock clock;

    public List<SolverDescriptorResponse> solvers() {
        return SOLVERS;
    }

    public PrecheckResponse precheck(long sessionId, long user, PrecheckRequest request) {
        var session = ownSession(sessionId, user, request.expectedInputVersion());
        List<ApiIssue> issues = new ArrayList<>(Prechecks.run(loader.load(session)).stream()
                .map(SolvingService::issue).toList());
        var incomplete = loader.incompleteEvents(sessionId);
        if (!incomplete.isEmpty()) {
            issues.add(new ApiIssue("INCOMPLETE_EVENTS", "Неполные события не участвуют в расчёте: "
                    + "у них не хватает обязательных участников", IssueSeverity.WARNING, incomplete, List.of(), List.of()));
        }
        var nonRespondents = loader.nonRespondents(sessionId);
        if (!nonRespondents.isEmpty()) {
            issues.add(new ApiIssue("NOT_RESPONDED", "Участники не отправили занятость: при расчёте они свободны "
                    + "в рабочие часы, кроме пар", IssueSeverity.WARNING, List.of(), nonRespondents, List.of()));
        }
        return new PrecheckResponse(sessionId, session.getInputVersion(),
                collectionOpen(session) || !nonRespondents.isEmpty(), nonRespondents, issues);
    }

    private PlanningSessionEntity ownSession(long id, long user, long expectedInputVersion) {
        var session = sessions.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена"));
        if (session.getOrganizerId() != user) {
            throw new AccessDeniedException("Не владелец сессии");
        }
        if (session.getInputVersion() != expectedInputVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_INPUT_VERSION",
                    "Данные сессии изменились; обновите их и повторите запрос");
        }
        return session;
    }

    private boolean collectionOpen(PlanningSessionEntity session) {
        return session.getCollectionStatus() != CollectionStatus.CLOSED
                && clock.instant().isBefore(session.getCollectionEndsAt());
    }

    private static ApiIssue issue(PrecheckIssue issue) {
        return new ApiIssue(issue.code().name(), issue.message(), IssueSeverity.valueOf(issue.severity().name()),
                issue.eventIds(), issue.personIds(), issue.roomIds());
    }
}
