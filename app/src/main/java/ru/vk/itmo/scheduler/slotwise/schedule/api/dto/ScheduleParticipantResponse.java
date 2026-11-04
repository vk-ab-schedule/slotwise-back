package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Public participant identity and role in this event, not an account profile")
public record ScheduleParticipantResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long accountId,
        @NotNull String lastName,
        @NotNull String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String patronymic,
        @NotNull ParticipantRole role) {
}
