package ru.vk.itmo.scheduler.slotwise.domain.validation;

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
import ru.vk.itmo.scheduler.slotwise.domain.Venue;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScheduleValidatorTests {
    private static final int DAY = 288;
    private static final long T1 = 1, T2 = 2, T3 = 3, T4 = 4, S1 = 11, S2 = 12;
    private static final long PROJECTOR = 1;
    private static final long R1 = 1, R2 = 2, SMALL = 3, ONLINE = 9;
    private static final long EXAM = 101, DEFENSE = 102, CONSULTATION = 103;

    /** Four days, 9:00–19:00. */
    private static final TimeGrid GRID = new TimeGrid(Instant.parse("2027-06-14T21:00:00Z"), ZoneId.of("Europe/Moscow"),
            5, 4 * DAY, IntStream.range(0, 4).mapToObj(d -> new SlotRange(slot(d, 9, 0), slot(d, 19, 0))).toList());

    private static final Event EXAM_EVENT = new Event(EXAM, 6, Event.Scope.GROUP, Event.Format.ONSITE,
            List.of(S1, S2, T1), Event.CommissionRequirement.NONE, List.of(), List.of(), 2);
    private static final Event DEFENSE_EVENT = new Event(DEFENSE, 6, Event.Scope.INDIVIDUAL, Event.Format.ONSITE,
            List.of(S1, T1), new Event.CommissionRequirement(2, true), List.of(T1), List.of(PROJECTOR), 0);
    private static final Event CONSULTATION_EVENT = new Event(CONSULTATION, 6, Event.Scope.GROUP, Event.Format.ONLINE,
            List.of(S1, S2, T1), Event.CommissionRequirement.NONE, List.of(), List.of(), 0);

    // A valid schedule: consultation on day 0 online, exam and defense on day 1.
    private static final Assignment CONSULTATION_OK = new Assignment(CONSULTATION, slot(0, 10, 0), new Venue.Online(ONLINE), List.of());
    private static final Assignment EXAM_OK = new Assignment(EXAM, slot(1, 10, 0), Venue.room(R2), List.of());
    private static final Assignment DEFENSE_OK = new Assignment(DEFENSE, slot(1, 12, 0), Venue.room(R1), List.of(T2, T3), OptionalLong.of(T2));

    @Test
    void validScheduleHasNoViolationsAndExpectedMetrics() {
        Validation validation = validate(problem().build(), CONSULTATION_OK, EXAM_OK, DEFENSE_OK);

        assertThat(validation.violations()).isEmpty();
        // S1: T1 idles 10:30–12:00 on day 1. S2: T2 and T3 sit 30 minutes, T4 none. S4: T1 goes from R2 to R1.
        assertThat(validation.rawPenalties()).containsEntry(SoftConstraint.S1, 90L).containsEntry(SoftConstraint.S2, 30L)
                .containsEntry(SoftConstraint.S3, 0L).containsEntry(SoftConstraint.S4, 1L).containsEntry(SoftConstraint.S5, 0L);
        assertThat(validation.score()).isEqualTo(new Score(0, 90 + 30 + 15));
        assertThat(validation.commissionLoadStdDevMinutes()).isCloseTo(Math.sqrt(200), within(1e-9));
        assertThat(validation.undesirableEvents()).isZero();
    }

    @Test
    void missingAssignmentsCountAsUnassigned() {
        assertThat(validate(problem().build(), CONSULTATION_OK, EXAM_OK).score().unassigned()).isEqualTo(1);
    }

    @Test
    void h1UnknownOrRepeatedEvent() {
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, DEFENSE_OK,
                new Assignment(999, slot(1, 15, 0), Venue.room(R2), List.of()),
                new Assignment(EXAM, slot(1, 15, 0), Venue.room(R2), List.of())), HardConstraint.H1, HardConstraint.H1);
    }

    @Test
    void h2PersonInTwoEventsOrWithoutTimeToChangeRooms() {
        Assignment overlapping = new Assignment(DEFENSE, slot(1, 10, 15), Venue.room(R1), List.of(T2, T3), OptionalLong.of(T2));
        Assignment rightAfter = new Assignment(DEFENSE, slot(1, 10, 30), Venue.room(R1), List.of(T2, T3), OptionalLong.of(T2));

        // S1 and T1 are in both events.
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, overlapping), HardConstraint.H2, HardConstraint.H2);
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, rightAfter), HardConstraint.H2, HardConstraint.H2);
    }

    @Test
    void h3RoomHasClassesOrTwoEvents() {
        Problem withClasses = problem().clearRooms().rooms(rooms(new Room(R2, 30, List.of(),
                List.of(new SlotRange(slot(1, 10, 0), slot(1, 11, 30)))))).build();
        assertViolations(validate(withClasses, CONSULTATION_OK, EXAM_OK, DEFENSE_OK), HardConstraint.H3);

        Problem withTeacher = problem().event(new Event(104, 6, Event.Scope.GROUP, Event.Format.ONSITE, List.of(T4),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 0)).build();
        assertViolations(validate(withTeacher, CONSULTATION_OK, EXAM_OK, DEFENSE_OK,
                new Assignment(104, slot(1, 10, 0), Venue.room(R2), List.of())), HardConstraint.H3);
    }

    @Test
    void h4ClassesAndCriticalTimeAreHardExceptForStudentsInGroupEvents() {
        SlotRange defenseTime = new SlotRange(slot(1, 12, 0), slot(1, 12, 30));
        SlotRange examTime = new SlotRange(slot(1, 10, 0), slot(1, 10, 30));

        assertViolations(validate(withBusy(S1, defenseTime, Person.Busy.Kind.TIMETABLE), CONSULTATION_OK, EXAM_OK, DEFENSE_OK),
                HardConstraint.H4);
        assertViolations(validate(withBusy(S1, defenseTime, Person.Busy.Kind.CRITICAL), CONSULTATION_OK, EXAM_OK, DEFENSE_OK),
                HardConstraint.H4);
        assertViolations(validate(withBusy(T1, examTime, Person.Busy.Kind.CRITICAL), CONSULTATION_OK, EXAM_OK, DEFENSE_OK),
                HardConstraint.H4);

        Validation groupCritical = validate(withBusy(S1, examTime, Person.Busy.Kind.CRITICAL), CONSULTATION_OK, EXAM_OK, DEFENSE_OK);
        assertThat(groupCritical.violations()).isEmpty();
        assertThat(groupCritical.rawPenalties()).containsEntry(SoftConstraint.S3, 10L * 30);
        assertThat(groupCritical.undesirableEvents()).isEqualTo(1);
    }

    @Test
    void s3WeighsOnlyTheOverlap() {
        SlotRange halfOfExam = new SlotRange(slot(1, 10, 15), slot(1, 11, 0));

        assertThat(validate(withBusy(T1, halfOfExam, Person.Busy.Kind.MEDIUM), CONSULTATION_OK, EXAM_OK, DEFENSE_OK)
                .rawPenalties()).containsEntry(SoftConstraint.S3, 3L * 15);
        assertThat(validate(withBusy(T1, halfOfExam, Person.Busy.Kind.LOW), CONSULTATION_OK, EXAM_OK, DEFENSE_OK)
                .rawPenalties()).containsEntry(SoftConstraint.S3, 15L);
    }

    @Test
    void s3CountsOverlappingBusyTimeOnceWithTheHighestCoefficient() {
        SlotRange examTime = new SlotRange(slot(1, 10, 0), slot(1, 10, 30));
        Problem base = problem().build();
        List<Person> people = base.people().stream().map(p -> p.id() == T1 ? new Person(T1, p.role(), List.of(
                new Person.Busy(examTime, Person.Busy.Kind.MEDIUM), new Person.Busy(examTime, Person.Busy.Kind.LOW))) : p).toList();

        assertThat(validate(base.toBuilder().clearPeople().people(people).build(), CONSULTATION_OK, EXAM_OK, DEFENSE_OK)
                .rawPenalties()).containsEntry(SoftConstraint.S3, 3L * 30);
    }

    @Test
    void h5VenueMustFitFormatCapacityAndEquipment() {
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK,
                new Assignment(DEFENSE, slot(1, 12, 0), Venue.room(R2), List.of(T2, T3), OptionalLong.of(T2))), HardConstraint.H5);
        assertViolations(validate(problem().build(), CONSULTATION_OK, DEFENSE_OK,
                new Assignment(EXAM, slot(1, 10, 0), Venue.room(SMALL), List.of())), HardConstraint.H5);
        assertViolations(validate(problem().build(), EXAM_OK, DEFENSE_OK,
                new Assignment(CONSULTATION, slot(0, 10, 0), Venue.room(R2), List.of())), HardConstraint.H5);
        assertViolations(validate(problem().build(), CONSULTATION_OK, DEFENSE_OK,
                new Assignment(EXAM, slot(1, 10, 0), new Venue.Online(ONLINE), List.of())), HardConstraint.H5);
    }

    @Test
    void h6CommissionMustHaveSizeMembersAndChair() {
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, defenseWith(List.of(T2), OptionalLong.of(T2))),
                HardConstraint.H6);
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, defenseWith(List.of(T1, T2), OptionalLong.of(T2))),
                HardConstraint.H6);
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, defenseWith(List.of(T2, T3), OptionalLong.of(T3))),
                HardConstraint.H6);
        assertViolations(validate(problem().build(), CONSULTATION_OK, EXAM_OK, defenseWith(List.of(T2, T3), OptionalLong.empty())),
                HardConstraint.H6);
    }

    @Test
    void h6NamedChairMustBeAbleToChairEvenIfNotRequired() {
        Problem withOptionalChair = problem().event(new Event(104, 6, Event.Scope.INDIVIDUAL, Event.Format.ONSITE,
                List.of(S2, T1), new Event.CommissionRequirement(2, false), List.of(), List.of(), 0)).build();
        Assignment base = new Assignment(104, slot(1, 15, 0), Venue.room(R2), List.of(T2, T3));

        assertThat(validate(withOptionalChair, CONSULTATION_OK, EXAM_OK, DEFENSE_OK, base).violations()).isEmpty();
        assertViolations(validate(withOptionalChair, CONSULTATION_OK, EXAM_OK, DEFENSE_OK,
                new Assignment(104, base.start(), base.venue(), base.commission(), OptionalLong.of(T3))), HardConstraint.H6);
    }

    @Test
    void h7DependencyKeepsOrderAndGap() {
        Assignment sameDay = new Assignment(CONSULTATION, slot(1, 9, 0), new Venue.Online(ONLINE), List.of());
        Assignment tooEarly = new Assignment(EXAM, slot(3, 10, 0), Venue.room(R2), List.of());

        assertViolations(validate(problem().build(), sameDay, EXAM_OK, DEFENSE_OK), HardConstraint.H7);
        assertViolations(validate(problem().build(), CONSULTATION_OK, tooEarly, DEFENSE_OK), HardConstraint.H7);
    }

    @Test
    void h8EventStaysWithinWorkingHours() {
        assertViolations(validate(problem().build(), CONSULTATION_OK, DEFENSE_OK,
                new Assignment(EXAM, slot(1, 18, 45), Venue.room(R2), List.of())), HardConstraint.H8);
        assertViolations(validate(problem().build(), CONSULTATION_OK, DEFENSE_OK,
                new Assignment(EXAM, 4 * DAY - 2, Venue.room(R2), List.of())), HardConstraint.H8);
    }

    @Test
    void h9PinnedAssignmentsDoNotMove() {
        Problem pinned = problem().pin(CONSULTATION_OK).build();

        assertThat(validate(pinned, CONSULTATION_OK, EXAM_OK, DEFENSE_OK).violations()).isEmpty();
        assertViolations(validate(pinned, EXAM_OK, DEFENSE_OK), HardConstraint.H9);
    }

    @Test
    void h10StudentNeedsPreparationDaysBetweenExams() {
        Problem twoExams = problem().event(new Event(104, 6, Event.Scope.GROUP, Event.Format.ONSITE, List.of(S1, S2, T4),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 2)).build();
        Assignment nextDay = new Assignment(104, slot(2, 10, 0), Venue.room(R2), List.of());
        Assignment twoDaysLater = new Assignment(104, slot(3, 10, 0), Venue.room(R2), List.of());

        // Two students, one violation each.
        assertViolations(validate(twoExams, CONSULTATION_OK, EXAM_OK, DEFENSE_OK, nextDay), HardConstraint.H10, HardConstraint.H10);
        assertThat(validate(twoExams, CONSULTATION_OK, EXAM_OK, DEFENSE_OK, twoDaysLater).violations()).isEmpty();
    }

    @Test
    void s5CountsChangesAgainstBaseline() {
        Problem rescheduling = problem().baselineAssignment(DEFENSE_OK).baselineAssignment(EXAM_OK).build();
        Assignment moved = new Assignment(DEFENSE, slot(1, 14, 0), Venue.room(R1), List.of(T2, T3), OptionalLong.of(T2));

        assertThat(validate(rescheduling, CONSULTATION_OK, EXAM_OK, moved).rawPenalties()).containsEntry(SoftConstraint.S5, 1L);
        assertThat(validate(rescheduling, CONSULTATION_OK, moved).rawPenalties()).containsEntry(SoftConstraint.S5, 2L);
    }

    private static Assignment defenseWith(List<Long> commission, OptionalLong chair) {
        return new Assignment(DEFENSE, slot(1, 12, 0), Venue.room(R1), commission, chair);
    }

    private static Problem withBusy(long personId, SlotRange range, Person.Busy.Kind kind) {
        Problem base = problem().build();
        List<Person> people = base.people().stream().map(p -> p.id() == personId
                ? new Person(p.id(), p.role(), List.of(new Person.Busy(range, kind))) : p).toList();
        return base.toBuilder().clearPeople().people(people).build();
    }

    private static Problem.ProblemBuilder problem() {
        return Problem.builder()
                .grid(GRID)
                .person(new Person(T1, Person.Role.TEACHER, List.of()))
                .person(new Person(T2, Person.Role.TEACHER, List.of()))
                .person(new Person(T3, Person.Role.TEACHER, List.of()))
                .person(new Person(T4, Person.Role.TEACHER, List.of()))
                .person(new Person(S1, Person.Role.STUDENT, List.of()))
                .person(new Person(S2, Person.Role.STUDENT, List.of()))
                .rooms(rooms(new Room(R2, 30, List.of(), List.of())))
                .commissionCandidate(new CommissionCandidate(T2, true))
                .commissionCandidate(new CommissionCandidate(T3, false))
                .commissionCandidate(new CommissionCandidate(T4, false))
                .event(EXAM_EVENT)
                .event(DEFENSE_EVENT)
                .event(CONSULTATION_EVENT)
                .dependency(Dependency.between(CONSULTATION, EXAM, 1, 2))
                .roomChangeBuffer(3)
                .weights(Weights.DEFAULT);
    }

    private static List<Room> rooms(Room r2) {
        return List.of(new Room(R1, 30, List.of(PROJECTOR), List.of()), r2, new Room(SMALL, 2, List.of(), List.of()),
                new Room(ONLINE, null, List.of(), List.of(), true));
    }

    private static Validation validate(Problem problem, Assignment... assignments) {
        return ScheduleValidator.validate(problem, List.of(assignments));
    }

    private static void assertViolations(Validation validation, HardConstraint... expected) {
        assertThat(validation.violations()).extracting(Violation::constraint).containsExactlyInAnyOrder(expected);
    }

    private static int slot(int day, int hour, int minute) {
        return day * DAY + hour * 12 + minute / 5;
    }
}
