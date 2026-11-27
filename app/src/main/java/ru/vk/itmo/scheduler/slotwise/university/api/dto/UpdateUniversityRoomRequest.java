package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUniversityRoomRequest(
        @Schema(nullable = true) @Size(max = 255) String externalId,
        @NotBlank @Size(max = 255) String name,
        @Schema(nullable = true) @Size(max = 255) String building,
        @NotNull Boolean online,
        @Schema(nullable = true) @Min(1) Integer capacity,
        @NotNull List<@NotNull @Min(1) Long> equipmentIds,
        @NotNull Boolean active,
        @NotNull @PositiveOrZero Long expectedVersion) {
}
