package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.NonNull;

import java.util.List;
import java.util.OptionalLong;

/**
 * Placement of one event: start slot, room (a physical room or an online platform, see {@link Room#online()}),
 * commission members and optional chair. Events that are not placed have no assignment.
 */
public record Assignment(long eventId, int start, long roomId, List<Long> commission, @NonNull OptionalLong chair) {

    public Assignment(long eventId, int start, long roomId, List<Long> commission) {
        this(eventId, start, roomId, commission, OptionalLong.empty());
    }

    public Assignment {
        if (start < 0) {
            throw new IllegalArgumentException("Event " + eventId + " starts before the grid: " + start);
        }
        commission = Ids.sortedUnique(commission, "commission of event " + eventId);
        if (chair.isPresent() && !commission.contains(chair.getAsLong())) {
            throw new IllegalArgumentException("Chair " + chair.getAsLong() + " is not in the commission of event " + eventId);
        }
    }
}
