package ru.vk.itmo.scheduler.slotwise.domain.solver;

public enum SolveStatus {
    /** The best possible score is proven: no schedule has fewer unassigned events or, with as many, a lower penalty. */
    OPTIMAL,
    /** A schedule is found, but it is not proven to be the best. */
    FEASIBLE,
    /** No schedule was found within the time limit, for example because pinned assignments conflict. */
    NO_SOLUTION
}
