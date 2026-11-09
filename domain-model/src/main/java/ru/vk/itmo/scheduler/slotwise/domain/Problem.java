package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of a planning session that a solver works on (MODEL.md, «Входные данные солвера»).
 *
 * <p>The constructor checks that the parts reference each other correctly: unknown people, rooms
 * or events and duplicate ids are rejected. It does not check the hard constraints, that is the
 * validator's job: a problem may be unsolvable, and pinned assignments may already violate them.
 * All collections are sorted by id, so every solver sees the same order on every run.
 */
@Getter
@Accessors(fluent = true)
public final class Problem {
    private final TimeGrid grid;
    private final List<Person> people;
    private final List<Room> rooms;
    private final List<CommissionCandidate> commissionPool;
    private final List<Event> events;
    private final List<Dependency> dependencies;
    /** Assignments the solver must keep as they are (H9). */
    private final List<Assignment> pinned;
    /** Previous schedule for rescheduling, moves against it are penalised (S5). */
    private final List<Assignment> baseline;
    /** Slots a participant needs between onsite events in different rooms (H2). */
    private final int roomChangeBuffer;
    private final Weights weights;

    @Getter(AccessLevel.NONE)
    private final Map<Long, Person> peopleById;
    @Getter(AccessLevel.NONE)
    private final Map<Long, Room> roomsById;
    @Getter(AccessLevel.NONE)
    private final Map<Long, Event> eventsById;
    @Getter(AccessLevel.NONE)
    private final Map<Long, Assignment> pinnedByEvent;

    @Builder(toBuilder = true)
    public Problem(@NonNull TimeGrid grid,
                   @Singular("person") List<Person> people,
                   @Singular List<Room> rooms,
                   @Singular("commissionCandidate") List<CommissionCandidate> commissionPool,
                   @Singular List<Event> events,
                   @Singular("dependency") List<Dependency> dependencies,
                   @Singular("pin") List<Assignment> pinned,
                   @Singular("baselineAssignment") List<Assignment> baseline,
                   int roomChangeBuffer,
                   @NonNull Weights weights) {
        if (roomChangeBuffer < 0) {
            throw new IllegalArgumentException("Room change buffer must not be negative: " + roomChangeBuffer);
        }
        this.grid = grid;
        this.weights = weights;
        this.roomChangeBuffer = roomChangeBuffer;

        this.people = sorted(people, Person::id, "people");
        this.rooms = sorted(rooms, Room::id, "rooms");
        this.commissionPool = sorted(commissionPool, CommissionCandidate::personId, "commission pool");
        this.events = sorted(events, Event::id, "events");
        this.peopleById = index(this.people, Person::id);
        this.roomsById = index(this.rooms, Room::id);
        this.eventsById = index(this.events, Event::id);

        this.people.forEach(person -> person.busy()
                .forEach(busy -> requireInGrid(busy.range(), "busy time of person " + person.id())));
        this.rooms.forEach(room -> room.busy()
                .forEach(busy -> requireInGrid(busy, "busy time of room " + room.id())));
        for (CommissionCandidate candidate : this.commissionPool) {
            if (person(candidate.personId()).role() != Person.Role.TEACHER) {
                throw new IllegalArgumentException("Commission candidate " + candidate.personId() + " is not a teacher");
            }
        }
        for (Event event : this.events) {
            if (event.duration() > grid.slotCount()) {
                throw new IllegalArgumentException("Event " + event.id() + " is longer than the grid");
            }
            event.participants().forEach(this::person);
            event.commissionExcluded().forEach(this::person);
        }

        this.dependencies = dependencies.stream()
                .sorted(Comparator.comparingLong(Dependency::before).thenComparingLong(Dependency::after))
                .toList();
        Set<List<Long>> pairs = new HashSet<>();
        for (Dependency dependency : this.dependencies) {
            event(dependency.before());
            event(dependency.after());
            if (!pairs.add(List.of(dependency.before(), dependency.after()))) {
                throw new IllegalArgumentException("Duplicate dependency " + dependency.before() + " → " + dependency.after());
            }
        }

        this.pinned = assignments(pinned, "pinned assignments");
        this.baseline = assignments(baseline, "baseline");
        this.pinnedByEvent = index(this.pinned, Assignment::eventId);
    }

    public Person person(long id) {
        return find(peopleById, id, "person");
    }

    public Room room(long id) {
        return find(roomsById, id, "room");
    }

    public Event event(long id) {
        return find(eventsById, id, "event");
    }

    public Optional<Assignment> pinned(long eventId) {
        return Optional.ofNullable(pinnedByEvent.get(eventId));
    }

    private List<Assignment> assignments(List<Assignment> assignments, String what) {
        List<Assignment> sorted = sorted(assignments, Assignment::eventId, what);
        for (Assignment assignment : sorted) {
            Event event = event(assignment.eventId());
            requireInGrid(SlotRange.ofLength(assignment.start(), event.duration()), what + " of event " + event.id());
            Room room = room(assignment.venue().roomId());
            if (room.online() != (assignment.venue() instanceof Venue.Online)) {
                throw new IllegalArgumentException("Venue type does not match room " + room.id());
            }
            assignment.commission().forEach(this::person);
            if (assignment.chair().isPresent()) {
                long chairId = assignment.chair().getAsLong();
                if (commissionPool.stream().noneMatch(candidate -> candidate.personId() == chairId && candidate.canChair())) {
                    throw new IllegalArgumentException("Person " + chairId + " cannot chair the commission of event " + event.id());
                }
            }
        }
        return sorted;
    }

    private void requireInGrid(SlotRange range, String what) {
        if (range.end() > grid.slotCount()) {
            throw new IllegalArgumentException(what + " " + range + " is outside the grid of " + grid.slotCount() + " slots");
        }
    }

    private static <T> List<T> sorted(List<T> items, Function<T, Long> id, String what) {
        List<T> sorted = items.stream().sorted(Comparator.comparing(id)).toList();
        Ids.sortedUnique(sorted.stream().map(id).toList(), what);
        return sorted;
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toUnmodifiableMap(id, Function.identity()));
    }

    private static <T> T find(Map<Long, T> index, long id, String what) {
        T item = index.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown " + what + " " + id);
        }
        return item;
    }
}
