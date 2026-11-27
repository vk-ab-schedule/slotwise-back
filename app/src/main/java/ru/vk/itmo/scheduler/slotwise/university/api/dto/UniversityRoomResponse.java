package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UniversityRoomResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String externalId,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String building,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer capacity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean online,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
        @NotNull List<Long> equipmentIds) {
}
