package ru.vk.itmo.scheduler.slotwise.common.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Diagnostics use IDs and human-readable explanations, never another user's contacts.
 */
public record ApiIssue(
        @NotNull String code,
        @NotNull String message,
        @NotNull IssueSeverity severity,
        @NotNull List<Long> eventIds,
        @NotNull List<Long> accountIds,
        @NotNull List<Long> roomIds) {
}
