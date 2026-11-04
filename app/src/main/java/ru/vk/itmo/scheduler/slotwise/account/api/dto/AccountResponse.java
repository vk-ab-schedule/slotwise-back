package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

import java.time.Instant;

public record AccountResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 255) String externalId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "email", maxLength = 320) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 100) String patronymic,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AccountRole role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64, example = "Europe/Moscow") String timeZone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether a password has been set; the password and its hash are never exposed") boolean hasPassword,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
}
