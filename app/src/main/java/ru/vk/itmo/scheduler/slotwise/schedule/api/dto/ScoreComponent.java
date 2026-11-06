package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "One of five soft-constraint contributions to P; weightedPenalty = rawPenalty * weight. "
        + "Raw units are minutes for S1-S2, personal busy overlap minutes multiplied by importance coefficients "
        + "for S3 (MEDIUM=3, LOW=1, CRITICAL student in GROUP event=10), and counts for S4-S5. "
        + "Hour-based summary metrics are reported separately; unassigned events U are compared independently before P.")
public record ScoreComponent(
        @NotNull ScheduleConstraint constraint,
        @NotNull @PositiveOrZero BigDecimal rawPenalty,
        @NotNull @PositiveOrZero Integer weight,
        @NotNull @PositiveOrZero BigDecimal weightedPenalty) {
}
