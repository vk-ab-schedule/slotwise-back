package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateUniversityDisciplineRequest(
        @Schema(nullable = true) @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name,
        @NotNull Boolean active,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
