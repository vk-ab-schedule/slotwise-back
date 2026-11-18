package ru.vk.itmo.scheduler.slotwise.domain.validation;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single source of truth about a schedule (MODEL.md, «Валидатор — источник истины»): finds violations
 * of H1–H10 and computes (U, P) and the metrics. Solver results and manual edits are checked by it; no solver
 * scores itself.
 *
 * <p>Assignments come from solvers and the organizer, so unlike {@link Problem} they are not trusted:
 * unknown events, rooms and people become violations, not exceptions.
 */
public final class ScheduleValidator {
    private static final int MEDIUM_COEFFICIENT = 3;
    private static final int LOW_COEFFICIENT = 1;
    private static final int STUDENT_CRITICAL_IN_GROUP_COEFFICIENT = 10;

    private final Problem problem;
    private final TimeGrid grid;
    private final Map<Long, Event> events;
    private final Map<Long, Room> rooms;
    private final Map<Long, Person> people;
    private final Map<Long, CommissionCandidate> pool;
    private final List<Violation> violations = new ArrayList<>();
    /** Known events with one assignment each; the ones that fit the grid are also in {@link #placed}. */
    private final Map<Long, Assignment> assigned = new TreeMap<>();
    private final List<Placement> placed = new ArrayList<>();
    private final Map<Long, List<Placement>> byPerson = new TreeMap<>();
    private final Set<Long> undesirableEvents = new HashSet<>();
    private long undesirableMinutes;

    /** An assignment that fits the grid; roomId is null for an online platform. */
    private record Placement(Event event, Assignment assignment, SlotRange range, int day, Long roomId) {
        List<Long> people() {
            // Distinct: a participant wrongly put on the commission is still one person (reported by H6).
            List<Long> people = new ArrayList<>(event.participants());
            people.addAll(assignment.commission());
            return people.stream().distinct().toList();
        }
    }

    private ScheduleValidator(Problem problem) {
        this.problem = problem;
        this.grid = problem.grid();
        this.events = index(problem.events(), Event::id);
        this.rooms = index(problem.rooms(), Room::id);
        this.people = index(problem.people(), Person::id);
        this.pool = index(problem.commissionPool(), CommissionCandidate::personId);
    }

    public static Validation validate(Problem problem, List<Assignment> assignments) {
        return new ScheduleValidator(problem).run(assignments);
    }

    private Validation run(List<Assignment> assignments) {
        assignments.stream().sorted(Comparator.comparingLong(Assignment::eventId)).forEach(this::accept);
        checkPinned();
        checkRooms();
        checkPeople();
        checkDependencies();
        checkPreparation();

        Map<SoftConstraint, Long> raw = new EnumMap<>(SoftConstraint.class);
        raw.put(SoftConstraint.S1, teacherDays(this::gaps));
        long[] loads = commissionLoads();
        raw.put(SoftConstraint.S2, loads.length == 0 ? 0 : max(loads) - min(loads));
        raw.put(SoftConstraint.S3, undesirableMinutes);
        raw.put(SoftConstraint.S4, teacherDays(this::roomSwitches));
        raw.put(SoftConstraint.S5, moves());
        Weights w = problem.weights();
        long penalty = w.gaps() * raw.get(SoftConstraint.S1) + w.unfairness() * raw.get(SoftConstraint.S2)
                + w.undesirable() * raw.get(SoftConstraint.S3) + w.roomSwitches() * raw.get(SoftConstraint.S4)
                + w.moves() * raw.get(SoftConstraint.S5);
        Score score = new Score(events.size() - assigned.size(), penalty);
        return new Validation(violations, score, raw, standardDeviation(loads), undesirableEvents.size());
    }

    private void accept(Assignment assignment) {
        Event event = events.get(assignment.eventId());
        if (event == null) {
            violation(HardConstraint.H1, "Назначено неизвестное событие " + assignment.eventId(),
                    List.of(assignment.eventId()), List.of(), List.of());
            return;
        }
        if (assigned.putIfAbsent(event.id(), assignment) != null) {
            violation(HardConstraint.H1, "Событие " + event.id() + " назначено больше одного раза",
                    List.of(event.id()), List.of(), List.of());
            return;
        }
        SlotRange range = SlotRange.ofLength(assignment.start(), event.duration());
        if (range.end() > grid.slotCount() || !grid.fitsWorkingPeriod(assignment.start(), event.duration())) {
            violation(HardConstraint.H8, "Событие " + event.id() + " выходит за рабочее время сессии",
                    List.of(event.id()), List.of(), List.of());
            if (range.end() > grid.slotCount()) {
                return;
            }
        }
        Long roomId = checkVenue(event, assignment);
        checkCommission(event, assignment);
        Placement placement = new Placement(event, assignment, range, grid.dayOf(range.start()), roomId);
        placed.add(placement);
        for (long person : placement.people()) {
            if (people.containsKey(person)) {
                byPerson.computeIfAbsent(person, _ -> new ArrayList<>()).add(placement);
            } else {
                violation(HardConstraint.H6, "В событии " + event.id() + " неизвестный участник " + person,
                        List.of(event.id()), List.of(person), List.of());
            }
        }
    }

    /** H5. Returns the physical room id, or null for an online platform or an unknown room. */
    private Long checkVenue(Event event, Assignment assignment) {
        long roomId = assignment.venue().roomId();
        Room room = rooms.get(roomId);
        List<Long> eventIds = List.of(event.id());
        if (room == null || room.online() != (assignment.venue() instanceof Venue.Online)) {
            violation(HardConstraint.H5, "Событие " + event.id() + " назначено в неизвестную площадку " + roomId,
                    eventIds, List.of(), List.of(roomId));
            return null;
        }
        if (event.format() == Event.Format.ONLINE && !room.online()) {
            violation(HardConstraint.H5, "Онлайн-событие " + event.id() + " назначено в аудиторию",
                    eventIds, List.of(), List.of(roomId));
        }
        if (event.format() != Event.Format.ONLINE && room.online()) {
            violation(HardConstraint.H5, "Очное событие " + event.id() + " назначено на онлайн-площадку",
                    eventIds, List.of(), List.of(roomId));
        }
        if (room.online()) {
            return null;
        }
        int people = event.participants().size() + assignment.commission().size();
        if (people > room.capacity()) {
            violation(HardConstraint.H5, "В аудитории " + roomId + " " + room.capacity() + " мест, а в событии "
                    + event.id() + " участников: " + people, eventIds, List.of(), List.of(roomId));
        }
        if (!room.equipment().containsAll(event.equipment())) {
            violation(HardConstraint.H5, "В аудитории " + roomId + " нет оборудования для события " + event.id(),
                    eventIds, List.of(), List.of(roomId));
        }
        return roomId;
    }

    private void checkCommission(Event event, Assignment assignment) {
        Event.CommissionRequirement requirement = event.commission();
        List<Long> members = assignment.commission();
        List<Long> eventIds = List.of(event.id());
        if (members.size() != requirement.size()) {
            violation(HardConstraint.H6, "В комиссии события " + event.id() + " " + members.size()
                    + " чел. вместо " + requirement.size(), eventIds, members, List.of());
        }
        List<Long> wrong = members.stream().filter(member -> !pool.containsKey(member)
                || event.participants().contains(member) || event.commissionExcluded().contains(member)).toList();
        if (!wrong.isEmpty()) {
            violation(HardConstraint.H6, "В комиссию события " + event.id() + " нельзя включать " + wrong,
                    eventIds, wrong, List.of());
        }
        if (requirement.chairRequired()) {
            OptionalLong chair = assignment.chair();
            if (chair.isEmpty() || !pool.containsKey(chair.getAsLong()) || !pool.get(chair.getAsLong()).canChair()) {
                violation(HardConstraint.H6, "У комиссии события " + event.id() + " нет председателя с правом председательствовать",
                        eventIds, chair.stream().boxed().toList(), List.of());
            }
        }
    }

    private void checkPinned() {
        for (Assignment pin : problem.pinned()) {
            if (!pin.equals(assigned.get(pin.eventId()))) {
                violation(HardConstraint.H9, "Закреплённое событие " + pin.eventId() + " изменено",
                        List.of(pin.eventId()), List.of(), List.of());
            }
        }
    }

    private void checkRooms() {
        Map<Long, List<Placement>> byRoom = placed.stream().filter(p -> p.roomId() != null)
                .collect(Collectors.groupingBy(Placement::roomId, TreeMap::new, Collectors.toList()));
        byRoom.forEach((roomId, placements) -> {
            forOverlaps(placements, (a, b) -> violation(HardConstraint.H3, "Аудитория " + roomId + " занята событиями "
                    + a.event().id() + " и " + b.event().id() + " одновременно",
                    List.of(a.event().id(), b.event().id()), List.of(), List.of(roomId)));
            for (Placement placement : placements) {
                if (rooms.get(roomId).busy().stream().anyMatch(placement.range()::overlaps)) {
                    violation(HardConstraint.H3, "Событие " + placement.event().id() + " попадает на пару в аудитории " + roomId,
                            List.of(placement.event().id()), List.of(), List.of(roomId));
                }
            }
        });
    }

    /** H2, H4 and the S3 penalty. */
    private void checkPeople() {
        byPerson.forEach((personId, placements) -> {
            Person person = people.get(personId);
            forOverlaps(placements, (a, b) -> violation(HardConstraint.H2, "Участник " + personId + " одновременно в событиях "
                    + a.event().id() + " и " + b.event().id(),
                    List.of(a.event().id(), b.event().id()), List.of(personId), List.of()));
            List<Placement> onsite = sortedByStart(placements.stream().filter(p -> p.roomId() != null).toList());
            for (int i = 1; i < onsite.size(); i++) {
                Placement before = onsite.get(i - 1);
                Placement after = onsite.get(i);
                int gap = after.range().start() - before.range().end();
                if (gap >= 0 && gap < problem.roomChangeBuffer() && !before.roomId().equals(after.roomId())) {
                    violation(HardConstraint.H2, "Участнику " + personId + " не хватает времени перейти из аудитории "
                            + before.roomId() + " в " + after.roomId(), List.of(before.event().id(), after.event().id()),
                            List.of(personId), List.of(before.roomId(), after.roomId()));
                }
            }
            for (Placement placement : placements) {
                checkBusy(person, placement);
            }
        });
    }

    private void checkBusy(Person person, Placement placement) {
        for (Person.Busy busy : person.busy()) {
            if (!busy.range().overlaps(placement.range())) {
                continue;
            }
            boolean studentInGroupEvent = person.role() == Person.Role.STUDENT && placement.event().scope() == Event.Scope.GROUP;
            int coefficient = switch (busy.kind()) {
                case TIMETABLE -> 0;
                case CRITICAL -> studentInGroupEvent ? STUDENT_CRITICAL_IN_GROUP_COEFFICIENT : 0;
                case MEDIUM -> MEDIUM_COEFFICIENT;
                case LOW -> LOW_COEFFICIENT;
            };
            if (coefficient == 0) {
                violation(HardConstraint.H4, "Событие " + placement.event().id() + " попадает на "
                        + (busy.kind() == Person.Busy.Kind.TIMETABLE ? "пару" : "критическую занятость")
                        + " участника " + person.id(), List.of(placement.event().id()), List.of(person.id()), List.of());
            } else {
                undesirableEvents.add(placement.event().id());
                undesirableMinutes += (long) coefficient * grid.toMinutes(overlap(busy.range(), placement.range()));
            }
        }
    }

    private void checkDependencies() {
        Map<Long, Placement> byEvent = placed.stream().collect(Collectors.toMap(p -> p.event().id(), Function.identity()));
        for (Dependency dependency : problem.dependencies()) {
            Placement before = byEvent.get(dependency.before());
            Placement after = byEvent.get(dependency.after());
            if (before == null || after == null) {
                continue;
            }
            int days = after.day() - before.day();
            boolean ordered = before.range().end() <= after.range().start();
            boolean tooLate = dependency.maxGapDays().isPresent() && days > dependency.maxGapDays().getAsInt();
            if (!ordered || days < dependency.minGapDays() || tooLate) {
                violation(HardConstraint.H7, "Событие " + dependency.before() + " должно быть раньше события "
                        + dependency.after() + " на " + dependency.minGapDays()
                        + dependency.maxGapDays().stream().mapToObj(max -> "–" + max).findFirst().orElse("+") + " дн.",
                        List.of(dependency.before(), dependency.after()), List.of(), List.of());
            }
        }
    }

    private void checkPreparation() {
        byPerson.forEach((personId, placements) -> {
            if (people.get(personId).role() != Person.Role.STUDENT) {
                return;
            }
            List<Placement> withPreparation = sortedByStart(placements.stream()
                    .filter(p -> p.event().preparationDays() > 0).toList());
            for (int i = 0; i < withPreparation.size(); i++) {
                for (int j = i + 1; j < withPreparation.size(); j++) {
                    Placement a = withPreparation.get(i);
                    Placement b = withPreparation.get(j);
                    int needed = Math.max(a.event().preparationDays(), b.event().preparationDays());
                    if (b.day() - a.day() < needed) {
                        violation(HardConstraint.H10, "У студента " + personId + " между событиями " + a.event().id()
                                + " и " + b.event().id() + " меньше " + needed + " дн. на подготовку",
                                List.of(a.event().id(), b.event().id()), List.of(personId), List.of());
                    }
                }
            }
        });
    }

    /** Sum of a per-day measure over teachers. */
    private long teacherDays(Function<List<Placement>, Long> measure) {
        long total = 0;
        for (Map.Entry<Long, List<Placement>> entry : byPerson.entrySet()) {
            if (people.get(entry.getKey()).role() == Person.Role.TEACHER) {
                Map<Integer, List<Placement>> byDay = entry.getValue().stream()
                        .collect(Collectors.groupingBy(Placement::day, TreeMap::new, Collectors.toList()));
                for (List<Placement> day : byDay.values()) {
                    total += measure.apply(sortedByStart(day));
                }
            }
        }
        return total;
    }

    /** S1: idle minutes between the events of one day. */
    private long gaps(List<Placement> day) {
        long gaps = 0;
        int end = day.getFirst().range().end();
        for (Placement placement : day) {
            gaps += grid.toMinutes(Math.max(0, placement.range().start() - end));
            end = Math.max(end, placement.range().end());
        }
        return gaps;
    }

    /** S4: changes of room between consecutive onsite events of one day. */
    private long roomSwitches(List<Placement> day) {
        List<Long> onsiteRooms = day.stream().map(Placement::roomId).filter(room -> room != null).toList();
        long switches = 0;
        for (int i = 1; i < onsiteRooms.size(); i++) {
            if (!onsiteRooms.get(i).equals(onsiteRooms.get(i - 1))) {
                switches++;
            }
        }
        return switches;
    }

    /** Commission minutes of every pool member, including those without commissions. */
    private long[] commissionLoads() {
        Map<Long, Long> loads = new TreeMap<>();
        pool.keySet().forEach(member -> loads.put(member, 0L));
        for (Placement placement : placed) {
            for (long member : placement.assignment().commission()) {
                loads.computeIfPresent(member, (_, load) -> load + grid.toMinutes(placement.event().duration()));
            }
        }
        return loads.values().stream().mapToLong(Long::longValue).toArray();
    }

    /** S5: baseline events that are now unassigned or have another time, venue or commission. */
    private long moves() {
        return problem.baseline().stream().filter(base -> {
            Assignment now = assigned.get(base.eventId());
            return now == null || now.start() != base.start() || !now.venue().equals(base.venue())
                    || !now.commission().equals(base.commission());
        }).count();
    }

    private static void forOverlaps(List<Placement> placements, OverlapHandler handler) {
        List<Placement> sorted = sortedByStart(placements);
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size() && sorted.get(j).range().start() < sorted.get(i).range().end(); j++) {
                handler.overlap(sorted.get(i), sorted.get(j));
            }
        }
    }

    @FunctionalInterface
    private interface OverlapHandler {
        void overlap(Placement first, Placement second);
    }

    private static List<Placement> sortedByStart(List<Placement> placements) {
        return placements.stream().sorted(Comparator.comparing(Placement::range)
                .thenComparingLong(p -> p.event().id())).toList();
    }

    private static int overlap(SlotRange a, SlotRange b) {
        return Math.min(a.end(), b.end()) - Math.max(a.start(), b.start());
    }

    private static double standardDeviation(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        double mean = (double) Arrays.stream(values).sum() / values.length;
        return Math.sqrt(Arrays.stream(values).mapToDouble(v -> (v - mean) * (v - mean)).sum() / values.length);
    }

    private static long max(long[] values) {
        return Arrays.stream(values).max().orElse(0);
    }

    private static long min(long[] values) {
        return Arrays.stream(values).min().orElse(0);
    }

    private void violation(HardConstraint constraint, String message, List<Long> eventIds, List<Long> personIds,
                           List<Long> roomIds) {
        violations.add(new Violation(constraint, message, eventIds, personIds, roomIds));
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
