package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UniversityImportGroup(
        @NotBlank @Size(max = 255) String externalId,
        @Schema(nullable = true, description = "Optional display name; defaults to externalId when omitted") @Size(max = 255) String name,
        @NotNull List<@NotBlank @Size(max = 255) String> students) {
}
