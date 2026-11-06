package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Weights of the soft constraints (MODEL.md, «Мягкие ограничения»). The penalty is
 * {@code P = gaps·Gaps + unfairness·Unfair + undesirable·Pref + roomSwitches·Switches + moves·Moves}.
 *
 * <p>Gaps, Unfair and Pref are in minutes, Switches and Moves are counts, so the default weights
 * value one room switch as 15 minutes of gaps and one moved event as 30 minutes.
 */
public record Weights(int gaps, int unfairness, int undesirable, int roomSwitches, int moves) {
    public static final Weights DEFAULT = new Weights(1, 1, 1, 15, 30);

    public Weights {
        if (gaps < 0 || unfairness < 0 || undesirable < 0 || roomSwitches < 0 || moves < 0) {
            throw new IllegalArgumentException("Weights must not be negative: " + this);
        }
    }
}
