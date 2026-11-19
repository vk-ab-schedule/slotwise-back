package ru.vk.itmo.scheduler.slotwise.solver.cpsat;

import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;
import ru.vk.itmo.scheduler.slotwise.domain.BusyRules;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;
import ru.vk.itmo.scheduler.slotwise.solver.cpsat.CpsatModel.Attendance;
import ru.vk.itmo.scheduler.slotwise.solver.cpsat.CpsatModel.Placement;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the S1-S4 penalty for the second phase.
 */
final class CpsatObjective {
    private static final long[] NO_BUSY_TIME = new long[0];

    private final CpModel model;
    private final Problem problem;
    private final LinearExprBuilder penalty = LinearExpr.newBuilder();
    private final Map<EventPair, Literal> roomChanges = new HashMap<>();
    private final Map<Integer, BitSet> workingDays = new LinkedHashMap<>();

    CpsatObjective(CpsatModel schedule, Runnable checkpoint) {
        model = schedule.model;
        problem = schedule.problem;
        Weights weights = problem.weights();
        int minutes = problem.grid().slotMinutes();
        if (weights.teacherGaps() > 0) {
            problem.grid().workingPeriods().forEach(period -> workingDays
                    .computeIfAbsent(problem.grid().dayOf(period.start()), ignored -> new BitSet())
                    .set(period.start(), period.end()));
        }
        for (var entry : schedule.byPerson.entrySet()) {
            checkpoint.run();
            Person person = problem.person(entry.getKey());
            List<Attendance> available = entry.getValue().stream()
                    .filter(a -> !a.starts().isEmpty() && !a.placement().rooms.isEmpty()).toList();
            if (person.role() == Person.Role.TEACHER) {
                if (weights.teacherGaps() > 0) {
                    addGaps(available, (long) minutes * weights.teacherGaps());
                }
                if (weights.teacherRoomChanges() > 0) {
                    addTransitions(available, weights.teacherRoomChanges(), checkpoint);
                }
            }
            if (weights.undesirableTime() > 0) {
                Map<Event.Scope, long[]> busyPrefixes = new EnumMap<>(Event.Scope.class);
                for (Attendance a : available) {
                    checkpoint.run();
                    long[] prefix = busyPrefixes.computeIfAbsent(a.placement().event.scope(),
                            scope -> busyPrefix(person, scope));
                    addUndesirable(a, prefix, (long) minutes * weights.undesirableTime());
                }
            }
        }
        if (weights.commissionLoadSpread() > 0 && problem.commissionPool().size() > 1) {
            addUnfairness(schedule, (long) minutes * weights.commissionLoadSpread());
        }
    }

    LinearExpr expression() {
        return penalty.build();
    }

    private void addGaps(List<Attendance> available, long weight) {
        int horizon = problem.grid().slotCount();
        workingDays.forEach((day, slots) -> {
            List<Attendance> possible = available.stream().filter(a -> a.starts().intersects(slots)).toList();
            if (possible.size() < 2) {
                return;
            }
            List<Literal> present = new ArrayList<>();
            List<IntVar> starts = new ArrayList<>();
            List<IntVar> ends = new ArrayList<>();
            for (Attendance a : possible) {
                Placement event = a.placement();
                Literal attendsToday = a.present() == event.assigned
                        ? event.days.get(day) : both(a.present(), event.days.get(day));
                present.add(attendsToday);
                IntVar start = model.newIntVar(0, horizon, "daily_start");
                IntVar end = model.newIntVar(0, horizon, "daily_end");
                model.addEquality(start, event.start).onlyEnforceIf(attendsToday);
                model.addEquality(start, horizon).onlyEnforceIf(attendsToday.not());
                model.addEquality(end, event.end()).onlyEnforceIf(attendsToday);
                model.addEquality(end, 0).onlyEnforceIf(attendsToday.not());
                starts.add(start);
                ends.add(end);
                penalty.addTerm(attendsToday, -weight * event.event.duration());
            }
            IntVar first = model.newIntVar(0, horizon, "first_daily_start");
            IntVar last = model.newIntVar(0, horizon, "last_daily_end");
            model.addMinEquality(first, starts);
            model.addMaxEquality(last, ends);
            // With no events, last - first is -horizon. The empty flag cancels that sentinel.
            penalty.addTerm(last, weight).addTerm(first, -weight)
                    .addTerm(none(present), weight * horizon);
        });
    }

    private void addUnfairness(CpsatModel schedule, long weight) {
        int horizon = problem.grid().slotCount();
        List<IntVar> workloads = new ArrayList<>();
        for (var candidate : problem.commissionPool()) {
            var duration = LinearExpr.newBuilder();
            for (Placement placement : schedule.events.values()) {
                Literal member = placement.members.get(candidate.personId());
                if (member != null) {
                    duration.addTerm(member, placement.event.duration());
                }
            }
            IntVar load = model.newIntVar(0, horizon, "commission_workload");
            model.addEquality(load, duration);
            workloads.add(load);
        }
        IntVar minimum = model.newIntVar(0, horizon, "min_commission_workload");
        IntVar maximum = model.newIntVar(0, horizon, "max_commission_workload");
        model.addMinEquality(minimum, workloads);
        model.addMaxEquality(maximum, workloads);
        penalty.addTerm(maximum, weight).addTerm(minimum, -weight);
    }

    private long[] busyPrefix(Person person, Event.Scope scope) {
        long[] prefix = null;
        for (Person.Busy busy : person.busy()) {
            int coefficient = BusyRules.softCoefficient(person.role(), busy.kind(), scope);
            if (coefficient == 0) {
                continue;
            }
            if (prefix == null) {
                prefix = new long[problem.grid().slotCount() + 1];
            }
            for (int slot = busy.range().start(); slot < busy.range().end(); slot++) {
                prefix[slot + 1] = Math.max(prefix[slot + 1], coefficient);
            }
        }
        if (prefix == null) {
            return NO_BUSY_TIME;
        }
        for (int i = 1; i < prefix.length; i++) {
            prefix[i] += prefix[i - 1];
        }
        return prefix;
    }

    private static long undesirableSlots(long[] prefix, int start, int duration) {
        return prefix[start + duration] - prefix[start];
    }

    private void addUndesirable(Attendance attendance, long[] prefix, long weight) {
        if (prefix.length == 0) {
            return;
        }
        Placement event = attendance.placement();
        int duration = event.event.duration();
        List<CostSegment> segments = new ArrayList<>();
        long highest = 0;
        for (int from = attendance.starts().nextSetBit(0); from >= 0; ) {
            long first = undesirableSlots(prefix, from, duration);
            int to = from;
            long slope = 0;
            if (attendance.starts().get(from + 1)) {
                slope = undesirableSlots(prefix, from + 1, duration) - first;
                do {
                    to++;
                } while (attendance.starts().get(to + 1)
                        && undesirableSlots(prefix, to + 1, duration) == first + slope * (to + 1L - from));
            }
            segments.add(new CostSegment(from, to, slope, first - slope * from));
            highest = Math.max(highest, Math.max(first, first + slope * (to - from)));
            from = attendance.starts().nextSetBit(to + 1);
        }
        if (highest == 0) {
            return;
        }
        if (segments.size() == 1) {
            CostSegment segment = segments.getFirst();
            if (segment.slope() == 0) {
                penalty.addTerm(attendance.present(), weight * segment.offset());
                return;
            }
        }

        IntVar cost = model.newIntVar(0, highest, "personal_busy_overlap");
        model.addEquality(cost, 0).onlyEnforceIf(attendance.present().not());
        List<Literal> chosenSegments = new ArrayList<>();
        for (CostSegment segment : segments) {
            Literal chosen = model.newBoolVar("busy_segment");
            chosenSegments.add(chosen);
            model.addLinearConstraint(event.start, segment.first(), segment.last()).onlyEnforceIf(chosen);
            model.addEquality(cost, LinearExpr.affine(event.start, segment.slope(), segment.offset())).onlyEnforceIf(chosen);
        }
        model.addEquality(CpsatModel.sum(chosenSegments), attendance.present());
        penalty.addTerm(cost, weight);
    }

    private void addTransitions(List<Attendance> available, int weight, Runnable checkpoint) {
        List<Attendance> onsite = available.stream().filter(a -> a.placement().physical()).toList();
        if (onsite.size() < 2) {
            return;
        }
        boolean canChangeRoomOnSameDay = false;
        for (int i = 0; i < onsite.size() && !canChangeRoomOnSameDay; i++) {
            Placement first = onsite.get(i).placement();
            for (int j = i + 1; j < onsite.size(); j++) {
                if (canChangeRoomOnSameDay(first, onsite.get(j).placement())) {
                    canChangeRoomOnSameDay = true;
                    break;
                }
            }
        }
        if (!canChangeRoomOnSameDay) {
            return;
        }

        // Strict time ordering makes the circuit into a chronological path through the assigned onsite events.
        var circuit = model.addCircuit();
        circuit.addArc(0, 0, none(onsite.stream().map(Attendance::present).toList()));
        for (int i = 0; i < onsite.size(); i++) {

            checkpoint.run();
            Attendance first = onsite.get(i);
            circuit.addArc(i + 1, i + 1, first.present().not());
            circuit.addArc(0, i + 1, model.newBoolVar("first_onsite"));
            circuit.addArc(i + 1, 0, model.newBoolVar("last_onsite"));

            for (int j = 0; j < onsite.size(); j++) {
                Attendance second = onsite.get(j);
                Placement a = first.placement();
                Placement b = second.placement();
                if (i == j || (long) first.starts().nextSetBit(0) + a.event.duration() >= second.starts().length()) {
                    continue;
                }
                Literal next = model.newBoolVar("next_onsite");
                circuit.addArc(i + 1, j + 1, next);
                model.addGreaterOrEqual(b.start, a.end()).onlyEnforceIf(next);
                if (canChangeRoomOnSameDay(a, b)) {
                    EventPair pair = i < j ? new EventPair(a, b) : new EventPair(b, a);
                    Literal change = roomChanges.computeIfAbsent(pair, this::roomChangeOnSameDay);
                    penalty.addTerm(both(next, change), weight);
                }
            }
        }
    }

    private static boolean canChangeRoomOnSameDay(Placement a, Placement b) {
        if (a.rooms.size() == 1 && a.rooms.keySet().equals(b.rooms.keySet())) {
            return false;
        }
        return a.days.keySet().stream().anyMatch(b.days::containsKey);
    }

    private Literal roomChangeOnSameDay(EventPair pair) {
        Literal sameDay = model.newBoolVar("same_onsite_day");
        model.addEquality(pair.first().day, pair.second().day).onlyEnforceIf(sameDay);
        model.addDifferent(pair.first().day, pair.second().day).onlyEnforceIf(sameDay.not());
        Literal differentRoom = model.newBoolVar("different_onsite_room");
        model.addDifferent(pair.first().roomCode, pair.second().roomCode).onlyEnforceIf(differentRoom);
        model.addEquality(pair.first().roomCode, pair.second().roomCode).onlyEnforceIf(differentRoom.not());
        return both(sameDay, differentRoom);
    }

    private Literal both(Literal first, Literal second) {
        Literal result = model.newBoolVar("both");
        model.addImplication(result, first);
        model.addImplication(result, second);
        model.addBoolOr(new Literal[]{first.not(), second.not(), result});
        return result;
    }

    private Literal none(List<Literal> present) {
        Literal result = model.newBoolVar("empty");
        LinearExpr count = CpsatModel.sum(present);
        model.addEquality(count, 0).onlyEnforceIf(result);
        model.addGreaterOrEqual(count, 1).onlyEnforceIf(result.not());
        return result;
    }

    private record CostSegment(int first, int last, long slope, long offset) {
    }

    private record EventPair(Placement first, Placement second) {
    }
}
