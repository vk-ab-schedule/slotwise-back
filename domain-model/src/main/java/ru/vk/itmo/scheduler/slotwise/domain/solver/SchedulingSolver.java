package ru.vk.itmo.scheduler.slotwise.domain.solver;

import ru.vk.itmo.scheduler.slotwise.domain.Problem;

/**
 * A scheduling algorithm: greedy, CP-SAT, Timefold (MODEL.md, «Солверы»).
 *
 * <p>Contract for every implementation:
 * <ul>
 *     <li>pinned assignments of the problem are returned unchanged (H9);</li>
 *     <li>the same problem and seed give the same result: no {@code Math.random()} or {@code now()} in the logic
 *     (CP-SAT is deterministic with one worker);</li>
 *     <li>the solver stops near the time limit and on thread interruption, returning the best schedule found so far;</li>
 *     <li>it may not throw for a hard problem: events it cannot place are left unassigned. An exception means a bug,
 *     and the application records the run as failed.</li>
 * </ul>
 * The result is not trusted: the application checks it with the validator before saving.
 */
public interface SchedulingSolver {

    /**
     * Stable name of the algorithm: {@code greedy}, {@code cpsat}, {@code timefold}.
     */
    String id();

    /**
     * Runs on a worker thread; {@code progress} is called from the same thread.
     */
    SolveResult solve(Problem problem, SolveParams params, ProgressListener progress);
}
