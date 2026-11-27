package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import ru.vk.itmo.scheduler.slotwise.common.api.MultipleOf;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

public final class SessionRules {
    private SessionRules() {
    }

    @Schema(description = "Exactly five nonnegative integer weights for S1-S5. Unassigned events U and soft penalty P "
            + "are independent: minimize U first, then P at the same U; no combined unassigned-event penalty is used.")
    public record Weights(
            @NotNull @PositiveOrZero Integer teacherGaps,
            @NotNull @PositiveOrZero Integer commissionLoadSpread,
            @NotNull @PositiveOrZero Integer undesirableTime,
            @NotNull @PositiveOrZero Integer teacherRoomChanges,
            @NotNull @PositiveOrZero Integer movedEvents) {
    }

    public record UpdateRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @Valid Weights weights,
            @NotNull @PositiveOrZero @MultipleOf(TimeGrid.DEFAULT_SLOT_MINUTES)
            @Schema(multipleOf = TimeGrid.DEFAULT_SLOT_MINUTES, description = "Room-change buffer in minutes; default is 15")
            Integer roomChangeBufferMinutes,
            @NotNull SolverAlgorithm defaultAlgorithm,
            @NotNull @Min(1) @Max(300) Integer timeLimitSeconds,
            @NotNull Long seed) {
    }

    @Schema(name = "SessionRulesResponse")
    public record Response(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inputVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", example = "5",
                    accessMode = Schema.AccessMode.READ_ONLY,
                    description = "Server-chosen model parameter in minutes, currently 5; informational and not editable in requests")
            int slotMinutes,
            @NotNull Weights weights,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", multipleOf = TimeGrid.DEFAULT_SLOT_MINUTES,
                    description = "Room-change buffer in minutes; default is 15") int roomChangeBufferMinutes,
            @NotNull SolverAlgorithm defaultAlgorithm,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int timeLimitSeconds,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long seed) {
    }
}

