package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Set;

import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;

public final class SessionSelection {
    private SessionSelection() {
    }

    public record ParticipantsRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull Set<@NotNull @Min(1) Long> studyGroupIds,
            @NotNull Set<@NotNull @Min(1) Long> accountIds) {
    }

    public record ParticipantsResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
            @NotNull Set<Long> studyGroupIds,
            @NotNull Set<Long> accountIds,
            @NotNull List<PersonSummary> participants) {
    }

    public record CommissionCandidate(@NotNull @Min(1) Long accountId, @NotNull Boolean canChair) {
    }

    public record CommissionPoolRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull List<@Valid @NotNull CommissionCandidate> candidates) {
    }

    public record CommissionPoolResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
            @NotNull List<CommissionCandidate> candidates) {
    }

    public record RoomsRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull Set<@NotNull @Min(1) Long> roomIds) {
    }

    public record RoomsResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
            @NotNull Set<Long> roomIds) {
    }
}

