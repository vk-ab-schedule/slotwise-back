package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UniversityTeachingRequest(
        @NotNull @Min(1) Long teacherId,
        @NotNull @Min(1) Long disciplineId,
        @NotNull @Min(1) Long groupId) {
}
