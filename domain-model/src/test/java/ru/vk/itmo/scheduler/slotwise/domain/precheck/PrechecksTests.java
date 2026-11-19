package ru.vk.itmo.scheduler.slotwise.domain.precheck;

import org.junit.jupiter.api.Test;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PrechecksTests {
    private static final int DAY = 288;
    private static final long T1 = 1, T2 = 2, T3 = 3, S1 = 11, S2 = 12;
    private static final long PROJECTOR = 1;
    private static final long EXAM = 101, DEFENSE = 102;
    private static final SlotRange DAY_0 = new SlotRange(9 * 12, 19 * 12);
    private static final SlotRange DAY_1 = new SlotRange(DAY + 9 * 12, DAY + 19 * 12);
    /** Two days, 9:00–19:00. */
    private static final TimeGrid GRID = new TimeGrid(Instant.parse("2027-06-14T21:00:00Z"), ZoneId.of("Europe/Moscow"),
            5, 2 * DAY, List.of(DAY_0, DAY_1));

    private static final Event EXAM_EVENT = new Event(EXAM, 6, Event.Scope.GROUP, Event.Format.ONSITE,
            List.of(S1, S2, T1), Event.CommissionRequirement.NONE, List.of(), List.of(), 2);
    private static final Event DEFENSE_EVENT = new Event(DEFENSE, 6, Event.Scope.INDIVIDUAL, Event.Format.ONSITE,
            List.of(S1, T1), new Event.CommissionRequirement(2, true), List.of(T1), List.of(PROJECTOR), 0);

    @Test
    void consistentInputHasNoIssues() {
        assertThat(Prechecks.run(problem().build())).isEmpty();
    }

    @Test
    void participantsWithoutCommonWindow() {
        Problem problem = problem(person(S1, Person.Role.STUDENT, Person.Busy.Kind.TIMETABLE, DAY_0),
                person(S2, Person.Role.STUDENT, Person.Busy.Kind.TIMETABLE, DAY_1)).build();

        assertThat(Prechecks.run(problem)).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo(PrecheckIssue.Code.NO_COMMON_WINDOW);
            assertThat(issue.eventIds()).containsExactly(EXAM);
        });
    }

    @Test
    void studentCriticalTimeBlocksOnlyIndividualEvents() {
        Problem problem = problem(person(S1, Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, DAY_0, DAY_1)).build();

        assertThat(Prechecks.run(problem)).extracting(PrecheckIssue::code, PrecheckIssue::eventIds).containsExactlyInAnyOrder(
                tuple(PrecheckIssue.Code.NO_COMMON_WINDOW, List.of(DEFENSE)),
                tuple(PrecheckIssue.Code.NO_FREE_TIME, List.of()));
    }

    @Test
    void noRoomWithEquipmentOrNoOnlinePlatform() {
        Problem withoutProjector = problem().clearRooms().room(new Room(1, 30, List.of(), List.of()))
                .room(new Room(9, null, List.of(), List.of(), true)).build();
        Problem withoutOnline = problem().clearRooms().room(new Room(1, 30, List.of(PROJECTOR), List.of()))
                .event(new Event(103, 6, Event.Scope.GROUP, Event.Format.ONLINE, List.of(S1, S2, T2),
                        Event.CommissionRequirement.NONE, List.of(), List.of(), 0)).build();

        assertThat(Prechecks.run(withoutProjector)).extracting(PrecheckIssue::code, PrecheckIssue::eventIds)
                .containsExactly(tuple(PrecheckIssue.Code.NO_ROOM, List.of(DEFENSE)));
        assertThat(Prechecks.run(withoutOnline)).extracting(PrecheckIssue::code, PrecheckIssue::eventIds)
                .containsExactly(tuple(PrecheckIssue.Code.NO_ROOM, List.of(103L)));
    }

    @Test
    void poolTooSmallOrWithoutChair() {
        Problem small = problem().clearCommissionPool().commissionCandidate(new CommissionCandidate(T1, true))
                .commissionCandidate(new CommissionCandidate(T2, true)).build();
        Problem noChair = problem().clearCommissionPool().commissionCandidate(new CommissionCandidate(T2, false))
                .commissionCandidate(new CommissionCandidate(T3, false)).build();

        // T1 is the supervisor: excluded, so one candidate is left.
        assertThat(Prechecks.run(small)).extracting(PrecheckIssue::code).containsExactly(PrecheckIssue.Code.COMMISSION_POOL_TOO_SMALL);
        assertThat(Prechecks.run(noChair)).extracting(PrecheckIssue::code).containsExactly(PrecheckIssue.Code.COMMISSION_POOL_TOO_SMALL);
    }

    @Test
    void teacherWithMoreEventsThanFreeTime() {
        // 45 free minutes: each 30-minute event fits, both together do not.
        Problem problem = problem(person(T1, Person.Role.TEACHER, Person.Busy.Kind.TIMETABLE,
                new SlotRange(DAY_0.start(), DAY_0.end() - 9), DAY_1)).build();

        assertThat(Prechecks.run(problem)).extracting(PrecheckIssue::code, PrecheckIssue::personIds)
                .containsExactly(tuple(PrecheckIssue.Code.TEACHER_OVERLOADED, List.of(T1)));
    }

    @Test
    void preparationDaysDoNotFitTheSession() {
        Problem problem = problem().event(new Event(103, 6, Event.Scope.GROUP, Event.Format.ONSITE, List.of(S1, S2, T2),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 2)).build();

        // One issue for the whole group.
        assertThat(Prechecks.run(problem)).extracting(PrecheckIssue::code, PrecheckIssue::personIds, PrecheckIssue::eventIds)
                .containsExactly(tuple(PrecheckIssue.Code.PREPARATION_DOES_NOT_FIT, List.of(S1, S2), List.of(EXAM, 103L)));
    }

    @Test
    void sessionWithoutWorkingTimeIsReportedNotThrown() {
        TimeGrid noWorkingTime = new TimeGrid(GRID.origin(), GRID.zone(), 5, 2 * DAY, List.of());

        assertThat(Prechecks.run(problem().grid(noWorkingTime).build()))
                .extracting(PrecheckIssue::code).containsExactly(PrecheckIssue.Code.NO_WORKING_TIME);
        assertThat(Prechecks.run(problem().grid(noWorkingTime).clearEvents().build()))
                .extracting(PrecheckIssue::code).containsExactly(PrecheckIssue.Code.NO_WORKING_TIME);
    }

    @Test
    void eventLongerThanWorkingDay() {
        Problem problem = problem().event(new Event(103, 11 * 12, Event.Scope.GROUP, Event.Format.ONSITE, List.of(S1, T2),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 0)).build();

        assertThat(Prechecks.run(problem)).extracting(PrecheckIssue::code, PrecheckIssue::eventIds)
                .contains(tuple(PrecheckIssue.Code.EVENT_LONGER_THAN_WORKING_DAY, List.of(103L)))
                .doesNotContain(tuple(PrecheckIssue.Code.NO_COMMON_WINDOW, List.of(103L)));
    }

    @Test
    void dependencyLongerThanTheSession() {
        Problem problem = problem().dependency(Dependency.atLeast(EXAM, DEFENSE, 3)).build();

        assertThat(Prechecks.run(problem)).extracting(PrecheckIssue::code, PrecheckIssue::eventIds)
                .containsExactly(tuple(PrecheckIssue.Code.DEPENDENCY_DOES_NOT_FIT, List.of(EXAM, DEFENSE)));
    }

    @Test
    void conflictingPinnedAssignments() {
        int tenOClock = 10 * 12;
        Problem problem = problem()
                .pin(new Assignment(EXAM, tenOClock, 1, List.of()))
                .pin(new Assignment(DEFENSE, tenOClock, 1, List.of(T2, T3), OptionalLong.of(T2))).build();

        assertThat(Prechecks.run(problem)).isNotEmpty().allMatch(issue -> issue.code() == PrecheckIssue.Code.PINNED_CONFLICT)
                .anyMatch(issue -> issue.message().equals("Закреплённые назначения: В аудитории одновременно два события")
                        && issue.eventIds().equals(List.of(EXAM, DEFENSE)));
    }

    @Test
    void peopleWithoutFreeTimeGetOneWarning() {
        Problem problem = problem(person(S1, Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, DAY_0, DAY_1),
                person(S2, Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, DAY_0, DAY_1)).build();

        assertThat(Prechecks.run(problem)).filteredOn(issue -> issue.code() == PrecheckIssue.Code.NO_FREE_TIME)
                .singleElement().satisfies(issue -> assertThat(issue.personIds()).containsExactly(S1, S2));
    }

    private static Person person(long id, Person.Role role, Person.Busy.Kind kind, SlotRange... busy) {
        return new Person(id, role, Arrays.stream(busy).map(range -> new Person.Busy(range, kind)).toList());
    }

    /** The base problem with some people replaced. */
    private static Problem.ProblemBuilder problem(Person... replaced) {
        List<Person> people = List.of(person(T1, Person.Role.TEACHER, Person.Busy.Kind.LOW),
                person(T2, Person.Role.TEACHER, Person.Busy.Kind.LOW), person(T3, Person.Role.TEACHER, Person.Busy.Kind.LOW),
                person(S1, Person.Role.STUDENT, Person.Busy.Kind.LOW), person(S2, Person.Role.STUDENT, Person.Busy.Kind.LOW));
        List<Person> merged = people.stream().map(p -> Arrays.stream(replaced).filter(r -> r.id() == p.id()).findFirst().orElse(p)).toList();
        return Problem.builder()
                .grid(GRID)
                .people(merged)
                .room(new Room(1, 30, List.of(PROJECTOR), List.of()))
                .room(new Room(9, null, List.of(), List.of(), true))
                .commissionCandidate(new CommissionCandidate(T2, true))
                .commissionCandidate(new CommissionCandidate(T3, false))
                .event(EXAM_EVENT)
                .event(DEFENSE_EVENT)
                .roomChangeBuffer(3)
                .weights(Weights.DEFAULT);
    }
}
