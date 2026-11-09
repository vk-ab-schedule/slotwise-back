package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public record SolverParameters(
        @NotNull @Min(1) @Max(300) Integer timeLimitSeconds,
        @NotNull Long seed) {
}
