package ru.vk.itmo.scheduler.slotwise.planning;

import java.time.Instant;

public enum CollectionStatus {
    PENDING,
    OPEN,
    CLOSED;

    CollectionStatus effectiveAt(Instant now, Instant start, Instant end) {
        if (this == CLOSED || !now.isBefore(end)) {
            return CLOSED;
        }
        return now.isBefore(start) ? PENDING : OPEN;
    }
}
