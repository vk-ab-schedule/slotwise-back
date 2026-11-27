package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.planning.CollectionStatus;
import ru.vk.itmo.scheduler.slotwise.planning.ResponseStatus;

public record CollectionResponse(
        @NotNull CollectionStatus status,
        @NotNull Instant startsAt, @NotNull Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
        @NotNull List<ParticipantResponse> participants) {

    public record ParticipantResponse(
            @NotNull PersonSummary person,
            @NotNull ResponseStatus responseStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant submittedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean noFreeWindow) {
    }
}

