package ru.vk.itmo.scheduler.slotwise.domain.validation;

import java.util.List;

/**
 * A broken hard constraint. The message is for the organizer and names no ids: the application shows
 * the events, people and rooms from the id lists by their names. One violation covers everybody it affects,
 * so a clash of two group events is one violation with all its students, not one per student.
 *
 * @param eventIds for {@link Reason#DEPENDENCY_BROKEN}, the event that must come first, then the other one
 */
public record Violation(Reason reason, String message, List<Long> eventIds, List<Long> personIds, List<Long> roomIds) {

    public Violation {
        eventIds = eventIds.stream().distinct().toList();
        personIds = personIds.stream().distinct().sorted().toList();
        roomIds = roomIds.stream().distinct().sorted().toList();
    }

    public HardConstraint constraint() {
        return reason.constraint();
    }

    /**
     * A stable code for the frontend, finer than the constraint number.
     */
    public enum Reason {
        UNKNOWN_EVENT(HardConstraint.H1),
        DUPLICATE_ASSIGNMENT(HardConstraint.H1),
        PERSON_DOUBLE_BOOKED(HardConstraint.H2),
        NO_TIME_TO_CHANGE_ROOM(HardConstraint.H2),
        ROOM_DOUBLE_BOOKED(HardConstraint.H3),
        ROOM_HAS_CLASSES(HardConstraint.H3),
        PERSON_HAS_CLASSES(HardConstraint.H4),
        PERSON_CRITICALLY_BUSY(HardConstraint.H4),
        UNKNOWN_ROOM(HardConstraint.H5),
        ONLINE_EVENT_IN_ROOM(HardConstraint.H5),
        ONSITE_EVENT_ONLINE(HardConstraint.H5),
        ROOM_TOO_SMALL(HardConstraint.H5),
        ROOM_LACKS_EQUIPMENT(HardConstraint.H5),
        COMMISSION_SIZE(HardConstraint.H6),
        COMMISSION_MEMBER_NOT_ALLOWED(HardConstraint.H6),
        CHAIR_NOT_ALLOWED(HardConstraint.H6),
        CHAIR_MISSING(HardConstraint.H6),
        DEPENDENCY_BROKEN(HardConstraint.H7),
        OUTSIDE_WORKING_HOURS(HardConstraint.H8),
        PINNED_CHANGED(HardConstraint.H9),
        PREPARATION_TOO_SHORT(HardConstraint.H10);

        private final HardConstraint constraint;

        Reason(HardConstraint constraint) {
            this.constraint = constraint;
        }

        public HardConstraint constraint() {
            return constraint;
        }
    }
}
