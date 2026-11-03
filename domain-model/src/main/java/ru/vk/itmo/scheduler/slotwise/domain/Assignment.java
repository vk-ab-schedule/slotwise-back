package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.NonNull;

import java.util.List;

/**
 * Placement of one event: start slot, venue and commission members (empty if the event has no commission).
 * Events that are not placed have no assignment.
 */
public record Assignment(long eventId, int start, @NonNull Venue venue, List<Long> commission) {

    public Assignment {
        if (start < 0) {
            throw new IllegalArgumentException("Event " + eventId + " starts before the grid: " + start);
        }
        commission = Ids.sortedUnique(commission, "commission of event " + eventId);
    }
}
