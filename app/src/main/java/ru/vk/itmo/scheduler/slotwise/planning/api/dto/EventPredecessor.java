package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

public record EventPredecessor(
        @NotNull @Min(1) Long predecessorEventId,
        @NotNull @PositiveOrZero Integer minGapDays,
        @PositiveOrZero @Schema(nullable = true, description = "Optional maximum difference of session-local dates; must be at least minGapDays") Integer maxGapDays) {

    @AssertTrue(message = "maxGapDays must be greater than or equal to minGapDays")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isGapValid() {
        return maxGapDays == null || minGapDays == null || maxGapDays >= minGapDays;
    }
}
