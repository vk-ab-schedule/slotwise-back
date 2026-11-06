package ru.vk.itmo.scheduler.slotwise.domain;

import org.junit.jupiter.api.Test;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScoreTests {

    @Test
    void fewerUnassignedEventsWinWhateverThePenalty() {
        assertThat(new Score(0, 1_000_000).isBetterThan(new Score(1, 0))).isTrue();
        assertThat(new Score(2, 10).isBetterThan(new Score(2, 20))).isTrue();
        assertThat(new Score(2, 10).isBetterThan(new Score(2, 10))).isFalse();
    }

    @Test
    void resultIsSortedByEvent() {
        SolveResult result = new SolveResult(SolveStatus.FEASIBLE,
                List.of(new Assignment(2, 0, Venue.ONLINE, List.of()), new Assignment(1, 0, Venue.ONLINE, List.of())),
                Optional.of(new Score(0, 5)), OptionalLong.empty(), Duration.ofSeconds(1));

        assertThat(result.assignments()).extracting(Assignment::eventId).containsExactly(1L, 2L);
    }

    @Test
    void resultRejectsEventAssignedTwice() {
        Assignment assignment = new Assignment(1, 0, Venue.ONLINE, List.of());

        assertThatIllegalArgumentException().isThrownBy(() -> new SolveResult(SolveStatus.FEASIBLE,
                List.of(assignment, assignment), Optional.of(new Score(0, 0)), OptionalLong.empty(), Duration.ZERO));
    }

    @Test
    void scoreIsRequiredUnlessThereIsNoSolution() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SolveResult(SolveStatus.FEASIBLE,
                List.of(), Optional.empty(), OptionalLong.empty(), Duration.ZERO));
        assertThat(SolveResult.noSolution(Duration.ofSeconds(60)).score()).isEmpty();
    }
}
