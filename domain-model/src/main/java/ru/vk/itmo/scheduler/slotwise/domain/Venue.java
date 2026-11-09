package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Where an event takes place: a physical room or an online platform (unlimited, never busy).
 */
public sealed interface Venue {
    long roomId();

    static Venue room(long roomId) {
        return new InRoom(roomId);
    }

    record InRoom(long roomId) implements Venue {
    }

    record Online(long roomId) implements Venue {
    }
}
