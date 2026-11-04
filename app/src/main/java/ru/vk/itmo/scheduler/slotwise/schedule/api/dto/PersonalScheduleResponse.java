package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

@Schema(description = "Authenticated person's published assignments only, with public participant identities")
public record PersonalScheduleResponse(
        @NotNull Instant from,
        @NotNull Instant to,
        @NotNull @Schema(description = "Authenticated person's IANA display time zone, e.g. Europe/Moscow") String timeZone,
        @NotNull @Schema(description = "Events overlapping [from, to); empty when no assignments exist")
        List<PersonalScheduleEventResponse> events) {
}
