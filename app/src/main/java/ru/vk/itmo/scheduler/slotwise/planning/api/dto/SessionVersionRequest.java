package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SessionVersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {
}
