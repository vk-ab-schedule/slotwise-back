package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.Interval;

/** Clips availability in the session zone; priorities apply only to the overlapping portion. */
@Component
@RequiredArgsConstructor
public class BusyIntervalNormalizer {
    private final PlanningProperties properties;

    public record Window(Instant start, Instant end) { }

    public List<Window> workingWindows(PlanningSessionEntity session, Set<Integer> weekdays) {
        ZoneId zone = ZoneId.of(session.getTimeZone());
        List<Window> result = new ArrayList<>();
        LocalDate last = session.getEndsAt().atZone(zone).toLocalDate();
        for (LocalDate date = session.getStartsAt().atZone(zone).toLocalDate();
             !date.isAfter(last); date = date.plusDays(1)) {
            if (!weekdays.contains(date.getDayOfWeek().getValue())) continue;
            Instant start = max(session.getStartsAt(), date.atTime(session.getWorkingDayStart()).atZone(zone).toInstant());
            Instant end = min(session.getEndsAt(), date.atTime(session.getWorkingDayEnd()).atZone(zone).toInstant());
            if (start.isBefore(end)) result.add(new Window(start, end));
        }
        return result;
    }

    public List<Interval> normalize(List<Interval> input, List<Window> windows) {
        if (windows.isEmpty()) return List.of();
        long slotSeconds = properties.getSlotMinutes() * 60L;
        TreeMap<Instant, int[]> changes = new TreeMap<>();
        for (Interval item : input) {
            // Clip first to keep even extreme client timestamps inside the session period.
            Instant start = max(item.startsAt(), windows.getFirst().start());
            Instant end = min(item.endsAt(), windows.getLast().end());
            if (!start.isBefore(end)) continue;
            start = Instant.ofEpochSecond(Math.floorDiv(start.getEpochSecond(), slotSeconds) * slotSeconds);
            long endSecond = Math.floorDiv(end.getEpochSecond(), slotSeconds) * slotSeconds;
            if (end.getEpochSecond() != endSecond || end.getNano() != 0) endSecond += slotSeconds;
            end = Instant.ofEpochSecond(endSecond);
            for (Window window : windows) {
                Instant clippedStart = max(start, window.start());
                Instant clippedEnd = min(end, window.end());
                if (!clippedStart.isBefore(clippedEnd)) continue;
                int priority = item.importance().ordinal();
                changes.computeIfAbsent(clippedStart, _ -> new int[BusyImportance.values().length])[priority]++;
                changes.computeIfAbsent(clippedEnd, _ -> new int[BusyImportance.values().length])[priority]--;
            }
        }
        List<Interval> result = new ArrayList<>();
        int[] active = new int[BusyImportance.values().length];
        Instant previous = null;
        for (var change : changes.entrySet()) {
            if (previous != null) {
                for (BusyImportance importance : BusyImportance.values()) {
                    if (active[importance.ordinal()] > 0) {
                        append(result, new Interval(previous, change.getKey(), importance));
                        break;
                    }
                }
            }
            for (int i = 0; i < active.length; i++) active[i] += change.getValue()[i];
            previous = change.getKey();
        }
        return List.copyOf(result);
    }

    public boolean noFreeTime(List<Interval> intervals, List<Window> windows) {
        List<Interval> critical = normalize(intervals.stream()
                .filter(i -> i.importance() == BusyImportance.CRITICAL).toList(), windows);
        Duration available = windows.stream().map(w -> Duration.between(w.start(), w.end()))
                .reduce(Duration.ZERO, Duration::plus);
        Duration blocked = critical.stream().map(i -> Duration.between(i.startsAt(), i.endsAt()))
                .reduce(Duration.ZERO, Duration::plus);
        return blocked.compareTo(available) >= 0;
    }

    private static void append(List<Interval> result, Interval interval) {
        if (!result.isEmpty()) {
            Interval last = result.getLast();
            if (last.endsAt().equals(interval.startsAt()) && last.importance() == interval.importance()) {
                result.set(result.size() - 1, new Interval(last.startsAt(), interval.endsAt(), last.importance()));
                return;
            }
        }
        result.add(interval);
    }

    private static Instant min(Instant a, Instant b) { return a.isBefore(b) ? a : b; }
    private static Instant max(Instant a, Instant b) { return a.isAfter(b) ? a : b; }
}
