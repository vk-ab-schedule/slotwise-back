package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.List;

/**
 * Physical room. The online venue is not a room, see {@link Venue#ONLINE}.
 *
 * @param equipment ids of the equipment in the room
 * @param busy      classes from the university timetable (H3)
 */
public record Room(long id, int capacity, List<Long> equipment, List<SlotRange> busy) {

    public Room {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Room " + id + " must have a positive capacity: " + capacity);
        }
        equipment = Ids.sortedUnique(equipment, "equipment of room " + id);
        busy = busy.stream().sorted().toList();
    }
}
