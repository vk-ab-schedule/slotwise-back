package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UniversityDemoDataResponse(
        @NotNull UniversityDemoDataset dataset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seed,
        @NotNull UniversityImportResponse result) {
}
