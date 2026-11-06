package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Whole-variant publication only. Both confirmation flags must be explicitly supplied, "
        + "even when false; they never override hard constraints.")
public record PublishVariantRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @Schema(description = "Must be true when the variant contains unassigned events") Boolean confirmUnassigned,
        @NotNull @Schema(description = "Must be true when the input snapshot is stale") Boolean confirmStaleData) {
}
