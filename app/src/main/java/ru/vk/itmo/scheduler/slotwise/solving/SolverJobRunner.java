package ru.vk.itmo.scheduler.slotwise.solving;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.Prechecks;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SchedulingSolver;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.schedule.VariantStore;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleMetrics;
import ru.vk.itmo.scheduler.slotwise.solver.cpsat.CpsatSolver;
import ru.vk.itmo.scheduler.slotwise.solver.greedy.GreedySolver;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Runs queued calculations, at most {@code slotwise.solving.max-concurrent-jobs} at a time (1 by default):
 * CP-SAT takes every core, and the stand is shared. The queue lives in memory, so jobs left unfinished
 * by a restart are marked failed at startup and can be launched again.
 */
@Slf4j
@Component
public class SolverJobRunner {
    private static final Map<SolverAlgorithm, Supplier<SchedulingSolver>> SOLVERS = Map.of(
            SolverAlgorithm.GREEDY, GreedySolver::new,
            SolverAlgorithm.CP_SAT, () -> new CpsatSolver(new GreedySolver()));

    private final SolverJobRepository jobs;
    private final VariantStore variants;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final JsonMapper json;
    private final ExecutorService executor;
    private final ArrayDeque<Run> queue = new ArrayDeque<>();
    private final Map<Long, ProgressListener.Progress> progress = new ConcurrentHashMap<>();

    SolverJobRunner(SolverJobRepository jobs, VariantStore variants, PlatformTransactionManager transactions, Clock clock,
                    JsonMapper json, @Value("${slotwise.solving.max-concurrent-jobs:1}") int maxConcurrentJobs) {
        this.jobs = jobs;
        this.variants = variants;
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
        this.json = json;
        this.executor = Executors.newFixedThreadPool(maxConcurrentJobs, Thread.ofPlatform().name("solver-", 1).factory());
    }

    /**
     * @param problem the input with the pins and the baseline of the base variant, if any
     */
    public record Run(long jobId, long sessionId, long snapshotId, Long baseVariantId, SolverAlgorithm algorithm,
                      Problem problem, SolveParams params) {
    }

    static boolean available(SolverAlgorithm algorithm) {
        return SOLVERS.containsKey(algorithm);
    }

    /**
     * Queues the run once the transaction that created the job commits.
     */
    public void submitAfterCommit(Run run) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronized (queue) {
                    queue.addLast(run);
                    executor.execute(SolverJobRunner.this::executeNext);
                }
            }
        });
    }

    Integer queuePosition(long jobId) {
        synchronized (queue) {
            int position = 1;
            for (Run run : queue) {
                if (run.jobId() == jobId) {
                    return position;
                }
                position++;
            }
            return null;
        }
    }

    /**
     * The best schedule a running job has found so far.
     */
    public Optional<ProgressListener.Progress> progress(long jobId) {
        return Optional.ofNullable(progress.get(jobId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedJobs() {
        transaction.executeWithoutResult(status -> jobs.findByStatusIn(List.of(SolverJobStatus.QUEUED, SolverJobStatus.RUNNING))
                .forEach(job -> fail(job, "INTERRUPTED")));
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    private void executeNext() {
        Run run;
        synchronized (queue) {
            run = queue.removeFirst();
            try {
                update(run.jobId(), job -> {
                    job.setStatus(SolverJobStatus.RUNNING);
                    job.setStartedAt(clock.instant());
                });
            } catch (RuntimeException e) {
                failRun(run, e);
                return;
            }
        }
        try {
            var result = solve(run);
            transaction.executeWithoutResult(status -> {
                var job = jobs.findById(run.jobId()).orElseThrow();
                if (result.score().isPresent()) {
                    // The result is not trusted: the common validator checks it and computes the metrics.
                    var validation = ScheduleValidator.validate(run.problem(), result.assignments());
                    if (!validation.feasible()) {
                        throw new IllegalStateException(run.algorithm() + " broke " + validation.violations().getFirst().reason());
                    }
                    var metrics = ScheduleMetrics.of(validation, run.problem().weights(),
                            SolveStatus.valueOf(result.status().name()), result.elapsed().toMillis(), result.penaltyBound());
                    long draftId = variants.saveDraft(new VariantStore.Draft(run.sessionId(), run.snapshotId(), run.jobId(),
                            run.baseVariantId(), run.problem(), result.assignments(),
                            run.problem().pinned().stream().map(Assignment::eventId).collect(Collectors.toSet()),
                            metrics, explanations(run.problem())));
                    job.setCompletedResult(json.writeValueAsString(new SolverJobEntity.CompletedResult(draftId,
                            metrics.solveStatus(), metrics.unassignedEvents(), metrics.penalty(),
                            result.penaltyBound().isPresent() ? result.penaltyBound().getAsLong() : null)));
                }
                job.setStatus(SolverJobStatus.COMPLETED);
                job.setFinishedAt(clock.instant());
            });
        } catch (RuntimeException | LinkageError e) {
            failRun(run, e);
        } finally {
            progress.remove(run.jobId());
        }
    }

    private void failRun(Run run, Throwable error) {
        log.error("Solver job {} failed", run.jobId(), error);
        update(run.jobId(), job -> fail(job, "SOLVER_FAILED"));
    }

    SolveResult solve(Run run) {
        return SOLVERS.get(run.algorithm()).get()
                .solve(run.problem(), run.params(), best -> progress.put(run.jobId(), best));
    }

    /**
     * Basic reasons why events may stay unassigned (UX-13): the prechecks that mention them.
     */
    private static Map<Long, List<ApiIssue>> explanations(Problem problem) {
        var result = new HashMap<Long, List<ApiIssue>>();
        for (var issue : Prechecks.run(problem)) {
            issue.eventIds().forEach(event -> result.computeIfAbsent(event, id -> new ArrayList<>())
                    .add(SolvingService.issue(issue)));
        }
        return result;
    }

    private void update(long jobId, Consumer<SolverJobEntity> change) {
        transaction.executeWithoutResult(status -> change.accept(jobs.findById(jobId).orElseThrow()));
    }

    private void fail(SolverJobEntity job, String code) {
        Instant now = clock.instant();
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        job.setStatus(SolverJobStatus.FAILED);
        job.setFinishedAt(now);
        job.setErrorMessage(code);
    }
}
