package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UniversityImportRoom(
        @NotBlank @Size(max = 255) String externalId,
        @Schema(nullable = true, description = "Optional display name; defaults to externalId when omitted") @Size(max = 255) String name,
        @Schema(nullable = true) @Size(max = 255) String building,
        @Schema(defaultValue = "false", description = "Online platform. Defaults to false when omitted.") Boolean online,
        @Schema(nullable = true, description = "Required for a physical room and null for an online platform")
        @Min(1) Integer capacity,
        @NotNull List<@NotBlank @Size(max = 100) String> equipment) {

    public UniversityImportRoom {
        online = Boolean.TRUE.equals(online);
    }

    @AssertTrue(message = "capacity is required for a physical room and must be empty for an online platform")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isCapacityValid() {
        return online ? capacity == null : capacity != null;
    }
}
