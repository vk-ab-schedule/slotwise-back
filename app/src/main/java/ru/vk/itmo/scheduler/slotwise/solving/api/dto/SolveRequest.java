package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

public record SolveRequest(
        @NotNull @PositiveOrZero Long expectedInputVersion,
        @NotNull SolverAlgorithm algorithm,
        @NotNull @Min(1) @Max(300) Integer timeLimitSeconds,
        @NotNull @Schema(description = "Explicit signed seed; negative values are valid") Long seed,
        @Min(1) @Schema(description = "Optional baseline variant from the same session; preserves its locked assignments")
        Long baseVariantId) {
}
