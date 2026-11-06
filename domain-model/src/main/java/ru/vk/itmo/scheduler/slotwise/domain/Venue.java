package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Where an event takes place: a physical room or the online venue (unlimited, never busy).
 */
public sealed interface Venue {
    Venue ONLINE = new Online();

    static Venue room(long roomId) {
        return new InRoom(roomId);
    }

    record InRoom(long roomId) implements Venue {
    }

    record Online() implements Venue {
    }
}
