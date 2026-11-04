package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;

@Schema(description = "Expand one template for selected groups/students; deduplicate students, derive teachers and supervisors. Missing links are reported without guessing participants.")
public record CreateEventsRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull @Min(1) Long eventTypeId,
        @Min(1) Long disciplineId,
        @NotNull Set<@NotNull @Min(1) Long> studyGroupIds,
        @NotNull Set<@NotNull @Min(1) Long> studentIds) {
}
