package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.http.HttpStatus;

import java.time.Instant;

final class CollectionWindow {
    private CollectionWindow() {
    }

    static void requireEditable(CollectionStatus currentStatus, Instant currentEnd, Instant now,
                                Instant newStart, Instant newEnd, Instant sessionStart) {
        if (currentStatus == CollectionStatus.CLOSED || !now.isBefore(currentEnd)) {
            throw closed();
        }
        if (!newEnd.isAfter(now) || !newEnd.isAfter(newStart) || !newEnd.isBefore(sessionStart)) {
            throw new PlanningException(HttpStatus.BAD_REQUEST, "INVALID_COLLECTION_DEADLINE",
                    "Дедлайн должен быть после начала сбора, в будущем и до начала сессии");
        }
    }

    static PlanningException closed() {
        return new PlanningException(HttpStatus.CONFLICT, "COLLECTION_CLOSED", "Сбор закрыт; редактирование занятости недоступно");
    }
}
