package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniversityImportSupervision(
        @NotBlank @Size(max = 255) String student,
        @NotBlank @Size(max = 255) String supervisor) {
}
