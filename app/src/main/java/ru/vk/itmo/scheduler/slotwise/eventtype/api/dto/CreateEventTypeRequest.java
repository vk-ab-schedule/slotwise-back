package ru.vk.itmo.scheduler.slotwise.eventtype.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.vk.itmo.scheduler.slotwise.common.api.MultipleOf;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;

import java.util.List;

public record CreateEventTypeRequest(

        @NotBlank @Size(max = 255) String name,
        @NotNull @Min(5) @MultipleOf(5) @Schema(multipleOf = 5, example = "20") Integer defaultDurationMinutes,
        @NotNull EventGranularity granularity,
        @NotNull @PositiveOrZero Integer preparationDays,
        @NotNull EventFormat defaultFormat,
        @NotNull @PositiveOrZero Integer commissionSize,
        @NotNull Boolean requiresChair,
        @NotNull Boolean active,
        @NotEmpty List<@NotNull ParticipantRole> participantRoles,
        @NotNull List<@NotNull @Min(1) Long> equipmentIds,
        @NotNull List<@NotNull @Valid EventTypeDependency> dependencies) {

    @AssertTrue(message = "commissionSize must be positive when requiresChair is true")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isCommissionValid() {
        return !Boolean.TRUE.equals(requiresChair) || commissionSize == null || commissionSize > 0;
    }
}
