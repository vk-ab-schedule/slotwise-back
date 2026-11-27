package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UniversityImportRequest(
        @NotNull @Valid List<@NotNull UniversityImportAccount> accounts,
        @NotNull @Valid List<@NotNull UniversityImportGroup> groups,
        @NotNull @Valid List<@NotNull UniversityImportRoom> rooms,
        @NotNull @Valid List<@NotNull UniversityImportDiscipline> disciplines,
        @NotNull @Valid List<@NotNull UniversityImportTeaching> teaching,
        @NotNull @Valid List<@NotNull UniversityImportSupervision> supervision,
        @NotNull @Valid List<@NotNull UniversityImportTimetableEntry> timetable) {
}
