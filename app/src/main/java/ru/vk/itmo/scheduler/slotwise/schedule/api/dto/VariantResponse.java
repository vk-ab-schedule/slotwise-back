package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.schedule.ScheduleVariantStatus;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverParameters;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Full variant with snapshot provenance, solver parameters, metrics, every assignment and "
        + "limited basic diagnostics. Published and superseded variants are immutable.")
public record VariantResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputSnapshotId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long currentInputVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Snapshot inputVersion differs from currentInputVersion") boolean staleData,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Snapshot was taken while collection was open or responses were missing") boolean incompleteData,
        @NotNull List<Long> nonRespondentIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long solverJobId,
        @NotNull SolverAlgorithm algorithm,
        @NotNull SolverParameters parameters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long baseVariantId,
        @NotNull ScheduleVariantStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whole-variant optimistic version, checked in addition to assignment versions") long version,
        @NotNull ScheduleMetrics metrics,
        @NotNull List<AssignmentResponse> assignments,
        @NotNull List<ApiIssue> issues,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant publishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long publishedBy) {
}
