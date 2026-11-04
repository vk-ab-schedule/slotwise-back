package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;

import java.util.List;

@Schema(description = "Validated assignment and whole-variant metrics. Preview leaves versions unchanged; "
        + "a saved edit advances both variantVersion and assignment.version atomically.")
public record AssignmentUpdateResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long variantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long variantVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dryRun,
        @NotNull AssignmentResponse assignment,
        @NotNull ScheduleMetrics metrics,
        @NotNull List<Long> affectedAccountIds,
        @NotNull List<ApiIssue> issues) {
}
