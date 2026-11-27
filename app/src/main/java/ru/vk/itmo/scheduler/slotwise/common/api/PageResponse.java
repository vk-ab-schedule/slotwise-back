package ru.vk.itmo.scheduler.slotwise.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PageResponse<T>(
        @NotNull List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", description = "Zero-based page") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int size) {
}
