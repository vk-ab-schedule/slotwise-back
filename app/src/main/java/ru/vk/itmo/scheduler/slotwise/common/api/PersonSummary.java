package ru.vk.itmo.scheduler.slotwise.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record PersonSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @NotNull String lastName,
        @NotNull String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String patronymic,
        @NotNull AccountRole role) {
}
