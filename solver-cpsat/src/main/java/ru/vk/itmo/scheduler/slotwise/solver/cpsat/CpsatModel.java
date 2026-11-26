package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.IntervalVar;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.Literal;
import com.google.ortools.util.Domain;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.BusyRules;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

final class CpsatModel {

    final CpModel model = new CpModel();
    final Problem problem;
    final Map<Long, Placement> events = new LinkedHashMap<>();
    final Map<Long, List<Attendance>> byPerson = new LinkedHashMap<>();
    final LinearExpr assignedCount;

    private final Map<Long, List<IntervalVar>> roomIntervals = new LinkedHashMap<>();
    private final Map<Long, List<IntervalVar>> personIntervals = new LinkedHashMap<>();
    private final Map<Long, Integer> roomCodes = new LinkedHashMap<>();

    CpsatModel(Problem problem, Runnable checkpoint) {
        this.problem = problem;
        for (Room room : problem.rooms()) {
            roomCodes.put(room.id(), roomCodes.size() + 1);
        }
        for (Event event : problem.events()) {
            checkpoint.run();
            Placement placement = new Placement(event);
            events.put(event.id(), placement);
            for (long id : event.participants()) {
                excludeHardBusy(placement.starts, problem.person(id), event);
            }
            addRooms(placement);
            addPeople(placement);
            addDays(placement);
            problem.pinned(event.id()).ifPresent(pin -> pin(placement, pin));
        }

        roomIntervals.values().forEach(model::addNoOverlap);
        personIntervals.values().forEach(model::addNoOverlap);
        addRoomChangeBuffer(checkpoint);

        for (Dependency dependency : problem.dependencies()) {
            Placement first = events.get(dependency.before());
            Placement second = events.get(dependency.after());
            Literal[] both = {first.assigned, second.assigned};
            model.addGreaterOrEqual(second.start, first.end()).onlyEnforceIf(both);
            LinearExpr days = difference(second.day, first.day);
            model.addGreaterOrEqual(days, dependency.minGapDays()).onlyEnforceIf(both);
            dependency.maxGapDays().ifPresent(max -> model.addLessOrEqual(days, max).onlyEnforceIf(both));
        }
        addPreparation(checkpoint);
        assignedCount = LinearExpr.sum(events.values().stream().map(p -> p.assigned).toArray(Literal[]::new));
    }

    private void addRooms(Placement placement) {
        Event event = placement.event;
        var code = LinearExpr.newBuilder();
        BitSet possibleStarts = new BitSet();
        for (Room room : problem.rooms()) {
            if (room.online() != (event.format() == Event.Format.ONLINE)
                    || !room.online() && (room.capacity() < (long) event.participants().size() + event.commission().size()
                    || !room.equipment().containsAll(event.equipment()))) {
                continue;
            }
            BitSet starts = (BitSet) placement.starts.clone();
            room.busy().forEach(busy -> excludeBusy(starts, busy.start(), busy.end(), event.duration()));
            if (starts.isEmpty()) {
                continue;
            }
            BoolVar chosen = model.newBoolVar("room_" + event.id() + "_" + room.id());
            placement.rooms.put(room.id(), chosen);
            possibleStarts.or(starts);
            constrainStart(placement, starts, chosen);
            code.addTerm(chosen, roomCodes.get(room.id()));
            if (!room.online()) {
                roomIntervals.computeIfAbsent(room.id(), ignored -> new ArrayList<>())
                        .add(placement.interval(chosen, "room"));
            }
        }
        model.addEquality(sum(placement.rooms.values().stream().toList()), placement.assigned);
        model.addEquality(placement.roomCode, code);
        placement.starts.and(possibleStarts);
    }

    private void addPeople(Placement placement) {
        Event event = placement.event;
        if (placement.starts.isEmpty()) {
            return;
        }
        for (long id : event.participants()) {
            attend(placement, id, placement.assigned, placement.starts);
        }
        if (event.commission().size() == 0) {
            return;
        }
        for (CommissionCandidate candidate : problem.commissionPool()) {
            long id = candidate.personId();
            if (event.participants().contains(id) || event.commissionExcluded().contains(id)) {
                continue;
            }
            BitSet starts = (BitSet) placement.starts.clone();
            excludeHardBusy(starts, problem.person(id), event);
            if (starts.isEmpty()) {
                continue;
            }
            BoolVar member = model.newBoolVar("member_" + event.id() + "_" + id);
            placement.members.put(id, member);
            model.addImplication(member, placement.assigned);
            constrainStart(placement, starts, member);
            attend(placement, id, member, starts);
            if (candidate.canChair()) {
                BoolVar chair = model.newBoolVar("chair_" + event.id() + "_" + id);
                placement.chairs.put(id, chair);
                model.addImplication(chair, member);
            }
        }
        model.addEquality(sum(placement.members.values().stream().toList()),
                LinearExpr.term(placement.assigned, event.commission().size()));
        LinearExpr chairs = sum(placement.chairs.values().stream().toList());
        if (event.commission().chairRequired()) {
            model.addEquality(chairs, placement.assigned);
        } else if (!placement.chairs.isEmpty()) {
            model.addLessOrEqual(chairs, placement.assigned);
        }
    }

    private static void excludeHardBusy(BitSet starts, Person person, Event event) {
        for (Person.Busy busy : person.busy()) {
            if (BusyRules.isHard(person.role(), busy.kind(), event.scope())) {
                excludeBusy(starts, busy.range().start(), busy.range().end(), event.duration());
            }
        }
    }

    private void attend(Placement placement, long id, Literal present, BitSet starts) {
        byPerson.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Attendance(placement, present, starts));
        personIntervals.computeIfAbsent(id, ignored -> new ArrayList<>())
                .add(placement.interval(present, "person"));
    }

    private void addDays(Placement placement) {
        Map<Integer, BitSet> days = new LinkedHashMap<>();
        for (int slot = placement.starts.nextSetBit(0); slot >= 0; slot = placement.starts.nextSetBit(slot + 1)) {
            days.computeIfAbsent(problem.grid().dayOf(slot), ignored -> new BitSet()).set(slot);
        }
        var dayNumber = LinearExpr.newBuilder();
        days.forEach((day, starts) -> {
            Literal onDay = model.newBoolVar("day_" + placement.event.id() + "_" + day);
            placement.days.put(day, onDay);
            constrainStart(placement, starts, onDay);
            dayNumber.addTerm(onDay, day);
        });
        model.addEquality(sum(placement.days.values().stream().toList()), placement.assigned);
        model.addEquality(placement.day, dayNumber);
    }

    private void addRoomChangeBuffer(Runnable checkpoint) {
        int buffer = problem.roomChangeBuffer();
        if (buffer == 0) {
            return;
        }
        Map<EventPair, Travel> pairs = new LinkedHashMap<>();
        for (List<Attendance> attended : byPerson.values()) {
            checkpoint.run();
            List<Attendance> physical = attended.stream().filter(a -> a.placement.physical() && !a.starts.isEmpty()).toList();
            for (int i = 0; i < physical.size(); i++) {
                checkpoint.run();
                for (int j = i + 1; j < physical.size(); j++) {
                    Attendance first = physical.get(i);
                    Attendance second = physical.get(j);
                    Placement a = first.placement;
                    Placement b = second.placement;
                    if (a.rooms.isEmpty() || b.rooms.isEmpty() || sameOnlyRoom(a, b)
                            || alwaysFarEnough(first, second, buffer) || alwaysFarEnough(second, first, buffer)) {
                        continue;
                    }
                    Travel travel = pairs.computeIfAbsent(new EventPair(a, b), pair -> travel(a, b, buffer));
                    if (!travel.mandatory) {
                        model.addBoolOr(new Literal[]{first.present.not(), second.present.not(), travel.sameRoom,
                                travel.firstBefore, travel.secondBefore});
                        travel.mandatory = first.present == a.assigned && second.present == b.assigned;
                    }
                }
            }
        }
    }

    private Travel travel(Placement first, Placement second, int buffer) {
        Literal sameRoom = model.newBoolVar("same_room");
        model.addEquality(first.roomCode, second.roomCode).onlyEnforceIf(sameRoom);
        Literal firstBefore = model.newBoolVar("travel_forward");
        Literal secondBefore = model.newBoolVar("travel_back");
        model.addGreaterOrEqual(difference(second.start, first.start), (long) first.event.duration() + buffer)
                .onlyEnforceIf(firstBefore);
        model.addGreaterOrEqual(difference(first.start, second.start), (long) second.event.duration() + buffer)
                .onlyEnforceIf(secondBefore);
        return new Travel(sameRoom, firstBefore, secondBefore);
    }

    private static boolean sameOnlyRoom(Placement a, Placement b) {
        return a.rooms.size() == 1 && a.rooms.keySet().equals(b.rooms.keySet());
    }

    private static boolean alwaysFarEnough(Attendance first, Attendance second, int buffer) {
        return (long) first.starts.length() - 1 + first.placement.event.duration() + buffer <= second.starts.nextSetBit(0);
    }

    private void addPreparation(Runnable checkpoint) {
        var seen = new HashSet<EventPair>();
        byPerson.forEach((id, attended) -> {
            checkpoint.run();
            if (problem.person(id).role() != Person.Role.STUDENT) {
                return;
            }
            List<Placement> exams = attended.stream().map(Attendance::placement)
                    .filter(p -> p.event.preparationDays() > 0).toList();
            for (int i = 0; i < exams.size(); i++) {
                for (int j = i + 1; j < exams.size(); j++) {
                    Placement a = exams.get(i);
                    Placement b = exams.get(j);
                    if (!seen.add(new EventPair(a, b))) {
                        continue;
                    }
                    int minimum = Math.max(a.event.preparationDays(), b.event.preparationDays());
                    Literal first = model.newBoolVar("preparation_order");
                    model.addGreaterOrEqual(difference(b.day, a.day), minimum)
                            .onlyEnforceIf(new Literal[]{a.assigned, b.assigned, first});
                    model.addGreaterOrEqual(difference(a.day, b.day), minimum)
                            .onlyEnforceIf(new Literal[]{a.assigned, b.assigned, first.not()});
                }
            }
        });
    }

    private void pin(Placement placement, Assignment pin) {
        model.addEquality(placement.assigned, 1);
        model.addEquality(placement.start, pin.start());
        if (!placement.rooms.containsKey(pin.roomId())
                || !placement.members.keySet().containsAll(pin.commission())
                || pin.chair().isPresent() && !placement.chairs.containsKey(pin.chair().getAsLong())) {
            model.addEquality(placement.assigned, 0);
        }
        placement.rooms.forEach((id, chosen) -> model.addEquality(chosen, id == pin.roomId() ? 1 : 0));
        placement.members.forEach((id, chosen) -> model.addEquality(chosen, pin.commission().contains(id) ? 1 : 0));
        placement.chairs.forEach((id, chosen) -> model.addEquality(chosen,
                pin.chair().isPresent() && pin.chair().getAsLong() == id ? 1 : 0));
    }

    List<Assignment> assignments(SolutionValues values) {
        List<Assignment> result = new ArrayList<>();
        events.values().forEach(p -> {
            if (values.selected(p.assigned)) {
                long roomId = p.rooms.entrySet().stream().filter(e -> values.selected(e.getValue()))
                        .mapToLong(Map.Entry::getKey).findFirst().orElseThrow();
                List<Long> members = p.members.entrySet().stream().filter(e -> values.selected(e.getValue()))
                        .map(Map.Entry::getKey).toList();
                OptionalLong chair = p.chairs.entrySet().stream().filter(e -> values.selected(e.getValue()))
                        .mapToLong(Map.Entry::getKey).findFirst();
                result.add(new Assignment(p.event.id(), Math.toIntExact(values.value(p.start)), roomId, members, chair));
            }
        });
        return result;
    }

    /**
     * Seeds the search from an already validated schedule.
     */
    void hint(List<Assignment> assignments) {
        model.clearHints();
        Map<Long, Assignment> byEvent = new LinkedHashMap<>();
        for (Assignment a : assignments) {
            byEvent.put(a.eventId(), a);
        }
        events.values().forEach(p -> {
            Assignment a = byEvent.get(p.event.id());
            model.addHint(p.assigned, a != null ? 1 : 0);
            model.addHint(p.start, a != null ? a.start() : 0);
            p.rooms.forEach((id, flag) -> model.addHint(flag, a != null && id == a.roomId() ? 1 : 0));
            p.members.forEach((id, flag) -> model.addHint(flag, a != null && a.commission().contains(id) ? 1 : 0));
            p.chairs.forEach((id, flag) -> model.addHint(flag,
                    a != null && a.chair().isPresent() && a.chair().getAsLong() == id ? 1 : 0));
        });
    }

    void constrainStart(Placement placement, BitSet starts, Literal when) {
        if (starts.isEmpty()) {
            model.addEquality(when, 0);
            return;
        }
        List<long[]> intervals = new ArrayList<>();
        for (int start = starts.nextSetBit(0); start >= 0; ) {
            int end = starts.nextClearBit(start);
            intervals.add(new long[]{start, end - 1L});
            start = starts.nextSetBit(end);
        }
        model.addLinearExpressionInDomain(placement.start, Domain.fromIntervals(intervals.toArray(long[][]::new)))
                .onlyEnforceIf(when);
    }

    private static void excludeBusy(BitSet starts, int from, int to, int duration) {
        starts.clear(Math.max(0, from - duration + 1), to);
    }

    static LinearExpr difference(IntVar a, IntVar b) {
        return LinearExpr.weightedSum(new IntVar[]{a, b}, new long[]{1, -1});
    }

    static LinearExpr sum(List<? extends Literal> terms) {
        return LinearExpr.sum(terms.toArray(Literal[]::new));
    }

    interface SolutionValues {
        long value(LinearArgument expr);

        default boolean selected(Literal flag) {
            return value(flag) != 0;
        }
    }

    final class Placement {
        final Event event;
        final BoolVar assigned;
        final IntVar start;
        final IntVar day;
        final IntVar roomCode;
        final BitSet starts = new BitSet();
        final Map<Long, BoolVar> rooms = new LinkedHashMap<>();
        final Map<Long, BoolVar> members = new LinkedHashMap<>();
        final Map<Long, BoolVar> chairs = new LinkedHashMap<>();
        final Map<Integer, Literal> days = new LinkedHashMap<>();

        Placement(Event event) {
            this.event = event;
            String key = Long.toString(event.id());
            assigned = model.newBoolVar("assigned_" + key);
            // With no candidate starts, the room constraints force the event to remain unassigned.
            start = model.newIntVar(0, Math.max(0, problem.grid().slotCount() - event.duration()), "start_" + key);
            day = model.newIntVar(0, problem.grid().dayOf(problem.grid().slotCount() - 1), "date_" + key);
            roomCode = model.newIntVar(0, roomCodes.size(), "venue_" + key);
            problem.grid().workingPeriods().forEach(window -> {
                if (window.length() >= event.duration()) {
                    starts.set(window.start(), window.end() - event.duration() + 1);
                }
            });
            model.addEquality(start, 0).onlyEnforceIf(assigned.not());
        }

        LinearExpr end() {
            return LinearExpr.affine(start, 1, event.duration());
        }

        IntervalVar interval(Literal present, String kind) {
            return model.newOptionalFixedSizeIntervalVar(start, event.duration(), present, kind + "_" + event.id());
        }

        boolean physical() {
            return event.format() != Event.Format.ONLINE;
        }
    }

    record Attendance(Placement placement, Literal present, BitSet starts) {
    }

    private record EventPair(Placement first, Placement second) {
    }

    private static final class Travel {
        final Literal sameRoom;
        final Literal firstBefore;
        final Literal secondBefore;
        boolean mandatory;

        Travel(Literal sameRoom, Literal firstBefore, Literal secondBefore) {
            this.sameRoom = sameRoom;
            this.firstBefore = firstBefore;
            this.secondBefore = secondBefore;
        }
    }
}
