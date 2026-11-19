package ru.vk.itmo.scheduler.slotwise.solver.greedy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetGenerator;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetProfile;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GreedySolverTests {
    private final GreedySolver solver = new GreedySolver();
    private final SolveParams params = new SolveParams(Duration.ofSeconds(5), 42);

    @ParameterizedTest
    @EnumSource(value = DatasetProfile.class, names = {"S", "M"})
    void createsNonEmptyValidatedStartingSolution(DatasetProfile profile) {
        for (int seed : List.of(0, 42)) {
            var problem = DatasetGenerator.problem(profile, seed);
            var updates = new ArrayList<ProgressListener.Progress>();
            var result = solver.solve(problem, params, updates::add);
            var validation = ScheduleValidator.validate(problem, result.assignments());

            assertThat(result.status()).isEqualTo(SolveStatus.FEASIBLE);
            assertThat(result.assignments()).hasSizeGreaterThan(problem.events().size() / 2);
            assertThat(validation.violations()).isEmpty();
            assertThat(result.score()).contains(validation.score());
            assertThat(updates).isNotEmpty().last().extracting(ProgressListener.Progress::best)
                    .isEqualTo(validation.score());
        }
    }

    @Test
    void sameSeedProducesSameAssignmentsAndRetainsPins() {
        var problem = DatasetGenerator.problem(DatasetProfile.S, 42);
        var initial = solver.solve(problem, params, ProgressListener.NONE);
        Assignment pin = initial.assignments().getFirst();
        var pinned = problem.toBuilder().pin(pin).build();

        var first = solver.solve(pinned, params, ProgressListener.NONE);
        var second = solver.solve(pinned, params, ProgressListener.NONE);
        assertThat(first.assignments()).contains(pin).isEqualTo(second.assignments());
        assertThat(first.score()).isEqualTo(second.score());
        assertThat(ScheduleValidator.validate(pinned, first.assignments()).violations()).isEmpty();
    }

    @Test
    void impossiblePinReturnsNoSolution() {
        var problem = DatasetGenerator.problem(DatasetProfile.S, 42);
        var event = problem.events().getFirst();
        var room = problem.rooms().getFirst();
        var pinned = problem.toBuilder().pin(new Assignment(event.id(), 0, room.id(), List.of())).build();

        var result = solver.solve(pinned, params, ProgressListener.NONE);
        assertThat(result.status()).isEqualTo(SolveStatus.NO_SOLUTION);
        assertThat(result.assignments()).isEmpty();
    }
}
