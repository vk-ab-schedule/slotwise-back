package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUniversityGroupRequest(
        @Schema(nullable = true) @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name,
        @NotNull List<@NotNull @Min(1) Long> studentIds,
        @NotNull Boolean active,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
