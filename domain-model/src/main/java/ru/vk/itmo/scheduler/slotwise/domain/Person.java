package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Participant of the session. The id is the account id.
 */
public record Person(long id, Role role, List<Busy> busy) {

    public Person {
        Objects.requireNonNull(role, "role");
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
    public record Busy(SlotRange range, Kind kind) {

        public Busy {
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(kind, "kind");
        }

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
