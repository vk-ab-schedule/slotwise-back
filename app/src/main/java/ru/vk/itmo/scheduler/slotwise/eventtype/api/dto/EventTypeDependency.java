package ru.vk.itmo.scheduler.slotwise.eventtype.api.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record EventTypeDependency(
        @NotNull @Min(1) Long predecessorTypeId,
        @NotNull @PositiveOrZero @Schema(description = "Minimum difference of session-local calendar dates; zero permits the same day") Integer minGapDays,
        @PositiveOrZero @Schema(nullable = true, description = "Optional maximum difference of session-local dates; must be at least minGapDays") Integer maxGapDays) {
    @AssertTrue(message = "maxGapDays must be greater than or equal to minGapDays")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isGapValid() {
        return maxGapDays == null || minGapDays == null || maxGapDays >= minGapDays;
    }
}
