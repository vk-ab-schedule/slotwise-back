package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;

import java.util.List;

@Schema(description = "Basic diagnostics, not a proof of infeasibility; issues do not block launching a solver")
public record PrecheckResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Collection is still open or some participants have not submitted") boolean incompleteData,
        @NotNull List<Long> nonRespondentIds,
        @NotNull List<ApiIssue> issues) {
}
