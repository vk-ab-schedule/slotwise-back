package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UniversityEquipmentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @NotNull String name) {
}
