package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

public record UniversityImportCounts(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @PositiveOrZero long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @PositiveOrZero long created,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @PositiveOrZero long updated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @PositiveOrZero long unchanged,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @PositiveOrZero long rejected) {
}
