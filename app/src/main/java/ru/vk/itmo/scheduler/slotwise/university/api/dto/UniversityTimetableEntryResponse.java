package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UniversityTimetableEntryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String externalId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long accountId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long roomId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description) {
}
