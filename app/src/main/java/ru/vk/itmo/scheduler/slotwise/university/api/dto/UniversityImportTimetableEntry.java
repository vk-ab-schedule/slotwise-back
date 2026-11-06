package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** External references, not database IDs. Exactly one of account and room is required; end must follow start. */
public record UniversityImportTimetableEntry(
        @Schema(nullable = true) @Size(min = 1, max = 255) String account,
        @Schema(nullable = true) @Size(min = 1, max = 255) String room,
        @NotNull Instant start,
        @NotNull Instant end) {
}
