package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.solving.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;
import ru.vk.itmo.scheduler.slotwise.solving.SolverJobStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Execution lifecycle is separate from solveStatus: COMPLETED + NO_SOLUTION is valid, "
        + "with no result variant and no technical error. All polling fields are present, including nulls.")
public record SolverJobResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputSnapshotId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
        @NotNull SolverAlgorithm algorithm,
        @NotNull SolverParameters parameters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long baseVariantId,
        @NotNull SolverJobStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Fraction in [0, 1], or null when the solver cannot estimate progress",
                minimum = "0", maximum = "1") Double progress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "1",
                description = "One-based position while queued; null outside the queue") Integer queuePosition,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Sanitized technical error for FAILED; null for NO_SOLUTION") Failure error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Result variant ID; null before completion or when no solution exists") Long resultId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "OPTIMAL, FEASIBLE or NO_SOLUTION on normal completion; otherwise null") SolveStatus solveStatus,
        @NotNull Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {

    @Schema(name = "SolverJobFailure", description = "Technical failure without stack traces or private participant data")
    public record Failure(@NotNull String code, @NotNull String message) {
    }
}
