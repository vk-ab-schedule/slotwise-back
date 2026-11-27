package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Display information for a physical room or the unlimited online platform")
public record ScheduleRoomResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @NotNull String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Physical building; null for an online platform") String building,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean online) {
}
