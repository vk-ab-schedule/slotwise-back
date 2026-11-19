package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * How busy time limits the events of a person (MODEL.md, H4 and S3). The validator, the prechecks and
 * the solvers all take the rules from here, so they agree on what is forbidden and what an overlap costs.
 */
public final class BusyRules {
    public static final int MEDIUM_COEFFICIENT = 3;
    public static final int LOW_COEFFICIENT = 1;
    public static final int STUDENT_CRITICAL_IN_GROUP_COEFFICIENT = 10;

    private BusyRules() {
    }

    /**
     * Classes are always hard. Critical time is hard too, except for a student in a group event:
     * otherwise one student would block the time of the whole group.
     */
    public static boolean isHard(Person.Role role, Person.Busy.Kind kind, Event.Scope scope) {
        return switch (kind) {
            case TIMETABLE -> true;
            case CRITICAL -> role != Person.Role.STUDENT || scope != Event.Scope.GROUP;
            case MEDIUM, LOW -> false;
        };
    }

    /**
     * Penalty per minute of overlap with soft busy time (S3); zero for hard busy time.
     * Where soft busy intervals overlap, the highest coefficient applies.
     */
    public static int softCoefficient(Person.Role role, Person.Busy.Kind kind, Event.Scope scope) {
        if (isHard(role, kind, scope)) {
            return 0;
        }
        return switch (kind) {
            case CRITICAL -> STUDENT_CRITICAL_IN_GROUP_COEFFICIENT;
            case MEDIUM -> MEDIUM_COEFFICIENT;
            case LOW -> LOW_COEFFICIENT;
            case TIMETABLE -> 0;
        };
    }
}
