package ru.vk.itmo.scheduler.slotwise.domain.validation;

import ru.vk.itmo.scheduler.slotwise.domain.Score;

import java.util.List;
import java.util.Map;

/**
 * Result of checking a schedule: hard violations, the score (U, P) and the metrics of the variant.
 * Gaps, commission load spread and room switches are the raw penalties of S1, S2 and S4.
 *
 * @param rawPenalties                raw penalty of every soft constraint, before weights
 * @param commissionLoadStdDevMinutes standard deviation of commission load over the whole pool
 * @param undesirableEvents           events that overlap soft busy time of at least one person
 */
public record Validation(
        List<Violation> violations,
        Score score,
        Map<SoftConstraint, Long> rawPenalties,
        double commissionLoadStdDevMinutes,
        int undesirableEvents) {

    public Validation {
        violations = List.copyOf(violations);
        rawPenalties = Map.copyOf(rawPenalties);
    }

    public boolean feasible() {
        return violations.isEmpty();
    }
}
