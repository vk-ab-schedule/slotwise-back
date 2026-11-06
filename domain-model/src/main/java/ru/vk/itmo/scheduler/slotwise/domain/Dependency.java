package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.NonNull;

import java.util.OptionalInt;

/**
 * Event {@code before} takes place before event {@code after}, their dates differ by at least
 * {@code minGapDays} and, if set, by at most {@code maxGapDays} (H7).
 */
public record Dependency(long before, long after, int minGapDays, @NonNull OptionalInt maxGapDays) {

    public Dependency {
        if (before == after) {
            throw new IllegalArgumentException("Event " + before + " cannot depend on itself");
        }
        if (minGapDays < 0) {
            throw new IllegalArgumentException("Minimum gap must not be negative: " + minGapDays);
        }
        if (maxGapDays.isPresent() && maxGapDays.getAsInt() < minGapDays) {
            throw new IllegalArgumentException("Maximum gap " + maxGapDays.getAsInt() + " is less than minimum " + minGapDays);
        }
    }

    public static Dependency atLeast(long before, long after, int minGapDays) {
        return new Dependency(before, after, minGapDays, OptionalInt.empty());
    }

    public static Dependency between(long before, long after, int minGapDays, int maxGapDays) {
        return new Dependency(before, after, minGapDays, OptionalInt.of(maxGapDays));
    }
}
