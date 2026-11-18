package ru.vk.itmo.scheduler.slotwise.domain.validation;

import java.util.List;

/**
 * A broken hard constraint with the events, people and rooms involved. The message is for the organizer.
 */
public record Violation(HardConstraint constraint, String message, List<Long> eventIds, List<Long> personIds,
                        List<Long> roomIds) {

    public Violation {
        eventIds = eventIds.stream().distinct().sorted().toList();
        personIds = personIds.stream().distinct().sorted().toList();
        roomIds = roomIds.stream().distinct().sorted().toList();
    }
}
