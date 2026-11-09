package ru.vk.itmo.scheduler.slotwise.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProblemTests {
    private static final TimeGrid GRID = new TimeGrid(Instant.parse("2027-06-14T21:00:00Z"), ZoneId.of("Europe/Moscow"),
            5, 288, List.of(new SlotRange(108, 228)));
    private static final Person TEACHER = new Person(1, Person.Role.TEACHER, List.of());
    private static final Person STUDENT = new Person(2, Person.Role.STUDENT, List.of());
    private static final Room ROOM = new Room(10, 30, List.of(), List.of());

    @Test
    void collectionsAreSortedById() {
        Problem problem = base()
                .event(defense(101))
                .event(defense(100))
                .dependency(Dependency.atLeast(101, 100, 3))
                .build();

        assertThat(problem.people()).extracting(Person::id).containsExactly(1L, 2L);
        assertThat(problem.events()).extracting(Event::id).containsExactly(100L, 101L);
        assertThat(problem.event(101).participants()).containsExactly(1L, 2L);
    }

    @Test
    void busyTimeIsSortedByStart() {
        Person person = new Person(3, Person.Role.TEACHER, List.of(
                new Person.Busy(new SlotRange(150, 160), Person.Busy.Kind.LOW),
                new Person.Busy(new SlotRange(120, 130), Person.Busy.Kind.TIMETABLE)));

        assertThat(person.busy()).extracting(Person.Busy::range)
                .containsExactly(new SlotRange(120, 130), new SlotRange(150, 160));
    }

    @Test
    void pinnedAssignmentsAreFoundByEvent() {
        Assignment pin = new Assignment(100, 120, Venue.room(10), List.of());

        Problem problem = base().event(defense(100)).pin(pin).build();

        assertThat(problem.pinned(100)).contains(pin);
        assertThat(problem.pinned(101)).isEmpty();
    }

    @Test
    void rejectsDuplicateIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).event(defense(100)).build())
                .withMessageContaining("Duplicate id 100");
    }

    @Test
    void rejectsUnknownParticipant() {
        Event event = new Event(100, 6, Event.Scope.INDIVIDUAL, Event.Format.ONSITE, List.of(2L, 99L),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(event).build())
                .withMessageContaining("Unknown person 99");
    }

    @Test
    void commissionCandidateMustBeTeacher() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().commissionCandidate(new CommissionCandidate(2, false)).build())
                .withMessageContaining("not a teacher");
    }

    @Test
    void rejectsDependencyOnUnknownEvent() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).dependency(Dependency.atLeast(100, 200, 1)).build())
                .withMessageContaining("Unknown event 200");
    }

    @Test
    void rejectsPinnedAssignmentInUnknownRoom() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).pin(new Assignment(100, 120, Venue.room(11), List.of())).build())
                .withMessageContaining("Unknown room 11");
    }

    @Test
    void onlineAssignmentKeepsItsPlatformId() {
        Assignment pin = new Assignment(100, 120, new Venue.Online(12), List.of());
        Problem problem = base().event(defense(100))
                .room(new Room(11, null, List.of(), List.of(), true))
                .room(new Room(12, null, List.of(), List.of(), true))
                .pin(pin).build();

        assertThat(problem.pinned(100).orElseThrow().venue().roomId()).isEqualTo(12);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).pin(pin).build())
                .withMessageContaining("Unknown room 12");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100))
                        .pin(new Assignment(100, 120, new Venue.Online(10), List.of())).build())
                .withMessageContaining("Venue type does not match room 10");
    }

    @Test
    void chairMustBeAMemberOfTheCommissionWithChairPermission() {
        Person chair = new Person(3, Person.Role.TEACHER, List.of());
        Assignment pin = new Assignment(100, 120, Venue.room(10), List.of(3L), OptionalLong.of(3));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Assignment(100, 120, Venue.room(10), List.of(1L), OptionalLong.of(3)))
                .withMessageContaining("is not in the commission");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).person(chair)
                        .commissionCandidate(new CommissionCandidate(3, false)).pin(pin).build())
                .withMessageContaining("cannot chair");
        Problem problem = base().event(defense(100)).person(chair)
                .commissionCandidate(new CommissionCandidate(3, true)).pin(pin).build();
        assertThat(problem.pinned(100).orElseThrow().chair()).hasValue(3);
    }

    @Test
    void rejectsAssignmentEndingAfterTheGrid() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().event(defense(100)).pin(new Assignment(100, 285, new Venue.Online(11), List.of())).build())
                .withMessageContaining("outside the grid");
    }

    @Test
    void rejectsBusyTimeOutsideTheGrid() {
        Person person = new Person(3, Person.Role.TEACHER,
                List.of(new Person.Busy(new SlotRange(280, 300), Person.Busy.Kind.CRITICAL)));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> base().person(person).build())
                .withMessageContaining("outside the grid");
    }

    @Test
    void rejectsInconsistentParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Event.CommissionRequirement(0, true));
        assertThatIllegalArgumentException().isThrownBy(() -> Dependency.between(1, 2, 3, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> new SlotRange(5, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> new Assignment(1, 0, new Venue.Online(11), List.of(3L, 3L)));
    }

    private static Problem.ProblemBuilder base() {
        return Problem.builder()
                .grid(GRID)
                .person(STUDENT)
                .person(TEACHER)
                .room(ROOM)
                .roomChangeBuffer(3)
                .weights(Weights.DEFAULT);
    }

    private static Event defense(long id) {
        return new Event(id, 6, Event.Scope.INDIVIDUAL, Event.Format.ONSITE, List.of(2L, 1L),
                Event.CommissionRequirement.NONE, List.of(), List.of(), 0);
    }
}
