package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUniversityDisciplineRequest(
        @Schema(nullable = true) @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name) {
}
