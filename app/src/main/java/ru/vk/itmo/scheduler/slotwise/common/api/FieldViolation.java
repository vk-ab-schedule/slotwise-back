package ru.vk.itmo.scheduler.slotwise.common.api;

import jakarta.validation.constraints.NotNull;

/**
 * Rejected values are intentionally absent: a field may contain credentials.
 */
public record FieldViolation(@NotNull String field, @NotNull String message) {
}
