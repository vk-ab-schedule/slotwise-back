package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;

/**
 * The JSON path identifies the rejected record; diagnostics never contain passwords or hashes.
 */
public record UniversityImportDiagnostic(
        @NotNull String path,
        @NotNull ApiIssue issue) {
}
