package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverSolutionCallback;
import com.google.ortools.sat.CpSolverStatus;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Score;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SchedulingSolver;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.domain.validation.Validation;
import ru.vk.itmo.scheduler.slotwise.solver.cpsat.exception.BudgetExhaustedException;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public final class CpsatSolver implements SchedulingSolver {

    static {
        Loader.loadNativeLibraries();
    }

    private final Optional<SchedulingSolver> greedy;

    public CpsatSolver() {
        greedy = Optional.empty();
    }

    public CpsatSolver(SchedulingSolver greedy) {
        this.greedy = Optional.of(greedy);
    }

    @Override
    public String id() {
        return "cpsat";
    }

    @Override
    public SolveResult solve(Problem problem, SolveParams params, ProgressListener progress) {
        Run run = new Run(problem, params, progress);
        try {
            optimize(run);
        } catch (BudgetExhaustedException ignored) {
            // An interrupted or timed-out run can still return its best feasible schedule.
        }
        return run.result();
    }

    private void optimize(Run run) {
        if (run.problem.events().isEmpty()) {
            run.optimal = true;
            run.bound = OptionalLong.of(0);
            return;
        }
        run.checkpoint();
        greedy.ifPresent(baseline -> warmStart(run, baseline));
        run.checkpoint();
        CpsatModel schedule = new CpsatModel(run.problem, run::checkpoint);

        if (run.best != null) {
            schedule.model.addGreaterOrEqual(schedule.assignedCount, run.best.assignments().size());
            schedule.hint(run.best.assignments());
        }
        schedule.model.maximize(schedule.assignedCount);
        Search coverage = search(run, schedule);
        if (coverage.status() == CpSolverStatus.INFEASIBLE || run.best == null) {
            return;
        }
        boolean coverageProven = coverage.status() == CpSolverStatus.OPTIMAL
                || run.best.score().unassigned() == 0;
        if (run.best.score().penalty() == 0) {
            run.bound = OptionalLong.of(0);
            run.optimal = coverageProven;
            return;
        }

        run.checkpoint();
        var penalty = new CpsatObjective(schedule, run::checkpoint).expression();
        schedule.model.addEquality(schedule.assignedCount, run.best.assignments().size());
        schedule.hint(run.best.assignments());
        schedule.model.minimize(penalty);
        Search quality = search(run, schedule);
        if (quality.status() == CpSolverStatus.INFEASIBLE) {
            throw new IllegalStateException("The penalty model excluded a feasible schedule");
        }
        if (quality.status() == CpSolverStatus.OPTIMAL) {
            long provenPenalty = quality.solver().value(penalty);
            if (provenPenalty != run.best.score().penalty()) {
                throw new IllegalStateException("The modeled penalty differs from the evaluated penalty");
            }
            run.bound = OptionalLong.of(provenPenalty);
            run.optimal = coverageProven;
        } else if (quality.status() == CpSolverStatus.FEASIBLE) {
            double nativeBound = quality.solver().bestObjectiveBound();
            if (Double.isFinite(nativeBound)) {
                long lowerBound = Math.max(0, (long) Math.floor(Math.nextDown(nativeBound)));
                run.bound = OptionalLong.of(Math.min(lowerBound, run.best.score().penalty()));
            }
        }
        if (run.bound.isPresent()) {
            run.publish();
        }
    }

    private void warmStart(Run run, SchedulingSolver baseline) {
        run.checkpoint();
        long allowance = Math.min(run.remainingNanos(), run.budgetNanos / 10);
        if (allowance <= 0) {
            return;
        }
        SolveResult hint = baseline.solve(run.problem, new SolveParams(Duration.ofNanos(allowance), run.params.seed()),
                ProgressListener.NONE);
        if (hint.score().isPresent()) {
            Validation validation = ScheduleValidator.validate(run.problem, hint.assignments());
            if (validation.feasible()) {
                run.consider(new Incumbent(hint.assignments(), validation.score()));
            }
        }
    }

    private Search search(Run run, CpsatModel schedule) {
        long budget = run.checkpoint();
        CpSolver solver = new CpSolver();
        solver.getParameters()
                .setNumWorkers(1)
                .setRandomSeed(Long.hashCode(run.params.seed()) & Integer.MAX_VALUE)
                // Probing the reified scheduling constraints can consume the whole search budget before the first incumbent.
                .setCpModelProbingLevel(0)
                .setMaxTimeInSeconds(budget / 1_000_000_000d);
        var latest = new AtomicReference<Incumbent>();
        var callbackFailure = new AtomicReference<RuntimeException>();

        var callback = new CpSolverSolutionCallback() {
            @Override
            public void onSolutionCallback() {
                try {
                    List<Assignment> assignments = schedule.assignments(this::value);
                    Incumbent candidate = run.evaluate(assignments);
                    latest.accumulateAndGet(candidate, (previous, next) ->
                            previous == null || next.score().isBetterThan(previous.score()) ? next : previous);
                } catch (RuntimeException failure) {
                    callbackFailure.set(failure);
                    stopSearch();
                }
            }
        };

        // Native search runs separately so progress and interruption are handled by the caller's thread.
        FutureTask<CpSolverStatus> search = new FutureTask<>(() -> solver.solve(schedule.model, callback));
        Thread.ofPlatform().daemon().name("slotwise-cpsat-search").start(search);
        CpSolverStatus status;
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted() || run.remainingNanos() <= 0) {
                    solver.stopSearch();
                }
                run.consider(latest.getAndSet(null));
                try {
                    status = search.get(20, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    // Poll for progress and cancellation while native search is active.
                } catch (InterruptedException interrupted) {
                    stopAndAwait(solver, search, true);
                }
            }
            if (callbackFailure.get() != null) {
                throw callbackFailure.get();
            }
            run.consider(latest.getAndSet(null));
            if (status == CpSolverStatus.MODEL_INVALID) {
                throw new IllegalStateException("Invalid CP-SAT model: " + solver.solutionInfo());
            }
            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                List<Assignment> assignments = schedule.assignments(solver::value);
                run.consider(run.evaluate(assignments));
            }
            return new Search(solver, status);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("CP-SAT search failed", failure.getCause());
        } finally {
            try {
                if (!search.isDone()) {
                    stopAndAwait(solver, search, false);
                }
            } finally {
                callback.delete();
            }
        }
    }

    private static void stopAndAwait(CpSolver solver, FutureTask<CpSolverStatus> search, boolean interrupted) {
        try {
            while (!search.isDone()) {
                solver.stopSearch();
                try {
                    search.get(20, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    // Retry if cancellation arrived before the native solver started.
                } catch (InterruptedException ignored) {
                    interrupted = true;
                } catch (ExecutionException ignored) {
                    // The caller reads the completed future and handles its failure.
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record Search(CpSolver solver, CpSolverStatus status) {
    }

    private record Incumbent(List<Assignment> assignments, Score score) {
    }

    private static final class Run {
        private final long start = System.nanoTime();
        private final Problem problem;
        private final SolveParams params;
        private final ProgressListener progress;
        private final long budgetNanos;
        private Incumbent best;
        private OptionalLong bound = OptionalLong.empty();
        private boolean optimal;

        private Run(Problem problem, SolveParams params, ProgressListener progress) {
            // M5 optimizes S1-S4. A baseline affects the validator's S5 score, so omit it for this run.
            this.problem = problem.baseline().isEmpty() ? problem : problem.toBuilder().clearBaseline().build();
            this.params = Objects.requireNonNull(params);
            this.progress = Objects.requireNonNull(progress);
            budgetNanos = params.timeLimit().compareTo(Duration.ofNanos(Long.MAX_VALUE)) >= 0
                    ? Long.MAX_VALUE : params.timeLimit().toNanos();
            if (problem.pinned().isEmpty()) {
                best = new Incumbent(List.of(), new Score(problem.events().size(), 0));
            }
        }

        private Duration elapsed() {
            return Duration.ofNanos(System.nanoTime() - start);
        }

        private long remainingNanos() {
            return budgetNanos - elapsed().toNanos();
        }

        private long checkpoint() {
            if (Thread.currentThread().isInterrupted()) {
                throw new BudgetExhaustedException("CP-SAT calculation was interrupted");
            }
            long remaining = remainingNanos();
            if (remaining <= 0) {
                throw new BudgetExhaustedException("CP-SAT calculation exceeded its time limit of " + params.timeLimit());
            }
            return remaining;
        }

        private Incumbent evaluate(List<Assignment> assignments) {
            Validation validation = ScheduleValidator.validate(problem, assignments);
            if (!validation.feasible()) {
                throw new IllegalStateException("CP-SAT returned an invalid schedule: " + validation.violations());
            }
            return new Incumbent(assignments, validation.score());
        }

        private void consider(Incumbent candidate) {
            if (candidate != null && (best == null || candidate.score().isBetterThan(best.score()))) {
                best = candidate;
                publish();
            }
        }

        private void publish() {
            progress.onProgress(new ProgressListener.Progress(elapsed(), best.score(), bound));
        }

        private SolveResult result() {
            if (best == null) {
                return SolveResult.noSolution(elapsed());
            }
            SolveStatus status = optimal ? SolveStatus.OPTIMAL : SolveStatus.FEASIBLE;
            return new SolveResult(status, best.assignments(), Optional.of(best.score()), bound, elapsed());
        }
    }
}
