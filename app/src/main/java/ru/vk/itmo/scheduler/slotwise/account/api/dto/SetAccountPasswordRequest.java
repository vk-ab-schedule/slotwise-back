package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SetAccountPasswordRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 128)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, format = "password") String password) {
    @Override
    public String toString() {
        return "SetAccountPasswordRequest[expectedVersion=" + expectedVersion + ", password=***]";
    }
}
