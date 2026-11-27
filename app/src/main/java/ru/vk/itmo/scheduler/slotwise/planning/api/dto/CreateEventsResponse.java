package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;

public record CreateEventsResponse(
        @NotNull List<EventResponse> events, @NotNull List<ApiIssue> issues,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion) {
}

