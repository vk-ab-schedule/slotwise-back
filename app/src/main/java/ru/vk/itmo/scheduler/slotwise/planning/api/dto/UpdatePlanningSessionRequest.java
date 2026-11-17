package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

@Schema(description = "Replace editable session settings, including the collection window. Other fields are not changed.")
public record UpdatePlanningSessionRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 255) String name,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotBlank @Size(max = 64) @Schema(example = "Europe/Moscow") String timeZone,
        @NotEmpty Set<@NotNull DayOfWeek> workingDays,
        @NotNull @Schema(type = "string", format = "local-time", pattern = "^([01][0-9]|2[0-3]):[0-5][05](:00([.]0{1,9})?)?$", example = "09:00:00") LocalTime workingDayStart,
        @NotNull @Schema(type = "string", format = "local-time", pattern = "^([01][0-9]|2[0-3]):[0-5][05](:00([.]0{1,9})?)?$", example = "19:00:00") LocalTime workingDayEnd,
        @NotNull Instant collectionStartsAt,
        @NotNull Instant collectionEndsAt) {

    @AssertTrue(message = "workingDayStart and workingDayEnd must be on 5-minute boundaries")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isWorkingDayAligned() {
        long slotNanos = Duration.ofMinutes(TimeGrid.DEFAULT_SLOT_MINUTES).toNanos();
        return (workingDayStart == null || workingDayStart.toNanoOfDay() % slotNanos == 0)
                && (workingDayEnd == null || workingDayEnd.toNanoOfDay() % slotNanos == 0);
    }
}
