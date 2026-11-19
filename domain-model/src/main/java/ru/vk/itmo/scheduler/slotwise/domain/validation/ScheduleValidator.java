package ru.vk.itmo.scheduler.slotwise.domain.validation;

import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.BusyRules;
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
import ru.vk.itmo.scheduler.slotwise.domain.validation.Violation.Reason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The single source of truth about a schedule (MODEL.md, «Валидатор — источник истины»): finds violations
 * of H1–H10 and computes (U, P) and the metrics. Solver results and manual edits are checked by it; no solver
 * scores itself.
 *
 * <p>Assignments come from solvers and the organizer, so unlike {@link Problem} they are not trusted:
 * unknown events, rooms and people become violations, not exceptions.
 */
public final class ScheduleValidator {
    private final Problem problem;
    private final TimeGrid grid;
    private final Map<Long, Event> events;
    private final Map<Long, Room> rooms;
    private final Map<Long, Person> people;
    private final Map<Long, CommissionCandidate> pool;
    /** Violations with the same reason, events, rooms and message are merged into one with all their people. */
    private final Map<List<Object>, Violation> violations = new LinkedHashMap<>();
    /** Known events with one assignment each; the ones that fit the grid are also in {@link #placed}. */
    private final Map<Long, Assignment> assigned = new TreeMap<>();
    private final List<Placement> placed = new ArrayList<>();
    private final Map<Long, List<Placement>> byPerson = new TreeMap<>();
    private final Set<Long> undesirableEvents = new HashSet<>();
    private long undesirableMinutes;

    /** An assignment that fits the grid; physicalRoom is null for an online platform or an unknown room. */
    private record Placement(Event event, Assignment assignment, SlotRange range, int day, Long physicalRoom) {
        List<Long> people() {
            // Distinct: a participant wrongly put on the commission is still one person (reported by H6).
            return Stream.concat(event.participants().stream(), assignment.commission().stream()).distinct().toList();
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
        raw.put(SoftConstraint.S2, Arrays.stream(loads).max().orElse(0) - Arrays.stream(loads).min().orElse(0));
        raw.put(SoftConstraint.S3, undesirableMinutes);
        raw.put(SoftConstraint.S4, teacherDays(this::roomSwitches));
        raw.put(SoftConstraint.S5, moves());
        Weights w = problem.weights();
        long penalty = w.teacherGaps() * raw.get(SoftConstraint.S1)
                + w.commissionLoadSpread() * raw.get(SoftConstraint.S2)
                + w.undesirableTime() * raw.get(SoftConstraint.S3)
                + w.teacherRoomChanges() * raw.get(SoftConstraint.S4)
                + w.movedEvents() * raw.get(SoftConstraint.S5);
        Score score = new Score(events.size() - assigned.size(), penalty);
        return new Validation(List.copyOf(violations.values()), score, raw, standardDeviation(loads), undesirableEvents.size());
    }

    private void accept(Assignment assignment) {
        Event event = events.get(assignment.eventId());
        if (event == null) {
            violation(Reason.UNKNOWN_EVENT, "Назначено событие, которого нет в сессии", List.of(assignment.eventId()));
            return;
        }
        if (assigned.putIfAbsent(event.id(), assignment) != null) {
            violation(Reason.DUPLICATE_ASSIGNMENT, "Событие назначено больше одного раза", List.of(event.id()));
            return;
        }
        SlotRange range = SlotRange.ofLength(assignment.start(), event.duration());
        boolean inGrid = range.end() <= grid.slotCount();
        if (!inGrid || !grid.fitsWorkingPeriod(assignment.start(), event.duration())) {
            violation(Reason.OUTSIDE_WORKING_HOURS, "Событие выходит за рабочее время сессии", List.of(event.id()));
            if (!inGrid) {
                return;
            }
        }
        Long physicalRoom = checkRoom(event, assignment);
        checkCommission(event, assignment);
        Placement placement = new Placement(event, assignment, range, grid.dayOf(range.start()), physicalRoom);
        placed.add(placement);
        // Unknown commission members are already reported by H6.
        placement.people().stream().filter(people::containsKey)
                .forEach(person -> byPerson.computeIfAbsent(person, _ -> new ArrayList<>()).add(placement));
    }

    /** H5. Returns the physical room id, or null for an online platform or an unknown room. */
    private Long checkRoom(Event event, Assignment assignment) {
        long roomId = assignment.roomId();
        Room room = rooms.get(roomId);
        List<Long> eventIds = List.of(event.id());
        if (room == null) {
            violation(Reason.UNKNOWN_ROOM, "Событие назначено в аудиторию, которой нет в сессии", eventIds, List.of(), List.of(roomId));
            return null;
        }
        if (event.format() == Event.Format.ONLINE && !room.online()) {
            violation(Reason.ONLINE_EVENT_IN_ROOM, "Онлайн-событие назначено в аудиторию, а не на онлайн-площадку",
                    eventIds, List.of(), List.of(roomId));
        }
        if (event.format() != Event.Format.ONLINE && room.online()) {
            violation(Reason.ONSITE_EVENT_ONLINE, "Очное событие назначено на онлайн-площадку", eventIds, List.of(), List.of(roomId));
        }
        if (room.online()) {
            return null;
        }
        int people = event.participants().size() + assignment.commission().size();
        if (people > room.capacity()) {
            violation(Reason.ROOM_TOO_SMALL, "В аудитории " + room.capacity() + " мест, а участников вместе с комиссией " + people,
                    eventIds, List.of(), List.of(roomId));
        }
        if (!room.equipment().containsAll(event.equipment())) {
            violation(Reason.ROOM_LACKS_EQUIPMENT, "В аудитории нет нужного для события оборудования", eventIds, List.of(), List.of(roomId));
        }
        return roomId;
    }

    private void checkCommission(Event event, Assignment assignment) {
        Event.CommissionRequirement requirement = event.commission();
        List<Long> members = assignment.commission();
        List<Long> eventIds = List.of(event.id());
        if (members.size() != requirement.size()) {
            violation(Reason.COMMISSION_SIZE, "В комиссии " + members.size() + " чел. вместо " + requirement.size(),
                    eventIds, members, List.of());
        }
        List<Long> notAllowed = members.stream().filter(member -> !pool.containsKey(member)
                || event.participants().contains(member) || event.commissionExcluded().contains(member)).toList();
        if (!notAllowed.isEmpty()) {
            violation(Reason.COMMISSION_MEMBER_NOT_ALLOWED,
                    "В комиссии люди не из пула, участники события или исключённые для этого события", eventIds, notAllowed, List.of());
        }
        // A named chair must be able to chair; chairRequired only decides whether the chair may be absent.
        OptionalLong chair = assignment.chair();
        if (chair.isPresent() && !(pool.containsKey(chair.getAsLong()) && pool.get(chair.getAsLong()).canChair())) {
            violation(Reason.CHAIR_NOT_ALLOWED, "У председателя нет права председательствовать",
                    eventIds, List.of(chair.getAsLong()), List.of());
        } else if (chair.isEmpty() && requirement.chairRequired()) {
            violation(Reason.CHAIR_MISSING, "У комиссии нет председателя", eventIds);
        }
    }

    private void checkPinned() {
        for (Assignment pin : problem.pinned()) {
            if (!pin.equals(assigned.get(pin.eventId()))) {
                violation(Reason.PINNED_CHANGED, "Закреплённое назначение изменено", List.of(pin.eventId()));
            }
        }
    }

    private void checkRooms() {
        Map<Long, List<Placement>> byRoom = placed.stream().filter(p -> p.physicalRoom() != null)
                .collect(Collectors.groupingBy(Placement::physicalRoom, TreeMap::new, Collectors.toList()));
        byRoom.forEach((roomId, placements) -> {
            forOverlaps(placements, (a, b) -> violation(Reason.ROOM_DOUBLE_BOOKED, "В аудитории одновременно два события",
                    List.of(a.event().id(), b.event().id()), List.of(), List.of(roomId)));
            for (Placement placement : placements) {
                if (rooms.get(roomId).busy().stream().anyMatch(placement.range()::overlaps)) {
                    violation(Reason.ROOM_HAS_CLASSES, "Событие попадает на пару в аудитории",
                            List.of(placement.event().id()), List.of(), List.of(roomId));
                }
            }
        });
    }

    /** H2, H4 and the S3 penalty. */
    private void checkPeople() {
        byPerson.forEach((personId, placements) -> {
            forOverlaps(placements, (a, b) -> violation(Reason.PERSON_DOUBLE_BOOKED, "Участники в двух событиях одновременно",
                    List.of(a.event().id(), b.event().id()), List.of(personId), List.of()));
            List<Placement> onsite = sortedByStart(placements.stream().filter(p -> p.physicalRoom() != null).toList());
            for (int i = 1; i < onsite.size(); i++) {
                Placement before = onsite.get(i - 1);
                Placement after = onsite.get(i);
                int gap = after.range().start() - before.range().end();
                if (gap >= 0 && gap < problem.roomChangeBuffer() && !before.physicalRoom().equals(after.physicalRoom())) {
                    violation(Reason.NO_TIME_TO_CHANGE_ROOM, "Между событиями в разных аудиториях меньше "
                            + grid.toMinutes(problem.roomChangeBuffer()) + " мин на переход",
                            List.of(before.event().id(), after.event().id()), List.of(personId),
                            List.of(before.physicalRoom(), after.physicalRoom()));
                }
            }
            for (Placement placement : placements) {
                checkBusy(people.get(personId), placement);
            }
        });
    }

    private void checkBusy(Person person, Placement placement) {
        SlotRange range = placement.range();
        Event.Scope scope = placement.event().scope();
        int[] coefficients = new int[range.length()];
        for (Person.Busy busy : person.busy()) {
            if (!busy.range().overlaps(range)) {
                continue;
            }
            if (BusyRules.isHard(person.role(), busy.kind(), scope)) {
                boolean classes = busy.kind() == Person.Busy.Kind.TIMETABLE;
                violation(classes ? Reason.PERSON_HAS_CLASSES : Reason.PERSON_CRITICALLY_BUSY,
                        classes ? "Событие попадает на пары участников" : "Событие попадает на критическую занятость участников",
                        List.of(placement.event().id()), List.of(person.id()), List.of());
                continue;
            }
            int coefficient = BusyRules.softCoefficient(person.role(), busy.kind(), scope);
            for (int slot = Math.max(busy.range().start(), range.start()); slot < Math.min(busy.range().end(), range.end()); slot++) {
                coefficients[slot - range.start()] = Math.max(coefficients[slot - range.start()], coefficient);
            }
        }
        int weightedSlots = Arrays.stream(coefficients).sum();
        if (weightedSlots > 0) {
            undesirableEvents.add(placement.event().id());
            undesirableMinutes += grid.toMinutes(weightedSlots);
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
                violation(Reason.DEPENDENCY_BROKEN, "Второе событие должно пройти " + gapText(dependency) + " после первого",
                        List.of(dependency.before(), dependency.after()));
            }
        }
    }

    private static String gapText(Dependency dependency) {
        if (dependency.maxGapDays().isPresent()) {
            return "через " + dependency.minGapDays() + "–" + dependency.maxGapDays().getAsInt() + " дн.";
        }
        return dependency.minGapDays() == 0 ? "сразу или позже" : "не раньше чем через " + dependency.minGapDays() + " дн.";
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
                        violation(Reason.PREPARATION_TOO_SHORT, "Между событиями меньше " + needed + " дн. на подготовку",
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
        List<Long> onsiteRooms = day.stream().map(Placement::physicalRoom).filter(room -> room != null).toList();
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

    /** S5: baseline events that are now unassigned or have another time, room, commission or chair. */
    private long moves() {
        return problem.baseline().stream().filter(base -> !base.equals(assigned.get(base.eventId()))).count();
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

    private static double standardDeviation(long[] values) {
        if (values.length == 0) {
            return 0;
        }
        double mean = (double) Arrays.stream(values).sum() / values.length;
        return Math.sqrt(Arrays.stream(values).mapToDouble(v -> (v - mean) * (v - mean)).sum() / values.length);
    }

    private void violation(Reason reason, String message, List<Long> eventIds) {
        violation(reason, message, eventIds, List.of(), List.of());
    }

    private void violation(Reason reason, String message, List<Long> eventIds, List<Long> personIds, List<Long> roomIds) {
        Violation violation = new Violation(reason, message, eventIds, personIds, roomIds);
        violations.merge(List.of(reason, violation.eventIds(), violation.roomIds(), message), violation,
                (merged, next) -> new Violation(reason, message, merged.eventIds(),
                        Stream.concat(merged.personIds().stream(), next.personIds().stream()).toList(), merged.roomIds()));
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
