package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniversityImportDiscipline(
        @NotBlank @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name) {
}
