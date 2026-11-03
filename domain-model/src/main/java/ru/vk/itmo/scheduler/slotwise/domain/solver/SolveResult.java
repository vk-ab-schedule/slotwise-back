package ru.vk.itmo.scheduler.slotwise.domain.solver;

import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Score;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Outcome of a run.
 *
 * @param assignments  placed events, sorted by event id; events without an assignment are unassigned
 * @param score        score as the solver computed it; empty for {@link SolveStatus#NO_SOLUTION}
 * @param penaltyBound proven lower bound of the penalty for this number of unassigned events, if the solver has one (CP-SAT)
 */
public record SolveResult(
        SolveStatus status,
        List<Assignment> assignments,
        Optional<Score> score,
        OptionalLong penaltyBound,
        Duration elapsed) {

    public SolveResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(penaltyBound, "penaltyBound");
        Objects.requireNonNull(elapsed, "elapsed");
        assignments = assignments.stream().sorted(Comparator.comparingLong(Assignment::eventId)).toList();
        for (int i = 1; i < assignments.size(); i++) {
            if (assignments.get(i).eventId() == assignments.get(i - 1).eventId()) {
                throw new IllegalArgumentException("Event " + assignments.get(i).eventId() + " is assigned twice");
            }
        }
        if ((status == SolveStatus.NO_SOLUTION) != score.isEmpty()) {
            throw new IllegalArgumentException("A score is required unless there is no solution: " + status);
        }
        if (status == SolveStatus.NO_SOLUTION && !assignments.isEmpty()) {
            throw new IllegalArgumentException("No solution cannot have assignments");
        }
    }

    public static SolveResult noSolution(Duration elapsed) {
        return new SolveResult(SolveStatus.NO_SOLUTION, List.of(), Optional.empty(), OptionalLong.empty(), elapsed);
    }
}
