package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record CreateAccountRequest(
        @Size(max = 255) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String externalId,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String patronymic,
        @NotNull AccountRole role,
        @NotBlank @Size(max = 64) @Schema(example = "Europe/Moscow") String timeZone) {
}
