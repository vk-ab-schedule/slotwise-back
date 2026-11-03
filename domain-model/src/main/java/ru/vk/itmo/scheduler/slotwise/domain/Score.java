package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.Comparator;

/**
 * Quality of a schedule (MODEL.md, «Целевая функция»): fewer unassigned events is always better,
 * the penalty decides only between schedules with the same number of unassigned events.
 */
public record Score(int unassigned, long penalty) implements Comparable<Score> {
    private static final Comparator<Score> ORDER =
            Comparator.comparingInt(Score::unassigned).thenComparingLong(Score::penalty);

    public Score {
        if (unassigned < 0 || penalty < 0) {
            throw new IllegalArgumentException("Score must not be negative: " + unassigned + ", " + penalty);
        }
    }

    public boolean isBetterThan(Score other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(Score other) {
        return ORDER.compare(this, other);
    }
}
