package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Half-open range of slots {@code [start, end)}.
 */
public record SlotRange(int start, int end) implements Comparable<SlotRange> {

    public SlotRange {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("Slot range must satisfy 0 <= start < end: [" + start + ", " + end + ")");
        }
    }

    public static SlotRange ofLength(int start, int length) {
        return new SlotRange(start, start + length);
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(SlotRange other) {
        return start < other.end && other.start < end;
    }

    public boolean contains(SlotRange other) {
        return start <= other.start && other.end <= end;
    }

    @Override
    public int compareTo(SlotRange other) {
        return start != other.start ? Integer.compare(start, other.start) : Integer.compare(end, other.end);
    }
}
