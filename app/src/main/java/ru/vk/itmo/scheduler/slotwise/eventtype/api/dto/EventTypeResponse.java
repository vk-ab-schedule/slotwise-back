package ru.vk.itmo.scheduler.slotwise.eventtype.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;

import java.util.List;

public record EventTypeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "5", multipleOf = 5) int defaultDurationMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventGranularity granularity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int preparationDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventFormat defaultFormat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int commissionSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean requiresChair,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ParticipantRole> participantRoles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> equipmentIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EventTypeDependency> dependencies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long version) {
}
