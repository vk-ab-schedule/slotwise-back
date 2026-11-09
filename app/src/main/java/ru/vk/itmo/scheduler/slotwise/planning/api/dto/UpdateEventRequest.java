package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.vk.itmo.scheduler.slotwise.common.api.MultipleOf;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;

import java.util.List;
import java.util.Set;

@Schema(description = "Replace the complete editable event form. Dependencies must remain acyclic; business validation is implemented by B5.")
public record UpdateEventRequest(@NotNull @PositiveOrZero Long expectedVersion,
                                 @NotBlank @Size(max = 255) String title,
                                 @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
                                 @NotNull @Min(5) @MultipleOf(5) @Schema(multipleOf = 5) Integer durationMinutes,
                                 @NotNull EventFormat format,
                                 @NotNull @PositiveOrZero Integer preparationDays,
                                 @NotNull @PositiveOrZero Integer commissionSize,
                                 @NotNull Boolean requiresChair,
                                 @NotNull List<@Valid @NotNull EventParticipant> participants,
                                 @NotNull Set<@NotNull @Min(1) Long> equipmentIds,
                                 @NotNull List<@Valid @NotNull EventPredecessor> dependencies) {

    @AssertTrue(message = "commissionSize must be positive when requiresChair is true")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isCommissionValid() {
        return !Boolean.TRUE.equals(requiresChair) || commissionSize == null || commissionSize > 0;
    }
}
