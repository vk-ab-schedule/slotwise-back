package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Public commission identity; chair distinguishes the chairperson from other members")
public record ScheduleCommissionMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long accountId,
        @NotNull String lastName,
        @NotNull String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String patronymic,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean chair) {
}
