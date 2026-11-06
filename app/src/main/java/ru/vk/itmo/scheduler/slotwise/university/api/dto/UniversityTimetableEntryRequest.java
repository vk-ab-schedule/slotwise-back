package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Exactly one of accountId and roomId identifies the occupied resource; endsAt must follow startsAt.
 */
public record UniversityTimetableEntryRequest(
        @Schema(nullable = true) @Size(max = 255) String externalId,
        @Schema(nullable = true) @Min(1) Long accountId,
        @Schema(nullable = true) @Min(1) Long roomId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Schema(nullable = true) String description) {
}
