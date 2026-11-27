package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

public record SetAccountRoleRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull AccountRole role) {
}
