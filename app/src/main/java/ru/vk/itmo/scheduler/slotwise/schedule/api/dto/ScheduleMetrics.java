package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;
import ru.vk.itmo.scheduler.slotwise.domain.validation.SoftConstraint;
import ru.vk.itmo.scheduler.slotwise.domain.validation.Validation;
import ru.vk.itmo.scheduler.slotwise.solving.SolveStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Metrics recomputed by the common domain validator. Compare (U, P) lexicographically: "
        + "minimize unassignedEvents U first, then penalty P at the same U. A lower P cannot beat a lower U. "
        + "Null penalty/bestBound/gap mean unavailable, not zero. Bounds and gap concern only P at the reported U. "
        + "OPTIMAL requires proof of both minimum U and minimum P at that U. "
        + "NO_SOLUTION does not prove infeasibility; FEASIBLE does not prove optimality.")
public record ScheduleMetrics(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Validated soft penalty P, the sum of S1-S5 weighted penalties; null if no solution was obtained")
        Long penalty,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int hardViolations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0",
                description = "Unassigned-event count U; minimized before soft penalty P") int unassignedEvents,
        @NotNull @Schema(description = "Total teacher gaps in hours") BigDecimal gapsHours,
        @NotNull @Schema(description = "Maximum minus minimum commission workload in hours across the entire commission pool, "
                + "including teachers with zero commission workload; excludes examiner and supervisor workloads")
        BigDecimal commissionLoadRangeHours,
        @NotNull @Schema(description = "Standard deviation of commission workloads in hours across the entire commission pool, "
                + "including teachers with zero commission workload; excludes examiner and supervisor workloads")
        BigDecimal commissionLoadStandardDeviationHours,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0",
                description = "Number of events overlapping soft personal busy time: MEDIUM, LOW, "
                        + "or CRITICAL student busy time in GROUP events") int undesirableEvents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0",
                description = "Number of same-day teacher room transitions between in-person events (S4)")
        int teacherRoomChanges,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long runtimeMillis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Proven lower bound on soft penalty P at the reported U only; "
                        + "does not prove minimum U; null when unknown") BigDecimal bestBound,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Relative gap for P to bestBound at the reported U only (fraction, not percent); "
                        + "does not prove minimum U; null when unknown") BigDecimal gap,
        @NotNull @Schema(description = "OPTIMAL requires proof of both minimum U and minimum P at that U; "
                + "FEASIBLE does not prove optimality; NO_SOLUTION does not prove infeasibility") SolveStatus solveStatus,
        @NotNull @Schema(description = "Exactly one component for each S1-S5, including zero penalties; their weighted sum is P")
        List<ScoreComponent> scoreComponents) {

    /**
     * Metrics of a schedule checked by the validator; {@code penaltyBound} is the proven lower bound of P, if known.
     */
    public static ScheduleMetrics of(Validation validation, Weights weights, SolveStatus status, long runtimeMillis,
                                     OptionalLong penaltyBound) {
        var raw = validation.rawPenalties();
        int[] weight = {weights.teacherGaps(), weights.commissionLoadSpread(), weights.undesirableTime(),
                weights.teacherRoomChanges(), weights.movedEvents()};
        var components = Arrays.stream(SoftConstraint.values()).map(constraint -> {
            long value = raw.getOrDefault(constraint, 0L);
            int constraintWeight = weight[constraint.ordinal()];
            return new ScoreComponent(ScheduleConstraint.valueOf(constraint.name()), value, constraintWeight,
                    value * constraintWeight);
        }).toList();
        long penalty = validation.score().penalty();
        BigDecimal bound = penaltyBound.isPresent() ? BigDecimal.valueOf(penaltyBound.getAsLong()) : null;
        BigDecimal gap = bound == null ? null : penalty == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(penalty).subtract(bound).divide(BigDecimal.valueOf(penalty), 4, RoundingMode.HALF_UP);
        return new ScheduleMetrics(penalty, validation.violations().size(), validation.score().unassigned(),
                hours(raw.getOrDefault(SoftConstraint.S1, 0L)), hours(raw.getOrDefault(SoftConstraint.S2, 0L)),
                hours(validation.commissionLoadStdDevMinutes()), validation.undesirableEvents(),
                Math.toIntExact(raw.getOrDefault(SoftConstraint.S4, 0L)), runtimeMillis, bound, gap, status, components);
    }

    private static BigDecimal hours(double minutes) {
        return BigDecimal.valueOf(minutes / 60).setScale(2, RoundingMode.HALF_UP);
    }
}
