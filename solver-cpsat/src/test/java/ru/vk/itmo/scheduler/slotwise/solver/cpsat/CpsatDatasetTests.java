package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetGenerator;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetProfile;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.domain.validation.Validation;
import ru.vk.itmo.scheduler.slotwise.solver.greedy.GreedySolver;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CpsatDatasetTests {
    private static final long SEED = 42;

    @Test
    void repeatsTheSameSearchResultWhenProgressDeliveryTakesLonger() {
        Problem problem = DatasetGenerator.problem(DatasetProfile.S, SEED);
        SolveParams params = new SolveParams(Duration.ofSeconds(3), SEED);
        CpsatSolver solver = new CpsatSolver();

        SolveResult first = solver.solve(problem, params, ProgressListener.NONE);
        var delayed = new AtomicBoolean();
        SolveResult second = solver.solve(problem, params, progress -> {
            if (delayed.compareAndSet(false, true)) {
                LockSupport.parkNanos(Duration.ofMillis(750).toNanos());
            }
        });

        assertThat(delayed).isTrue();
        assertThat(first.assignments()).isNotEmpty();
        assertThat(second.assignments()).isEqualTo(first.assignments());
        assertThat(second.score()).isEqualTo(first.score());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.penaltyBound()).isEqualTo(first.penaltyBound());
    }

    @Test
    void warmStartedCpsatIsAtLeastAsGoodAsGreedyOnSmallDataset() {
        Problem problem = DatasetGenerator.problem(DatasetProfile.S, SEED);
        SolveParams params = new SolveParams(Duration.ofSeconds(5), SEED);
        GreedySolver greedy = new GreedySolver();
        SolveResult baseline = greedy.solve(problem, params, ProgressListener.NONE);
        SolveResult improved = new CpsatSolver(greedy).solve(problem, params, ProgressListener.NONE);
        Validation baselineValidation = ScheduleValidator.validate(problem, baseline.assignments());
        Validation improvedValidation = ScheduleValidator.validate(problem, improved.assignments());

        assertThat(baseline.assignments()).isNotEmpty();
        assertThat(baselineValidation.violations()).isEmpty();
        assertThat(improvedValidation.violations()).isEmpty();
        assertThat(improved.score()).contains(improvedValidation.score());
        assertThat(improvedValidation.score()).isLessThanOrEqualTo(baselineValidation.score());
    }

    @ParameterizedTest(name = "{0} with a {1}-second limit")
    @CsvSource({"S, 5", "M, 60"})
    void returnsAValidScheduleForTheSeededDataset(DatasetProfile profile, int timeLimitSeconds) {
        Problem problem = DatasetGenerator.problem(profile, SEED);
        SolveParams params = new SolveParams(Duration.ofSeconds(timeLimitSeconds), SEED);

        SolveResult result = new CpsatSolver().solve(problem, params, ProgressListener.NONE);
        Validation validation = ScheduleValidator.validate(problem, result.assignments());

        assertThat(result.assignments()).isNotEmpty();
        assertThat(validation.violations()).isEmpty();
        assertThat(result.score()).contains(validation.score());
    }
}
