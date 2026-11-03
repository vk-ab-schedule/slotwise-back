package ru.vk.itmo.scheduler.slotwise.domain.solver;

import java.time.Duration;
import java.util.Objects;

/**
 * Parameters of one run. Weights are part of the problem, because they change the meaning of "best".
 */
public record SolveParams(Duration timeLimit, long seed) {

    public SolveParams {
        Objects.requireNonNull(timeLimit, "timeLimit");
        if (timeLimit.isNegative() || timeLimit.isZero()) {
            throw new IllegalArgumentException("Time limit must be positive: " + timeLimit);
        }
    }
}
