package ru.vk.itmo.scheduler.slotwise.domain.precheck;

import java.util.List;

/**
 * A problem of the input found before solving. It does not block a solve: an ERROR means some events
 * will almost surely stay unassigned, a WARNING points at a likely cause. The message is for the organizer.
 */
public record PrecheckIssue(Code code, Severity severity, String message, List<Long> eventIds, List<Long> personIds,
                            List<Long> roomIds) {

    public PrecheckIssue {
        eventIds = eventIds.stream().distinct().sorted().toList();
        personIds = personIds.stream().distinct().sorted().toList();
        roomIds = roomIds.stream().distinct().sorted().toList();
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Code {
        /** Required participants of an event have no common free window of its length. */
        NO_COMMON_WINDOW,
        /** No room fits the format, the capacity and the equipment of an event. */
        NO_ROOM,
        /** The pool without the excluded people is smaller than the commission or has no chair. */
        COMMISSION_POOL_TOO_SMALL,
        /** A teacher's required events take longer than the teacher's free working time. */
        TEACHER_OVERLOADED,
        /** A student's events with preparation cannot be spread over the session days. */
        PREPARATION_DOES_NOT_FIT,
        /** A person has no free working time at all. */
        NO_FREE_TIME
    }
}
