package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.planning.BusyImportance;
import ru.vk.itmo.scheduler.slotwise.planning.ResponseStatus;

public final class BusyCalendar {
    private BusyCalendar() {
    }

    public record IntervalRequest(@NotNull Instant startsAt, @NotNull Instant endsAt,
                                  @NotNull BusyImportance importance) {
    }

    public record ReplaceRequest(
            @NotBlank @Schema(description = "Opaque revision returned by GET, compared atomically before replacement") String expectedRevision,
            @NotNull @Size(max = 2000) List<@Valid @NotNull IntervalRequest> intervals) {
    }

    public record SubmitRequest(@NotBlank String expectedRevision) {
    }

    public record Interval(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
            @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull BusyImportance importance) {
    }

    public record KnownBusy(
            @NotNull Instant startsAt, @NotNull Instant endsAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description) {
    }

    @Schema(name = "BusyCalendarResponse")
    public record Response(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionId,
            @NotNull String revision, @NotNull String timeZone,
            @NotNull ResponseStatus responseStatus,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant submittedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean editable,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean timetableAvailable,
            @NotNull List<Interval> intervals,
            @NotNull List<KnownBusy> knownBusy,
            @NotNull List<ApiIssue> issues) {
    }
}

