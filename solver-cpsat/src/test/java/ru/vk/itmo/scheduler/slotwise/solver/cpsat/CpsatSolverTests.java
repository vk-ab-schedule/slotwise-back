package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

import org.junit.jupiter.api.Test;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.Score;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SchedulingSolver;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class CpsatSolverTests {
    private static final SolveParams PARAMS = new SolveParams(Duration.ofSeconds(5), 42);

    @Test
    void prefersSchedulingAnEventOverAvoidingItsSoftPenalty() {
        Person student = new Person(1, Person.Role.STUDENT, List.of(
                new Person.Busy(new SlotRange(0, 6), Person.Busy.Kind.CRITICAL)));
        Problem problem = problem(new SlotRange(0, 6))
                .person(student)
                .event(event(1, Event.Scope.GROUP, 6, List.of(1L)))
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.status()).isEqualTo(SolveStatus.OPTIMAL);
        assertThat(result.assignments()).containsExactly(new Assignment(1, 0, 1, List.of()));
        assertThat(result.score()).contains(new Score(0, 300));
        assertThat(result.penaltyBound()).hasValue(300);
    }

    @Test
    void leavesConflictingEventsUnassignedAndKeepsPinnedAssignments() {
        Assignment pin = new Assignment(2, 0, 1, List.of());
        Problem problem = problem(new SlotRange(0, 6))
                .person(new Person(1, Person.Role.STUDENT, List.of()))
                .event(event(1, Event.Scope.INDIVIDUAL, 6, List.of(1L)))
                .event(event(2, Event.Scope.INDIVIDUAL, 6, List.of(1L)))
                .pin(pin)
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.assignments()).containsExactly(pin);
        assertThat(result.score()).contains(new Score(1, 0));
    }

    @Test
    void computesAllFourPenaltiesForTheSameSchedule() {
        Assignment first = new Assignment(1, 0, 1, List.of());
        Assignment online = new Assignment(2, 3, 3, List.of());
        Assignment last = new Assignment(3, 7, 2, List.of(1L), OptionalLong.of(1));
        Problem problem = problem(new SlotRange(0, 10))
                .room(new Room(2, 4, List.of(99L), List.of()))
                .room(new Room(3, null, List.of(), List.of(), true))
                .person(new Person(1, Person.Role.TEACHER, List.of()))
                .person(new Person(2, Person.Role.TEACHER, List.of()))
                .person(new Person(3, Person.Role.STUDENT, List.of(
                        new Person.Busy(new SlotRange(7, 8), Person.Busy.Kind.LOW),
                        new Person.Busy(new SlotRange(7, 9), Person.Busy.Kind.MEDIUM))))
                .commissionCandidate(new CommissionCandidate(1, true))
                .commissionCandidate(new CommissionCandidate(2, false))
                .event(event(1, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .event(new Event(2, 2, Event.Scope.INDIVIDUAL, Event.Format.ONLINE, List.of(1L),
                        Event.CommissionRequirement.NONE, List.of(), List.of(), 0))
                .event(new Event(3, 2, Event.Scope.INDIVIDUAL, Event.Format.ONSITE, List.of(3L),
                        new Event.CommissionRequirement(1, true), List.of(), List.of(99L), 0))
                .pin(first).pin(online).pin(last)
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.status()).isEqualTo(SolveStatus.OPTIMAL);
        assertThat(result.assignments()).containsExactly(first, online, last);
        // 15 idle minutes + 10 commission minutes of difference + 30 busy penalty + 15 for changing rooms.
        assertThat(result.score()).contains(new Score(0, 70));
        assertThat(result.penaltyBound()).hasValue(70);
        var validation = ScheduleValidator.validate(problem, result.assignments());
        assertThat(validation.violations()).isEmpty();
        assertThat(result.score()).contains(validation.score());
    }

    @Test
    void choosesTheLowerPenaltyPlacementAndReportsProgressOnTheCallingThread() {
        Person student = new Person(1, Person.Role.STUDENT, List.of(
                new Person.Busy(new SlotRange(0, 3), Person.Busy.Kind.MEDIUM),
                new Person.Busy(new SlotRange(3, 6), Person.Busy.Kind.LOW)));
        Problem problem = problem(new SlotRange(0, 6))
                .person(student)
                .event(event(1, Event.Scope.INDIVIDUAL, 3, List.of(1L)))
                .build();
        Thread caller = Thread.currentThread();
        List<ProgressListener.Progress> progress = new ArrayList<>();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, update -> {
            assertThat(Thread.currentThread()).isSameAs(caller);
            progress.add(update);
        });

        assertThat(result.assignments()).containsExactly(new Assignment(1, 3, 1, List.of()));
        assertThat(result.score()).contains(new Score(0, 15));
        assertThat(progress).isNotEmpty().allMatch(update -> update.best().compareTo(new Score(0, 15)) >= 0);
    }

    @Test
    void rejectsPinnedRoomChangesWithoutEnoughTravelTime() {
        Problem problem = problem(new SlotRange(0, 6))
                .room(new Room(2, 30, List.of(), List.of()))
                .roomChangeBuffer(2)
                .person(new Person(1, Person.Role.TEACHER, List.of()))
                .event(event(1, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .event(event(2, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .pin(new Assignment(1, 0, 1, List.of()))
                .pin(new Assignment(2, 3, 2, List.of()))
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.status()).isEqualTo(SolveStatus.NO_SOLUTION);
        assertThat(result.score()).isEmpty();
    }

    @Test
    void schedulesTheSuccessorEvenIfItsPredecessorMissesTheMaximumDateGap() {
        Problem problem = problem(new SlotRange(0, 2), new SlotRange(576, 578))
                .person(new Person(1, Person.Role.TEACHER, List.of()))
                .event(event(1, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .event(event(2, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .dependency(Dependency.between(1, 2, 1, 1))
                .pin(new Assignment(2, 576, 1, List.of()))
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.assignments()).containsExactly(new Assignment(2, 576, 1, List.of()));
        assertThat(result.score()).contains(new Score(1, 0));
    }

    @Test
    void returnsAFeasibleIncumbentWhenTheCallingThreadIsInterrupted() {
        Problem problem = problem(new SlotRange(0, 6))
                .person(new Person(1, Person.Role.STUDENT, List.of()))
                .event(event(1, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .build();
        Thread.currentThread().interrupt();
        try {
            SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

            assertThat(result.status()).isEqualTo(SolveStatus.FEASIBLE);
            assertThat(result.score()).contains(new Score(1, 0));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void choosesAChairAndAFreeRoomWithTheRequiredEquipment() {
        Problem problem = problem(new SlotRange(0, 6))
                .room(new Room(2, 5, List.of(99L), List.of(new SlotRange(2, 4))))
                .person(new Person(1, Person.Role.STUDENT,
                        List.of(new Person.Busy(new SlotRange(0, 2), Person.Busy.Kind.CRITICAL))))
                .person(new Person(2, Person.Role.TEACHER, List.of()))
                .person(new Person(3, Person.Role.TEACHER,
                        List.of(new Person.Busy(new SlotRange(0, 4), Person.Busy.Kind.TIMETABLE))))
                .commissionCandidate(new CommissionCandidate(2, false))
                .commissionCandidate(new CommissionCandidate(3, true))
                .event(new Event(1, 2, Event.Scope.INDIVIDUAL, Event.Format.HYBRID, List.of(1L),
                        new Event.CommissionRequirement(1, true), List.of(), List.of(99L), 0))
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.assignments()).containsExactly(
                new Assignment(1, 4, 2, List.of(3L), OptionalLong.of(3)));
    }

    @Test
    void keepsTheValidatedGreedyIncumbentIfInterruptedBeforeCoverageSearch() {
        Problem problem = problem(new SlotRange(0, 6))
                .person(new Person(1, Person.Role.STUDENT, List.of()))
                .person(new Person(2, Person.Role.TEACHER, List.of()))
                .commissionCandidate(new CommissionCandidate(2, true))
                .event(new Event(1, 2, Event.Scope.INDIVIDUAL, Event.Format.ONSITE, List.of(1L),
                        new Event.CommissionRequirement(1, false), List.of(), List.of(), 0))
                .build();
        Assignment assignment = new Assignment(1, 4, 1, List.of(2L), OptionalLong.of(2));
        SchedulingSolver greedy = new SchedulingSolver() {
            @Override
            public String id() {
                return "greedy";
            }

            @Override
            public SolveResult solve(Problem input, SolveParams params, ProgressListener listener) {
                assertThat(input).isSameAs(problem);
                assertThat(params.seed()).isEqualTo(PARAMS.seed());
                return new SolveResult(SolveStatus.FEASIBLE, List.of(assignment),
                        Optional.of(new Score(0, 999)), OptionalLong.empty(), Duration.ZERO);
            }
        };
        try {
            SolveResult result = new CpsatSolver(greedy).solve(problem, PARAMS,
                    ignored -> Thread.currentThread().interrupt());

            assertThat(result.status()).isEqualTo(SolveStatus.FEASIBLE);
            assertThat(result.assignments()).containsExactly(assignment);
            assertThat(result.score()).contains(new Score(0, 0));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void leavesBaselineMovePenaltyForTheLaterReplanningBlock() {
        Problem problem = problem(new SlotRange(0, 4))
                .person(new Person(1, Person.Role.STUDENT, List.of()))
                .event(event(1, Event.Scope.INDIVIDUAL, 2, List.of(1L)))
                .pin(new Assignment(1, 2, 1, List.of()))
                .baselineAssignment(new Assignment(1, 0, 1, List.of()))
                .build();

        SolveResult result = new CpsatSolver().solve(problem, PARAMS, ProgressListener.NONE);

        assertThat(result.assignments()).containsExactlyElementsOf(problem.pinned());
        assertThat(result.score()).contains(new Score(0, 0));
        assertThat(result.penaltyBound()).hasValue(0);
        assertThat(ScheduleValidator.validate(problem, result.assignments()).score())
                .isEqualTo(new Score(0, Weights.DEFAULT.movedEvents()));
    }

    private static Problem.ProblemBuilder problem(SlotRange... workingPeriods) {
        int horizon = List.of(workingPeriods).stream().mapToInt(SlotRange::end).max().orElseThrow();
        return Problem.builder()
                .grid(new TimeGrid(Instant.EPOCH, ZoneOffset.UTC, 5, horizon, List.of(workingPeriods)))
                .room(new Room(1, 30, List.of(), List.of()))
                .weights(Weights.DEFAULT);
    }

    private static Event event(long id, Event.Scope scope, int duration, List<Long> participants) {
        return new Event(id, duration, scope, Event.Format.ONSITE, participants,
                Event.CommissionRequirement.NONE, List.of(), List.of(), 0);
    }
}
