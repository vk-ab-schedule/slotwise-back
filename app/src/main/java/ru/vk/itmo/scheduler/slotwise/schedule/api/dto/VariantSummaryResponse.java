package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.schedule.ScheduleVariantStatus;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record VariantSummaryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long solverJobId,
        @NotNull ScheduleVariantStatus status,
        @NotNull SolverAlgorithm algorithm,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long baseVariantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long currentInputVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Snapshot inputVersion differs from the current session input version") boolean staleData,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Snapshot was taken while collection was open or responses were missing") boolean incompleteData,
        @NotNull ScheduleMetrics metrics,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant publishedAt) {
}
