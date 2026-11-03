package ru.vk.itmo.scheduler.slotwise.domain.solver;

import lombok.NonNull;

import java.time.Duration;

/**
 * Parameters of one run. Weights are part of the problem, because they change the meaning of "best".
 */
public record SolveParams(@NonNull Duration timeLimit, long seed) {

    public SolveParams {
        if (timeLimit.isNegative() || timeLimit.isZero()) {
            throw new IllegalArgumentException("Time limit must be positive: " + timeLimit);
        }
    }
}
