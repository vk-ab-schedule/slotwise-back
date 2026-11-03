package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.NonNull;

import java.util.Comparator;
import java.util.List;

/**
 * Participant of the session. The id is the account id.
 */
public record Person(long id, @NonNull Role role, List<Busy> busy) {

    public Person {
        busy = busy.stream()
                .sorted(Comparator.comparing(Busy::range).thenComparing(Busy::kind))
                .toList();
    }

    public enum Role {
        TEACHER,
        STUDENT
    }

    /**
     * Busy time of a person.
     */
    public record Busy(@NonNull SlotRange range, @NonNull Kind kind) {

        public enum Kind {
            /** Classes from the university timetable: always hard (H4). */
            TIMETABLE,
            /** Marked as critical by the person: hard, except for students in group events (H4, S3). */
            CRITICAL,
            /** Undesirable: soft (S3). */
            MEDIUM,
            /** Slightly undesirable: soft (S3). */
            LOW
        }
    }
}
