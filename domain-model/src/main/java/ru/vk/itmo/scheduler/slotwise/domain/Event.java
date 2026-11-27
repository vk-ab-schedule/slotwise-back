package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.NonNull;

import java.util.List;

/**
 * Event to schedule. The event type is not part of the model: the application copies its values
 * into the event, so a new type needs no solver changes.
 *
 * @param duration           length in slots
 * @param participants       required participants: students, discipline teacher, supervisor…
 * @param commissionExcluded people who may not sit on the commission of this event, such as the supervisor at a defense (H6)
 * @param equipment          ids of the equipment the room must have
 * @param preparationDays    days a student needs before this event after another event with preparation (H10)
 */
public record Event(
        long id,
        int duration,
        @NonNull Scope scope,
        @NonNull Format format,
        List<Long> participants,
        @NonNull CommissionRequirement commission,
        List<Long> commissionExcluded,
        List<Long> equipment,
        int preparationDays) {

    public Event {
        if (duration <= 0) {
            throw new IllegalArgumentException("Event " + id + " must have a positive duration: " + duration);
        }
        participants = Ids.sortedUnique(participants, "participants of event " + id);
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Event " + id + " has no participants");
        }
        commissionExcluded = Ids.sortedUnique(commissionExcluded, "commission exclusions of event " + id);
        equipment = Ids.sortedUnique(equipment, "equipment of event " + id);
        if (preparationDays < 0) {
            throw new IllegalArgumentException("Event " + id + " has negative preparation days: " + preparationDays);
        }
    }

    public enum Scope {
        /** For one student, such as a defense: critical busy time of the student is hard. */
        INDIVIDUAL,
        /** For a group, such as an exam: critical busy time of a single student is soft (S3). */
        GROUP
    }

    public enum Format {
        ONSITE,
        ONLINE,
        HYBRID
    }

    /**
     * @param size          number of commission members, 0 if the event has no commission
     * @param chairRequired at least one member must be able to chair
     */
    public record CommissionRequirement(int size, boolean chairRequired) {
        public static final CommissionRequirement NONE = new CommissionRequirement(0, false);

        public CommissionRequirement {
            if (size < 0) {
                throw new IllegalArgumentException("Commission size must not be negative: " + size);
            }
            if (chairRequired && size == 0) {
                throw new IllegalArgumentException("A chair needs a commission");
            }
        }
    }
}
