package ru.vk.itmo.scheduler.slotwise.account.api.dto;

import jakarta.validation.constraints.NotNull;

public record AccountRoleImpactSession(long id, @NotNull String name) {
}
