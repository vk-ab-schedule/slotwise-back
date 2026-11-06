package ru.vk.itmo.scheduler.slotwise.planning;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.Interval;

class BusyIntervalNormalizerTests {
    private final BusyIntervalNormalizer normalizer = new BusyIntervalNormalizer(new PlanningProperties());

    @Test
    void roundsOutwardsAndUsesHighestPriorityOnlyOnOverlap() {
        var windows = List.of(window("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"));
        var actual = normalizer.normalize(List.of(
                interval("2027-01-15T10:12:00Z", "2027-01-15T10:48:00Z", BusyImportance.LOW),
                interval("2027-01-15T10:40:00Z", "2027-01-15T11:02:00Z", BusyImportance.CRITICAL)), windows);
        assertThat(actual).containsExactly(
                interval("2027-01-15T10:10:00Z", "2027-01-15T10:40:00Z", BusyImportance.LOW),
                interval("2027-01-15T10:40:00Z", "2027-01-15T11:05:00Z", BusyImportance.CRITICAL));
        assertThat(normalizer.normalize(actual, windows)).isEqualTo(actual);
    }

    @Test
    void nestedIntervalsRestorePreviousPriorityAndAdjacentEqualIntervalsMerge() {
        var windows = List.of(window("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"));
        var actual = normalizer.normalize(List.of(
                interval("2027-01-15T10:00:00Z", "2027-01-15T12:00:00Z", BusyImportance.LOW),
                interval("2027-01-15T10:30:00Z", "2027-01-15T11:00:00Z", BusyImportance.CRITICAL),
                interval("2027-01-15T12:00:00Z", "2027-01-15T13:00:00Z", BusyImportance.LOW)), windows);
        assertThat(actual).containsExactly(
                interval("2027-01-15T10:00:00Z", "2027-01-15T10:30:00Z", BusyImportance.LOW),
                interval("2027-01-15T10:30:00Z", "2027-01-15T11:00:00Z", BusyImportance.CRITICAL),
                interval("2027-01-15T11:00:00Z", "2027-01-15T13:00:00Z", BusyImportance.LOW));
    }

    @Test
    void clipsToPeriodWorkingHoursAndWeekdaysInSessionZone() {
        var session = session("Europe/Moscow", "2027-01-15T07:00:00Z", "2027-01-18T15:00:00Z");
        var windows = normalizer.workingWindows(session, Set.of(1, 2, 3, 4, 5));
        assertThat(windows).containsExactly(
                window("2027-01-15T07:00:00Z", "2027-01-15T16:00:00Z"),
                window("2027-01-18T06:00:00Z", "2027-01-18T15:00:00Z"));
        var actual = normalizer.normalize(List.of(new Interval(Instant.MIN, Instant.MAX, BusyImportance.MEDIUM)), windows);
        assertThat(actual).hasSize(2);
        assertThat(actual.getFirst().startsAt()).isEqualTo(windows.getFirst().start());
        assertThat(actual.getLast().endsAt()).isEqualTo(windows.getLast().end());
    }

    @Test
    void daylightSavingUsesLocalWorkingHours() {
        var session = session("Europe/Berlin", "2027-03-27T00:00:00Z", "2027-03-29T00:00:00Z");
        assertThat(normalizer.workingWindows(session, Set.of(6, 7))).containsExactly(
                window("2027-03-27T08:00:00Z", "2027-03-27T18:00:00Z"),
                window("2027-03-28T07:00:00Z", "2027-03-28T17:00:00Z"));
    }

    @Test
    void fractionsRoundUpButExactBoundariesDoNot() {
        var windows = List.of(window("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"));
        assertThat(normalizer.normalize(List.of(
                interval("2027-01-15T10:00:00Z", "2027-01-15T10:05:00.000000001Z", BusyImportance.LOW)), windows))
                .containsExactly(interval("2027-01-15T10:00:00Z", "2027-01-15T10:10:00Z", BusyImportance.LOW));
        assertThat(normalizer.normalize(List.of(
                interval("2027-01-15T10:00:00Z", "2027-01-15T10:05:00Z", BusyImportance.LOW)), windows))
                .containsExactly(interval("2027-01-15T10:00:00Z", "2027-01-15T10:05:00Z", BusyImportance.LOW));
    }

    @Test
    void outsideIntervalsAndEmptyWorkweekProduceEmptyCalendar() {
        var windows = List.of(window("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"));
        var input = List.of(interval("2027-01-14T10:00:00Z", "2027-01-14T11:00:00Z", BusyImportance.LOW));
        assertThat(normalizer.normalize(input, windows)).isEmpty();
        assertThat(normalizer.normalize(input, List.of())).isEmpty();
        assertThat(normalizer.workingWindows(session("UTC", "2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"), Set.of())).isEmpty();
    }

    @Test
    void noFreeTimeCountsOnlyCriticalCoverageWithoutDoubleCounting() {
        var windows = List.of(window("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z"));
        assertThat(normalizer.noFreeTime(List.of(
                interval("2027-01-15T09:00:00Z", "2027-01-15T15:00:00Z", BusyImportance.CRITICAL),
                interval("2027-01-15T12:00:00Z", "2027-01-15T19:00:00Z", BusyImportance.CRITICAL)), windows)).isTrue();
        assertThat(normalizer.noFreeTime(List.of(
                interval("2027-01-15T09:00:00Z", "2027-01-15T19:00:00Z", BusyImportance.MEDIUM)), windows)).isFalse();
        assertThat(normalizer.noFreeTime(List.of(
                interval("2027-01-15T09:00:00Z", "2027-01-15T15:00:00Z", BusyImportance.CRITICAL),
                interval("2027-01-15T09:00:00Z", "2027-01-15T15:00:00Z", BusyImportance.CRITICAL)), windows)).isFalse();
    }

    private static PlanningSessionEntity session(String zone, String start, String end) {
        var session = new PlanningSessionEntity();
        session.setStartsAt(Instant.parse(start));
        session.setEndsAt(Instant.parse(end));
        session.setTimeZone(zone);
        session.setWorkingDayStart(LocalTime.of(9, 0));
        session.setWorkingDayEnd(LocalTime.of(19, 0));
        return session;
    }

    private static BusyIntervalNormalizer.Window window(String start, String end) {
        return new BusyIntervalNormalizer.Window(Instant.parse(start), Instant.parse(end));
    }

    private static Interval interval(String start, String end, BusyImportance importance) {
        return new Interval(Instant.parse(start), Instant.parse(end), importance);
    }
}
