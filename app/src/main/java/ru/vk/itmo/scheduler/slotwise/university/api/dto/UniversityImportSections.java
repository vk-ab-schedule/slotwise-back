package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.constraints.NotNull;

public record UniversityImportSections(
        @NotNull UniversityImportCounts accounts,
        @NotNull UniversityImportCounts groups,
        @NotNull UniversityImportCounts rooms,
        @NotNull UniversityImportCounts disciplines,
        @NotNull UniversityImportCounts teaching,
        @NotNull UniversityImportCounts supervision,
        @NotNull UniversityImportCounts timetable) {
}
