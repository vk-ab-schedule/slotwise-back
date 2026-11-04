package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UniversityImportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dryRun,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowPartial,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean applied,
        @NotNull UniversityImportCounts summary,
        @NotNull UniversityImportSections sections,
        @NotNull List<UniversityImportDiagnostic> diagnostics) {
}
