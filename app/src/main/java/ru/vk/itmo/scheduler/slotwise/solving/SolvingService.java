package ru.vk.itmo.scheduler.slotwise.solving;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.PrecheckIssue;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.Prechecks;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.planning.CollectionStatus;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionEntity;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolveRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverDescriptorResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverJobResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverParameters;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
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
    private static final List<SolverJobStatus> ACTIVE = List.of(SolverJobStatus.QUEUED, SolverJobStatus.RUNNING);
    // The snapshot records the input as the solver got it and is not read back, so the fields are written as they are.
    private static final JsonMapper SNAPSHOT = JsonMapper.builder()
            .changeDefaultVisibility(checker -> checker.withFieldVisibility(Visibility.ANY)
                    .withGetterVisibility(Visibility.NONE).withIsGetterVisibility(Visibility.NONE))
            .addMixIn(Problem.class, ProblemIndexes.class)
            .addMixIn(TimeGrid.class, TimeGridIndexes.class)
            .build();

    private final PlanningSessionRepository sessions;
    private final InputSnapshotRepository snapshots;
    private final SolverJobRepository jobs;
    private final ProblemLoader loader;
    private final SolverJobRunner runner;
    private final JsonMapper json;
    private final Clock clock;

    public List<SolverDescriptorResponse> solvers() {
        return SOLVERS;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PrecheckResponse precheck(long sessionId, long user, PrecheckRequest request) {
        var session = ownSession(sessionId, user, request.expectedInputVersion(), false);
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

    /**
     * Queues a calculation. The same request for the same input returns the existing job, and so does any request
     * while the session already has a queued or running job (UX-11).
     */
    @Transactional
    public SolverJobResponse solve(long sessionId, long user, SolveRequest request) {
        var session = ownSession(sessionId, user, request.expectedInputVersion(), true);
        if (!SolverJobRunner.available(request.algorithm())) {
            throw new ApiException(HttpStatus.CONFLICT, "SOLVER_UNAVAILABLE", "Этот алгоритм пока недоступен");
        }
        var active = jobs.findFirstByPlanningSessionIdAndStatusIn(sessionId, ACTIVE);
        if (active.isPresent()) {
            return response(active.get());
        }
        Long baseVariantId = request.baseVariantId();
        Long baseVariantVersion = baseVariantId == null ? null : loader.lockVariantVersion(baseVariantId, sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND",
                        "Базовый вариант не найден в этой сессии"));
        var parameters = new JobParameters(request.timeLimitSeconds(), request.seed(), baseVariantId, baseVariantVersion);
        String parametersJson = json.writeValueAsString(parameters);
        var snapshot = snapshots.findByPlanningSessionIdAndInputVersion(sessionId, session.getInputVersion());
        var completed = snapshot.flatMap(value ->
                jobs.findCompletedRequest(value.getId(), request.algorithm().name(), parametersJson));
        if (completed.isPresent()) {
            return response(completed.get());
        }
        var input = loader.load(session);
        var problem = baseVariantId == null ? input : loader.withBaseline(input, baseVariantId, true);
        var nonRespondents = loader.nonRespondents(sessionId);
        boolean incompleteData = collectionOpen(session) || !nonRespondents.isEmpty();
        long snapshotId = snapshot.orElseGet(() -> snapshots.save(InputSnapshotEntity.create(
                sessionId, session.getInputVersion(),
                SNAPSHOT.writeValueAsString(new Snapshot(incompleteData, nonRespondents, input))))).getId();

        var job = new SolverJobEntity();
        job.setPlanningSessionId(sessionId);
        job.setInputSnapshotId(snapshotId);
        job.setIncompleteData(incompleteData);
        job.setAlgorithm(request.algorithm());
        job.setParameters(parametersJson);
        job.setStatus(SolverJobStatus.QUEUED);
        job.setRequestedBy(user);
        jobs.saveAndFlush(job);
        runner.submitAfterCommit(new SolverJobRunner.Run(job.getId(), sessionId, snapshotId,
                baseVariantId, request.algorithm(), problem,
                new SolveParams(Duration.ofSeconds(request.timeLimitSeconds()), request.seed())));
        return response(job);
    }

    public SolverJobResponse job(long id, long user) {
        return jobs.findById(id)
                .filter(job -> sessions.findById(job.getPlanningSessionId())
                        .map(session -> session.getOrganizerId() == user).orElse(false))
                .map(this::response)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Задача расчёта не найдена"));
    }

    static ApiIssue issue(PrecheckIssue issue) {
        return new ApiIssue(issue.code().name(), issue.message(), IssueSeverity.valueOf(issue.severity().name()),
                issue.eventIds(), issue.personIds(), issue.roomIds());
    }

    private SolverJobResponse response(SolverJobEntity job) {
        var parameters = json.readValue(job.getParameters(), JobParameters.class);
        Double progress = null;
        SolverJobResponse.Quality quality = null;
        Integer queuePosition = null;
        SolverJobResponse.Failure error = null;
        Long resultId = null;
        SolveStatus solveStatus = null;
        switch (job.getStatus()) {
            case QUEUED -> queuePosition = Math.toIntExact(
                    jobs.countByStatusAndIdLessThan(SolverJobStatus.QUEUED, job.getId()) + 1);
            case RUNNING -> {
                long elapsedMillis = Duration.between(job.getStartedAt(), clock.instant()).toMillis();
                long limitMillis = Duration.ofSeconds(parameters.timeLimitSeconds()).toMillis();
                progress = Math.clamp((double) elapsedMillis / limitMillis, 0.0, 1.0);
                var best = runner.progress(job.getId());
                if (best.isPresent()) {
                    var bound = best.get().penaltyBound();
                    quality = new SolverJobResponse.Quality(best.get().best().unassigned(), best.get().best().penalty(),
                            bound.isPresent() ? bound.getAsLong() : null);
                }
            }
            case COMPLETED -> {
                progress = 1.0;
                solveStatus = SolveStatus.NO_SOLUTION;
                if (job.getCompletedResult() != null) {
                    var result = json.readValue(job.getCompletedResult(), SolverJobEntity.CompletedResult.class);
                    solveStatus = result.solveStatus();
                    resultId = result.variantId();
                    quality = new SolverJobResponse.Quality(result.unassignedEvents(), result.penalty(), result.bestBound());
                }
            }
            case FAILED, CANCELLED -> error = new SolverJobResponse.Failure(job.getErrorMessage(),
                    "INTERRUPTED".equals(job.getErrorMessage())
                            ? "Расчёт прерван перезапуском сервера; запустите его снова"
                            : "Расчёт завершился технической ошибкой; запустите его снова");
        }
        long inputVersion = snapshots.findById(job.getInputSnapshotId()).orElseThrow().getInputVersion();
        return new SolverJobResponse(job.getId(), job.getPlanningSessionId(), job.getInputSnapshotId(), inputVersion,
                job.getAlgorithm(), new SolverParameters(parameters.timeLimitSeconds(), parameters.seed()),
                parameters.baseVariantId(), job.getStatus(), progress, quality, queuePosition, error, resultId,
                solveStatus, job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(), job.getVersion());
    }

    private PlanningSessionEntity ownSession(long id, long user, long expectedInputVersion, boolean lock) {
        var session = (lock ? sessions.findForUpdate(id) : sessions.findById(id)).orElseThrow(() ->
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

    /**
     * Content of {@code input_snapshot.problem}: the solver input and whether it was taken on incomplete data.
     */
    record Snapshot(boolean incompleteData, List<Long> nonRespondentIds, Problem problem) {
    }

    /**
     * Parameters stored with a job; equal parameters of the same input mean the same calculation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JobParameters(int timeLimitSeconds, long seed, Long baseVariantId, Long baseVariantVersion) {
    }

    @JsonIgnoreProperties({"peopleById", "roomsById", "eventsById", "pinnedByEvent"})
    private interface ProblemIndexes {
    }

    @JsonIgnoreProperties("dayOfSlot")
    private interface TimeGridIndexes {
    }
}
