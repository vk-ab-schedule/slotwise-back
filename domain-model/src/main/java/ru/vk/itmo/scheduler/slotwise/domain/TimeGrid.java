package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Time of a planning session as a sequence of equal slots (MODEL.md, «Время»).
 *
 * <p>Slot 0 starts at {@code origin}. Days are calendar dates in the session time zone, counted
 * from the date of the origin. Events may be placed only inside working periods; each period
 * lies within one day (for example, 9:00–19:00 of a working day).
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class TimeGrid {
    private final Instant origin;
    private final ZoneId zone;
    private final int slotMinutes;
    private final int slotCount;
    /** Sorted, non-overlapping; each period within one day. */
    private final List<SlotRange> workingPeriods;
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final int[] dayOfSlot;

    public TimeGrid(@NonNull Instant origin, @NonNull ZoneId zone, int slotMinutes, int slotCount, List<SlotRange> workingPeriods) {
        this.origin = origin;
        this.zone = zone;
        if (slotMinutes <= 0 || 60 % slotMinutes != 0) {
            throw new IllegalArgumentException("Slot length must divide an hour: " + slotMinutes);
        }
        ZonedDateTime local = origin.atZone(zone);
        if (local.getMinute() % slotMinutes != 0 || local.getSecond() != 0 || local.getNano() != 0) {
            throw new IllegalArgumentException("Origin " + local + " is not on a slot boundary");
        }
        if (slotCount <= 0) {
            throw new IllegalArgumentException("Grid must have slots: " + slotCount);
        }
        this.slotMinutes = slotMinutes;
        this.slotCount = slotCount;

        this.dayOfSlot = new int[slotCount];
        LocalDate firstDay = local.toLocalDate();
        for (int slot = 0; slot < slotCount; slot++) {
            dayOfSlot[slot] = (int) ChronoUnit.DAYS.between(firstDay, toInstant(slot).atZone(zone).toLocalDate());
        }

        this.workingPeriods = workingPeriods.stream().sorted().toList();
        SlotRange previous = null;
        for (SlotRange period : this.workingPeriods) {
            if (period.end() > slotCount) {
                throw new IllegalArgumentException("Working period " + period + " is outside the grid of " + slotCount + " slots");
            }
            if (previous != null && previous.overlaps(period)) {
                throw new IllegalArgumentException("Working periods overlap: " + previous + " and " + period);
            }
            if (dayOf(period.start()) != dayOf(period.end() - 1)) {
                throw new IllegalArgumentException("Working period " + period + " spans more than one day");
            }
            previous = period;
        }
    }

    public Instant toInstant(int slot) {
        return origin.plus((long) slot * slotMinutes, ChronoUnit.MINUTES);
    }

    /**
     * Day of the slot: 0 for the date of the origin in the session time zone, then 1, 2…
     */
    public int dayOf(int slot) {
        return dayOfSlot[slot];
    }

    public int toSlots(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes <= 0 || minutes % slotMinutes != 0 || !duration.equals(Duration.ofMinutes(minutes))) {
            throw new IllegalArgumentException(duration + " is not a positive multiple of " + slotMinutes + " minutes");
        }
        return Math.toIntExact(minutes / slotMinutes);
    }

    public int toMinutes(int slots) {
        return slots * slotMinutes;
    }

    /**
     * Slots touched by {@code [start, end)}, extended to slot boundaries and cut to the grid:
     * busy time 10:12–10:48 covers slots 10:10–10:50. Empty if the interval is outside the grid.
     */
    public Optional<SlotRange> covering(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Interval must not be empty: " + start + " – " + end);
        }
        long slotSeconds = slotMinutes * 60L;
        long first = Math.floorDiv(Duration.between(origin, start).toSeconds(), slotSeconds);
        long last = -Math.floorDiv(-Duration.between(origin, end).toSeconds(), slotSeconds);
        first = Math.max(first, 0);
        last = Math.min(last, slotCount);
        return first < last ? Optional.of(new SlotRange((int) first, (int) last)) : Optional.empty();
    }

    /**
     * Whether an event of {@code length} slots starting at {@code start} lies within one working period (H8).
     */
    public boolean fitsWorkingPeriod(int start, int length) {
        SlotRange event = SlotRange.ofLength(start, length);
        for (SlotRange period : workingPeriods) {
            if (period.contains(event)) {
                return true;
            }
            if (period.start() > start) {
                return false;
            }
        }
        return false;
    }

}
