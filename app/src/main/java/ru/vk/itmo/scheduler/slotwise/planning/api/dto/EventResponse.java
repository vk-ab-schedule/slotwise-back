package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

import ru.vk.itmo.scheduler.slotwise.common.api.MultipleOf;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;

public record EventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long eventTypeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long disciplineId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long studyGroupId,
        @NotBlank @Size(max = 255) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
        @NotNull @Min(5) @MultipleOf(5) @Schema(multipleOf = 5) Integer durationMinutes,
        @NotNull EventFormat format,
        @NotNull @Schema(description = "Copied from the template when the event is created; later template edits do not change it") EventGranularity granularity,
        @NotNull @PositiveOrZero Integer preparationDays,
        @NotNull @PositiveOrZero Integer commissionSize,
        @NotNull Boolean requiresChair,
        @NotNull List<@Valid @NotNull EventParticipant> participants,
        @NotNull Set<@NotNull @Min(1) Long> equipmentIds,
        @NotNull List<@Valid @NotNull EventPredecessor> dependencies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean complete,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version) {
}
