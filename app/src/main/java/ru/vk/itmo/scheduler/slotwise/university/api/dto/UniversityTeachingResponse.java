package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UniversityTeachingResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long teacherId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long disciplineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long groupId) {
}
