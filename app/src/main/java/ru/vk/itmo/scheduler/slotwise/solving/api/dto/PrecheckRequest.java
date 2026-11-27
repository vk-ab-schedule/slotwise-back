package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PrecheckRequest(
        @NotNull @PositiveOrZero
        @Schema(description = "Session input version to check; a mismatch returns 409") Long expectedInputVersion) {
}
