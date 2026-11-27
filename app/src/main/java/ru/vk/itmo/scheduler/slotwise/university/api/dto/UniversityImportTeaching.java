package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniversityImportTeaching(
        @NotBlank @Size(max = 255) String teacher,
        @NotBlank @Size(max = 255) String discipline,
        @NotBlank @Size(max = 255) String group) {
}
