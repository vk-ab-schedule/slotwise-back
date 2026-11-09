package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.List;

/**
 * Physical room or online platform. An online platform has no capacity limit or room busy time.
 *
 * @param equipment ids of the equipment in the room
 * @param busy      classes from the university timetable (H3)
 */
public record Room(long id, Integer capacity, List<Long> equipment, List<SlotRange> busy, boolean online) {

    public Room(long id, int capacity, List<Long> equipment, List<SlotRange> busy) {
        this(id, capacity, equipment, busy, false);
    }

    public Room {
        if (online && (capacity != null || !busy.isEmpty())) {
            throw new IllegalArgumentException("Online platform " + id + " must have no capacity or room busy time");
        }
        if (!online && (capacity == null || capacity <= 0)) {
            throw new IllegalArgumentException("Room " + id + " must have a positive capacity: " + capacity);
        }
        equipment = Ids.sortedUnique(equipment, "equipment of room " + id);
        busy = busy.stream().sorted().toList();
    }
}
