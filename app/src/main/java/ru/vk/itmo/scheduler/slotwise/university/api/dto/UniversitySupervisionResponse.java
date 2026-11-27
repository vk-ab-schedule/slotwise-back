package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UniversitySupervisionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long studentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long supervisorId) {
}
