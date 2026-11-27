package ru.vk.itmo.scheduler.slotwise.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TimeGridTests {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    // 15 June 2027, 00:00 in Moscow; 5-minute slots, two days.
    private static final Instant MIDNIGHT = ZonedDateTime.of(2027, 6, 15, 0, 0, 0, 0, MOSCOW).toInstant();
    private static final int SLOTS_PER_DAY = 24 * 12;

    private final TimeGrid grid = new TimeGrid(MIDNIGHT, MOSCOW, 5, 2 * SLOTS_PER_DAY, List.of(
            hours(1, 9, 19), hours(0, 9, 19)));

    @Test
    void daysFollowTheSessionTimeZone() {
        assertThat(grid.dayOf(0)).isZero();
        assertThat(grid.dayOf(SLOTS_PER_DAY - 1)).isZero();
        assertThat(grid.dayOf(SLOTS_PER_DAY)).isEqualTo(1);
        assertThat(grid.toInstant(SLOTS_PER_DAY)).isEqualTo(Instant.parse("2027-06-15T21:00:00Z"));
    }

    @Test
    void busyTimeIsExtendedToSlotBoundaries() {
        Instant start = at(0, 10, 12);
        Instant end = at(0, 10, 48);

        assertThat(grid.covering(start, end)).contains(new SlotRange(slot(0, 10, 10), slot(0, 10, 50)));
    }

    @Test
    void itmoClassesFitTheGridExactly() {
        assertThat(grid.covering(at(0, 8, 20), at(0, 9, 50))).contains(new SlotRange(slot(0, 8, 20), slot(0, 9, 50)));
    }

    @Test
    void coveringIsCutToTheGrid() {
        assertThat(grid.covering(MIDNIGHT.minus(Duration.ofHours(1)), MIDNIGHT.plus(Duration.ofMinutes(10))))
                .contains(new SlotRange(0, 2));
        assertThat(grid.covering(MIDNIGHT.minus(Duration.ofHours(2)), MIDNIGHT.minus(Duration.ofHours(1)))).isEmpty();
    }

    @Test
    void eventMustFitOneWorkingPeriod() {
        int lastStart = slot(0, 18, 30);

        assertThat(grid.fitsWorkingPeriod(lastStart, 6)).isTrue();
        assertThat(grid.fitsWorkingPeriod(lastStart, 7)).isFalse();
        assertThat(grid.fitsWorkingPeriod(slot(0, 8, 55), 2)).isFalse();
        assertThat(grid.fitsWorkingPeriod(slot(1, 9, 0), 6)).isTrue();
    }

    @Test
    void workingPeriodsAreSorted() {
        assertThat(grid.workingPeriods()).containsExactly(hours(0, 9, 19), hours(1, 9, 19));
    }

    @Test
    void durationsConvertToSlots() {
        assertThat(grid.toSlots(Duration.ofMinutes(20))).isEqualTo(4);
        assertThat(grid.toMinutes(9)).isEqualTo(45);
        assertThatIllegalArgumentException().isThrownBy(() -> grid.toSlots(Duration.ofMinutes(12)));
    }

    @Test
    void rejectsInvalidGrids() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeGrid(MIDNIGHT, MOSCOW, 7, 10, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeGrid(MIDNIGHT.plusSeconds(60), MOSCOW, 5, 10, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeGrid(MIDNIGHT, MOSCOW, 5, SLOTS_PER_DAY + 10,
                List.of(new SlotRange(SLOTS_PER_DAY - 6, SLOTS_PER_DAY + 6))));
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeGrid(MIDNIGHT, MOSCOW, 5, SLOTS_PER_DAY,
                List.of(hours(0, 9, 12), hours(0, 11, 14))));
    }

    private static SlotRange hours(int day, int from, int to) {
        return new SlotRange(slot(day, from, 0), slot(day, to, 0));
    }

    private static int slot(int day, int hour, int minute) {
        return day * SLOTS_PER_DAY + hour * 12 + minute / 5;
    }

    private static Instant at(int day, int hour, int minute) {
        return MIDNIGHT.plus(Duration.ofDays(day).plusHours(hour).plusMinutes(minute));
    }
}
