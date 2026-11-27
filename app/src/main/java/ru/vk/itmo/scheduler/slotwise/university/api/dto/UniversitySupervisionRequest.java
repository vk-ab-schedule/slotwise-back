package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UniversitySupervisionRequest(
        @NotNull @Min(1) Long studentId,
        @NotNull @Min(1) Long supervisorId) {
}
