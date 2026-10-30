package ru.vk.itmo.scheduler.slotwise.auth;

import ru.vk.itmo.scheduler.slotwise.account.AccountEntity;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record CurrentUserResponse(
        long id,
        String email,
        String lastName,
        String firstName,
        String patronymic,
        AccountRole role,
        String timeZone) {

    static CurrentUserResponse of(AccountEntity account) {
        return new CurrentUserResponse(account.getId(), account.getEmail(), account.getLastName(),
                account.getFirstName(), account.getPatronymic(), account.getRole(), account.getTimeZone());
    }
}
