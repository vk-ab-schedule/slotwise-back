package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SetAccountEnabledRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull Boolean enabled) {
}
