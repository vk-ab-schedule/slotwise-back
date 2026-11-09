package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AssignmentCandidate(
        @NotNull @Schema(description = "Start time on the session grid") Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1",
                description = "Identifier of a physical room or an online platform") long roomId) {
}
