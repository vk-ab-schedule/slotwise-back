package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.List;
import java.util.Objects;

/**
 * Placement of one event: start slot, venue and commission members (empty if the event has no commission).
 * Events that are not placed have no assignment.
 */
public record Assignment(long eventId, int start, Venue venue, List<Long> commission) {

    public Assignment {
        if (start < 0) {
            throw new IllegalArgumentException("Event " + eventId + " starts before the grid: " + start);
        }
        Objects.requireNonNull(venue, "venue");
        commission = Ids.sortedUnique(commission, "commission of event " + eventId);
    }
}
