package ru.vk.itmo.scheduler.slotwise.domain.precheck;

import java.util.List;

/**
 * A problem of the input found before solving. It does not block a solve: an ERROR means some events
 * will almost surely stay unassigned, a WARNING points at a likely cause. The message names no ids: the
 * application shows the events, people and rooms from the id lists by their names.
 *
 * @param eventIds for {@link Code#DEPENDENCY_DOES_NOT_FIT}, the event that must come first, then the other one
 */
public record PrecheckIssue(Code code, Severity severity, String message, List<Long> eventIds, List<Long> personIds,
                            List<Long> roomIds) {

    public PrecheckIssue {
        eventIds = eventIds.stream().distinct().toList();
        personIds = personIds.stream().distinct().sorted().toList();
        roomIds = roomIds.stream().distinct().sorted().toList();
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Code {
        /** The session period has no working time at all. */
        NO_WORKING_TIME,
        /** An event is longer than every working day. */
        EVENT_LONGER_THAN_WORKING_DAY,
        /** Required participants of an event have no common free window of its length. */
        NO_COMMON_WINDOW,
        /** No room fits the format, the capacity and the equipment of an event. */
        NO_ROOM,
        /** The pool without the excluded people is smaller than the commission or has no chair. */
        COMMISSION_POOL_TOO_SMALL,
        /** A teacher's required events take longer than the teacher's free working time. */
        TEACHER_OVERLOADED,
        /** Students' events with preparation cannot be spread over the session days. */
        PREPARATION_DOES_NOT_FIT,
        /** No two working days of the session are as far apart as a dependency needs. */
        DEPENDENCY_DOES_NOT_FIT,
        /** Pinned assignments break hard constraints, so no solver can keep them all. */
        PINNED_CONFLICT,
        /** People have no free working time at all. */
        NO_FREE_TIME
    }
}
