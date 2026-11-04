package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.vk.itmo.scheduler.slotwise.schedule.AssignmentStatus;

import java.time.Instant;
import java.util.List;

@Schema(description = "Complete editable assignment. Conditional time/resource/commission checks belong to the "
        + "domain validator (422), alongside hard constraints; field validation returns 400.")
public record AssignmentUpdateRequest(
        @NotNull @PositiveOrZero Long expectedVariantVersion,
        @NotNull @PositiveOrZero Long expectedAssignmentVersion,
        @NotNull AssignmentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Required for ASSIGNED; null for UNASSIGNED") Instant startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Exclusive end, required for ASSIGNED; null for UNASSIGNED") Instant endsAt,
        @Min(1) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Room or online-platform ID for ASSIGNED; null for UNASSIGNED") Long roomId,
        @NotNull List<@NotNull @Valid CommissionMember> commissionMembers,
        @NotNull @Schema(description = "Explicit lock state; a lock does not publish the event") Boolean locked) {

    @Schema(name = "AssignmentCommissionMemberRequest")
    public record CommissionMember(@NotNull @Min(1) Long accountId, @NotNull Boolean chair) {
    }
}
