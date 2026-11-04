package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

public record CreatePlanningSessionRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 64) @Schema(example = "Europe/Moscow") String timeZone,
        @NotEmpty Set<@NotNull DayOfWeek> workingDays,
        @NotNull @Schema(type = "string", format = "local-time", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9]([.][0-9]{1,9})?)?$", example = "09:00:00") LocalTime workingDayStart,
        @NotNull @Schema(type = "string", format = "local-time", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9]([.][0-9]{1,9})?)?$", example = "19:00:00") LocalTime workingDayEnd,
        @NotNull Instant collectionStartsAt,
        @NotNull Instant collectionEndsAt) {
}
