package ru.vk.itmo.scheduler.slotwise.datagen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * University data in the shape of the JSON import (UX-04): records reference each other by external id.
 * The application maps it to its import request; datagen does not depend on the application.
 *
 * @param session the session period the timetable covers: the demo shows it to the organizer, otherwise
 *                a session on other dates would show no classes at all
 */
public record UniversityData(
        SessionHint session,
        List<Account> accounts,
        List<Group> groups,
        List<Room> rooms,
        List<Discipline> disciplines,
        List<Teaching> teaching,
        List<Supervision> supervision,
        List<TimetableEntry> timetable) {

    public record SessionHint(LocalDate firstDay, LocalDate lastDay, ZoneId timeZone, LocalTime dayStart, LocalTime dayEnd) {
    }

    public enum Role {
        ADMIN,
        ORGANIZER,
        TEACHER,
        STUDENT
    }

    /**
     * @param passwordHash Spring {@code {bcrypt}} hash of {@link DatasetGenerator#DEMO_PASSWORD}
     */
    public record Account(String externalId, String email, String lastName, String firstName, String patronymic,
                          Role role, String timeZone, String passwordHash) {
    }

    public record Group(String externalId, String name, List<String> students) {
    }

    /**
     * @param capacity null for the online platform
     */
    public record Room(String externalId, String name, String building, boolean online, Integer capacity,
                       List<String> equipment) {
    }

    public record Discipline(String externalId, String name) {
    }

    public record Teaching(String teacher, String discipline, String group) {
    }

    public record Supervision(String student, String supervisor) {
    }

    /**
     * Classes of a person or a room: exactly one of {@code account} and {@code room} is set.
     */
    public record TimetableEntry(String account, String room, Instant start, Instant end) {
    }
}
