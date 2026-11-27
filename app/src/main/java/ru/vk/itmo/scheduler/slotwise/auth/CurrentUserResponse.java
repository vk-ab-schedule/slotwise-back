package ru.vk.itmo.scheduler.slotwise.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import ru.vk.itmo.scheduler.slotwise.account.AccountEntity;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record CurrentUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long id,
        @NotNull String email,
        @NotNull String lastName,
        @NotNull String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String patronymic,
        @NotNull AccountRole role,
        @NotNull String timeZone) {

    static CurrentUserResponse of(AccountEntity account) {
        return new CurrentUserResponse(account.getId(), account.getEmail(), account.getLastName(),
                account.getFirstName(), account.getPatronymic(), account.getRole(), account.getTimeZone());
    }
}
