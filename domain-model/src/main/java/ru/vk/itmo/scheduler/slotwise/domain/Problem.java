package ru.vk.itmo.scheduler.slotwise.domain;

import lombok.Builder;
import lombok.Singular;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Immutable snapshot of a planning session that a solver works on (MODEL.md, «Входные данные солвера»).
 *
 * <p>The constructor checks that the parts reference each other correctly: unknown people, rooms
 * or events and duplicate ids are rejected. It does not check the hard constraints, that is the
 * validator's job: a problem may be unsolvable, and pinned assignments may already violate them.
 * All collections are sorted by id, so every solver sees the same order on every run.
 *
 * @param roomChangeBuffer slots a participant needs between onsite events in different rooms (H2)
 * @param pinned           assignments the solver must keep as they are (H9)
 * @param baseline         previous schedule for rescheduling, moves against it are penalised (S5)
 */
public final class Problem {
    private final TimeGrid grid;
    private final List<Person> people;
    private final List<Room> rooms;
    private final List<CommissionCandidate> commissionPool;
    private final List<Event> events;
    private final List<Dependency> dependencies;
    private final List<Assignment> pinned;
    private final List<Assignment> baseline;
    private final int roomChangeBuffer;
    private final Weights weights;

    private final Map<Long, Person> peopleById;
    private final Map<Long, Room> roomsById;
    private final Map<Long, Event> eventsById;
    private final Map<Long, Assignment> pinnedByEvent;

    @Builder(toBuilder = true)
    public Problem(TimeGrid grid,
                   @Singular("person") List<Person> people,
                   @Singular List<Room> rooms,
                   @Singular("commissionCandidate") List<CommissionCandidate> commissionPool,
                   @Singular List<Event> events,
                   @Singular("dependency") List<Dependency> dependencies,
                   @Singular("pin") List<Assignment> pinned,
                   @Singular("baselineAssignment") List<Assignment> baseline,
                   int roomChangeBuffer,
                   Weights weights) {
        this.grid = Objects.requireNonNull(grid, "grid");
        this.weights = Objects.requireNonNull(weights, "weights");
        if (roomChangeBuffer < 0) {
            throw new IllegalArgumentException("Room change buffer must not be negative: " + roomChangeBuffer);
        }
        this.roomChangeBuffer = roomChangeBuffer;

        this.people = sorted(people, Person::id, "people");
        this.rooms = sorted(rooms, Room::id, "rooms");
        this.commissionPool = sorted(commissionPool, CommissionCandidate::personId, "commission pool");
        this.events = sorted(events, Event::id, "events");
        this.peopleById = index(this.people, Person::id);
        this.roomsById = index(this.rooms, Room::id);
        this.eventsById = index(this.events, Event::id);

        for (Person person : this.people) {
            person.busy().forEach(busy -> requireInGrid(busy.range(), "busy time of person " + person.id()));
        }
        for (Room room : this.rooms) {
            room.busy().forEach(busy -> requireInGrid(busy, "busy time of room " + room.id()));
        }
        for (CommissionCandidate candidate : this.commissionPool) {
            if (person(candidate.personId()).role() != Person.Role.TEACHER) {
                throw new IllegalArgumentException("Commission candidate " + candidate.personId() + " is not a teacher");
            }
        }
        for (Event event : this.events) {
            if (event.duration() > grid.slotCount()) {
                throw new IllegalArgumentException("Event " + event.id() + " is longer than the grid");
            }
            event.participants().forEach(id -> requirePerson(id, "participant of event " + event.id()));
            event.commissionExcluded().forEach(id -> requirePerson(id, "commission exclusion of event " + event.id()));
        }

        this.dependencies = dependencies.stream()
                .sorted(Comparator.comparingLong(Dependency::before).thenComparingLong(Dependency::after))
                .toList();
        Set<List<Long>> pairs = new HashSet<>();
        for (Dependency dependency : this.dependencies) {
            requireEvent(dependency.before(), "dependency");
            requireEvent(dependency.after(), "dependency");
            if (!pairs.add(List.of(dependency.before(), dependency.after()))) {
                throw new IllegalArgumentException("Duplicate dependency " + dependency.before() + " → " + dependency.after());
            }
        }

        this.pinned = assignments(pinned, "pinned assignments");
        this.baseline = assignments(baseline, "baseline");
        this.pinnedByEvent = index(this.pinned, Assignment::eventId);
    }

    public TimeGrid grid() {
        return grid;
    }

    public List<Person> people() {
        return people;
    }

    public List<Room> rooms() {
        return rooms;
    }

    public List<CommissionCandidate> commissionPool() {
        return commissionPool;
    }

    public List<Event> events() {
        return events;
    }

    public List<Dependency> dependencies() {
        return dependencies;
    }

    public List<Assignment> pinned() {
        return pinned;
    }

    public List<Assignment> baseline() {
        return baseline;
    }

    public int roomChangeBuffer() {
        return roomChangeBuffer;
    }

    public Weights weights() {
        return weights;
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
            Event event = requireEvent(assignment.eventId(), what);
            requireInGrid(SlotRange.ofLength(assignment.start(), event.duration()), what + " of event " + event.id());
            if (assignment.venue() instanceof Venue.InRoom(long roomId) && !roomsById.containsKey(roomId)) {
                throw new IllegalArgumentException("Unknown room " + roomId + " in " + what + " of event " + event.id());
            }
            assignment.commission().forEach(id -> requirePerson(id, "commission of event " + event.id() + " in " + what));
        }
        return sorted;
    }

    private void requireInGrid(SlotRange range, String what) {
        if (range.end() > grid.slotCount()) {
            throw new IllegalArgumentException(what + " " + range + " is outside the grid of " + grid.slotCount() + " slots");
        }
    }

    private void requirePerson(long id, String what) {
        if (!peopleById.containsKey(id)) {
            throw new IllegalArgumentException("Unknown person " + id + " as " + what);
        }
    }

    private Event requireEvent(long id, String what) {
        Event event = eventsById.get(id);
        if (event == null) {
            throw new IllegalArgumentException("Unknown event " + id + " in " + what);
        }
        return event;
    }

    private static <T> List<T> sorted(List<T> items, Function<T, Long> id, String what) {
        List<T> sorted = items.stream().sorted(Comparator.comparing(id)).toList();
        Ids.sortedUnique(sorted.stream().map(id).toList(), what);
        return sorted;
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        Map<Long, T> index = new LinkedHashMap<>();
        items.forEach(item -> index.put(id.apply(item), item));
        return Map.copyOf(index);
    }

    private static <T> T find(Map<Long, T> index, long id, String what) {
        T item = index.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown " + what + " " + id);
        }
        return item;
    }
}
