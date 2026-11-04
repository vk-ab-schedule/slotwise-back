package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UniversityImportRoom(
        @NotBlank @Size(max = 255) String externalId,
        @Schema(nullable = true, description = "Optional display name; defaults to externalId when omitted") @Size(max = 255) String name,
        @Schema(nullable = true) @Size(max = 255) String building,
        @Schema(nullable = true) @Min(1) Integer capacity,
        @NotNull List<@NotBlank @Size(max = 100) String> equipment) {
}
