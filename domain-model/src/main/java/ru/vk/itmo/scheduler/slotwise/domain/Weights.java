package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Weights of the soft constraints (MODEL.md, «Мягкие ограничения»). The penalty is
 * {@code P = teacherGaps·S1 + commissionLoadSpread·S2 + undesirableTime·S3 + teacherRoomChanges·S4 + movedEvents·S5}.
 * The names are the same as in the API.
 *
 * <p>S1, S2 and S3 are in minutes, S4 and S5 are counts, so the default weights
 * value one room switch as 15 minutes of gaps and one moved event as 30 minutes.
 */
public record Weights(int teacherGaps, int commissionLoadSpread, int undesirableTime, int teacherRoomChanges,
                      int movedEvents) {
    public static final Weights DEFAULT = new Weights(1, 1, 1, 15, 30);

    public Weights {
        if (teacherGaps < 0 || commissionLoadSpread < 0 || undesirableTime < 0 || teacherRoomChanges < 0 || movedEvents < 0) {
            throw new IllegalArgumentException("Weights must not be negative: " + this);
        }
    }
}
