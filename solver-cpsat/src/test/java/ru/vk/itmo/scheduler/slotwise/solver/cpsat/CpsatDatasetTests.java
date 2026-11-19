package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CpsatDatasetTests {
    private static final long SEED = 42;

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
