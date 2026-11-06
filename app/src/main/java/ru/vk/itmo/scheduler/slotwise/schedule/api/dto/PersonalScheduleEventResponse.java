package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;

import java.time.Instant;
import java.util.List;

@Schema(description = "An assigned event from a current publication; participation includes commission membership")
public record PersonalScheduleEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @NotNull String sessionName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long variantId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eventTypeId,
        @NotNull String title,
        @NotNull EventFormat format,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull ScheduleRoomResponse room,
        @NotNull List<ScheduleParticipantResponse> participants,
        @NotNull List<ScheduleCommissionMemberResponse> commissionMembers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Assignment changed relative to the previous publication") boolean changed) {
}
