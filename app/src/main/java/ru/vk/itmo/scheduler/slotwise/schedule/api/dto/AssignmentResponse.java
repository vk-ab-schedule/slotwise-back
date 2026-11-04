package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.schedule.AssignmentStatus;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Organizer's assignment detail, including UNASSIGNED events. For UNASSIGNED, times and room "
        + "are null and the commission is empty. Explanations are limited basic diagnostics, not a unique cause.")
public record AssignmentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eventTypeId,
        @NotNull String title,
        @NotNull EventFormat format,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "5", multipleOf = 5) int durationMinutes,
        @NotNull List<ScheduleParticipantResponse> participants,
        @NotNull AssignmentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant endsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ScheduleRoomResponse room,
        @NotNull List<ScheduleCommissionMemberResponse> commissionMembers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean locked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Assignment version, checked separately from the variant version") long version,
        @NotNull List<ApiIssue> issues) {
}
