package ru.vk.itmo.scheduler.slotwise.solver.greedy;

import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SchedulingSolver;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/** Deterministic construction heuristic for standalone runs and CP-SAT starting solutions. */
public final class GreedySolver implements SchedulingSolver {
    @Override
    public String id() {
        return "greedy";
    }

    @Override
    public SolveResult solve(Problem problem, SolveParams params, ProgressListener progress) {
        long started = System.nanoTime();
        var pinned = ScheduleValidator.validate(problem, problem.pinned());
        if (!pinned.feasible()) {
            return SolveResult.noSolution(elapsed(started));
        }

        var search = new PlacementSearch(problem, params.seed(),
                () -> Thread.currentThread().isInterrupted() || elapsed(started).compareTo(params.timeLimit()) >= 0);
        var assignments = search.run();
        var validation = ScheduleValidator.validate(problem, assignments);
        if (!validation.feasible()) {
            throw new IllegalStateException("Greedy produced invalid assignments: " + validation.violations());
        }

        var duration = elapsed(started);
        progress.onProgress(new ProgressListener.Progress(duration, validation.score(), OptionalLong.empty()));
        return new SolveResult(SolveStatus.FEASIBLE, List.copyOf(assignments), Optional.of(validation.score()),
                OptionalLong.empty(), duration);
    }

    private static Duration elapsed(long start) {
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
