package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UniversityEquipmentRequest(
        @NotBlank @Size(max = 100) String name) {
}
