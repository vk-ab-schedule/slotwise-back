package ru.vk.itmo.scheduler.slotwise.solver.greedy;

import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.BusyRules;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

final class PlacementSearch {
    private static final long FORBIDDEN = Long.MAX_VALUE;

    private final Problem problem;
    private final TimeGrid grid;
    private final SplittableRandom random;
    private final BooleanSupplier stopped;
    private final Map<Long, Calendar> calendars = new HashMap<>();
    private final Map<Long, BitSet> roomBusy = new HashMap<>();
    private final Map<Long, List<Dependency>> dependencies = new HashMap<>();
    private final Map<Long, Placed> placed = new HashMap<>();
    private final List<Assignment> result = new ArrayList<>();
    private final int[] workloads;

    PlacementSearch(Problem problem, long seed, BooleanSupplier stopped) {
        this.problem = problem;
        grid = problem.grid();
        random = new SplittableRandom(seed);
        this.stopped = stopped;
        workloads = new int[problem.commissionPool().size()];
    }

    List<Assignment> run() {
        result.addAll(problem.pinned());
        if (stopped.getAsBoolean()) {
            return result;
        }
        problem.people().forEach(person -> calendars.put(person.id(), new Calendar(person, grid.slotCount())));
        problem.rooms().forEach(room -> {
            var busy = new BitSet();
            room.busy().forEach(interval -> busy.set(interval.start(), interval.end()));
            roomBusy.put(room.id(), busy);
        });
        for (Dependency dependency : problem.dependencies()) {
            dependencies.computeIfAbsent(dependency.before(), key -> new ArrayList<>()).add(dependency);
            dependencies.computeIfAbsent(dependency.after(), key -> new ArrayList<>()).add(dependency);
        }
        problem.pinned().forEach(this::occupy);

        List<EventOptions> order = new ArrayList<>();
        for (Event event : problem.events()) {
            if (stopped.getAsBoolean()) {
                return result;
            }
            if (!placed.containsKey(event.id())) {
                order.add(options(event));
            }
        }
        order.sort(Comparator.comparingLong(EventOptions::count)
                .thenComparing(Comparator.comparingLong((EventOptions item) ->
                        (long) item.people().length + item.event().commission().size()).reversed())
                .thenComparingLong(EventOptions::tie));
        for (EventOptions item : order) {
            if (stopped.getAsBoolean()) {
                break;
            }
            var assignment = best(item);
            if (assignment != null) {
                result.add(assignment);
                occupy(assignment);
            }
        }
        return result;
    }

    private EventOptions options(Event event) {
        Calendar[] people = event.participants().stream().map(calendars::get).toArray(Calendar[]::new);
        Room[] rooms = problem.rooms().stream().filter(room -> fits(event, room)).toArray(Room[]::new);
        var excluded = new HashSet<>(event.participants());
        excluded.addAll(event.commissionExcluded());
        var candidates = new ArrayList<Eligible>();
        var pool = problem.commissionPool();
        if (event.commission().size() > 0) {
            for (int i = 0; i < pool.size(); i++) {
                var candidate = pool.get(i);
                if (!excluded.contains(candidate.personId())) {
                    candidates.add(new Eligible(i, candidate, calendars.get(candidate.personId())));
                }
            }
        }
        for (int i = candidates.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Eligible candidate = candidates.get(i);
            candidates.set(i, candidates.get(j));
            candidates.set(j, candidate);
        }
        var available = new BitSet(grid.slotCount());
        long count = 0;
        for (SlotRange window : grid.workingPeriods()) {
            for (int start = window.start(); start <= window.end() - event.duration(); start++) {
                if (stopped.getAsBoolean()) {
                    return new EventOptions(event, people, rooms, candidates, available, count, random.nextLong());
                }
                int end = start + event.duration();
                if (fitsPeople(people, event, start, end) && fitsCommission(candidates, event, start, end)) {
                    long roomsFree = 0;
                    for (Room room : rooms) {
                        if (free(roomBusy.get(room.id()), start, end)
                                && fitsPinned(event, people, candidates, start, end, room)) {
                            roomsFree++;
                        }
                    }
                    if (roomsFree > 0) {
                        available.set(start);
                        count += roomsFree;
                    }
                }
            }
        }
        return new EventOptions(event, people, rooms, candidates, available, count, random.nextLong());
    }

    private boolean fitsPinned(Event event, Calendar[] people, List<Eligible> candidates, int start, int end, Room room) {
        if (placed.isEmpty()) {
            return true;
        }
        int day = grid.dayOf(start);
        if (!dependenciesAllow(event, start, end, day)) {
            return false;
        }
        for (var person : people) {
            if (cost(person, event, start, end, day, room) == FORBIDDEN) {
                return false;
            }
        }
        int remaining = event.commission().size();
        if (remaining == 0) {
            return true;
        }
        boolean chair = !event.commission().chairRequired();
        for (var candidate : candidates) {
            if (cost(candidate.calendar(), event, start, end, day, room) != FORBIDDEN) {
                remaining--;
                chair |= candidate.member().canChair();
                if (remaining <= 0 && chair) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fits(Event event, Room room) {
        return event.format() == Event.Format.ONLINE ? room.online()
                : !room.online() && room.capacity() >= (long) event.participants().size() + event.commission().size()
                        && room.equipment().containsAll(event.equipment());
    }

    private static boolean fitsPeople(Calendar[] people, Event event, int start, int end) {
        for (var calendar : people) {
            if (!free(calendar.hard(event.scope()), start, end)) {
                return false;
            }
        }
        return true;
    }

    private static boolean fitsCommission(List<Eligible> candidates, Event event, int start, int end) {
        if (event.commission().size() == 0) {
            return true;
        }
        int free = 0;
        boolean chair = !event.commission().chairRequired();
        for (var candidate : candidates) {
            if (free(candidate.calendar().hard(event.scope()), start, end)) {
                free++;
                chair |= candidate.member().canChair();
                if (chair && free >= event.commission().size()) {
                    return true;
                }
            }
        }
        return false;
    }

    private Assignment best(EventOptions item) {
        Event event = item.event();
        long bestCost = FORBIDDEN;
        Assignment best = null;
        int ties = 0;
        var panel = new CommissionSelection(item);
        for (int start = item.starts().nextSetBit(0); start >= 0; start = item.starts().nextSetBit(start + 1)) {
            if (stopped.getAsBoolean()) {
                break;
            }
            int end = start + event.duration();
            int day = grid.dayOf(start);
            if (!dependenciesAllow(event, start, end, day)) {
                continue;
            }
            for (Room room : item.rooms()) {
                if (!free(roomBusy.get(room.id()), start, end)) {
                    continue;
                }
                long cost = participantCost(item, start, end, day, room);
                if (cost == FORBIDDEN || !panel.choose(start, end, day, room)) {
                    continue;
                }
                cost += panel.cost;
                if (cost < bestCost) {
                    bestCost = cost;
                    ties = 0;
                }
                if (cost == bestCost && random.nextInt(++ties) == 0) {
                    best = new Assignment(event.id(), start, room.id(), panel.members(), panel.chair());
                }
            }
        }
        return best;
    }

    private boolean dependenciesAllow(Event event, int start, int end, int day) {
        for (Dependency dependency : dependencies.getOrDefault(event.id(), List.of())) {
            boolean first = dependency.before() == event.id();
            Placed other = placed.get(first ? dependency.after() : dependency.before());
            if (other == null) {
                continue;
            }
            int gap = first ? other.day() - day : day - other.day();
            if ((first ? end > other.start() : other.end() > start) || gap < dependency.minGapDays()
                    || dependency.maxGapDays().isPresent() && gap > dependency.maxGapDays().getAsInt()) {
                return false;
            }
        }
        return true;
    }

    private long participantCost(EventOptions item, int start, int end, int day, Room room) {
        long total = 0;
        for (Calendar calendar : item.people()) {
            long cost = cost(calendar, item.event(), start, end, day, room);
            if (cost == FORBIDDEN) {
                return FORBIDDEN;
            }
            total += cost;
        }
        return total;
    }

    private long cost(Calendar calendar, Event event, int start, int end, int day, Room room) {
        if (!free(calendar.hard(event.scope()), start, end)) {
            return FORBIDDEN;
        }
        boolean teacher = calendar.person.role() == Person.Role.TEACHER;
        int earliest = Integer.MAX_VALUE;
        int latest = -1;
        Placed previous = null;
        Placed next = null;
        for (Placed other : calendar.assignments) {
            if (start < other.end() && other.start() < end) {
                return FORBIDDEN;
            }
            if (!teacher && event.preparationDays() > 0 && other.preparation() > 0
                    && Math.abs(day - other.day()) < Math.max(event.preparationDays(), other.preparation())) {
                return FORBIDDEN;
            }
            if (other.day() == day) {
                earliest = Math.min(earliest, other.start());
                latest = Math.max(latest, other.end());
            }
            if (!room.online() && !other.room().online()) {
                if (other.end() <= start && (previous == null || other.end() > previous.end())) {
                    previous = other;
                }
                if (other.start() >= end && (next == null || other.start() < next.start())) {
                    next = other;
                }
            }
        }
        if ((previous != null && previous.room().id() != room.id() && start - previous.end() < problem.roomChangeBuffer())
                || (next != null && next.room().id() != room.id() && next.start() - end < problem.roomChangeBuffer())) {
            return FORBIDDEN;
        }
        long total = (long) calendar.soft(event.scope(), start, end) * grid.slotMinutes() * problem.weights().undesirableTime();
        if (teacher && latest >= 0) {
            int gapDelta = Math.max(latest, end) - Math.min(earliest, start) - (latest - earliest) - (end - start);
            total += (long) gapDelta * grid.slotMinutes() * problem.weights().teacherGaps();
            if (!room.online()) {
                boolean from = previous != null && previous.day() == day;
                boolean to = next != null && next.day() == day;
                int switches = (from && previous.room().id() != room.id() ? 1 : 0)
                        + (to && next.room().id() != room.id() ? 1 : 0)
                        - (from && to && previous.room().id() != next.room().id() ? 1 : 0);
                total += (long) switches * problem.weights().teacherRoomChanges();
            }
        }
        return total;
    }

    private void occupy(Assignment assignment) {
        var event = problem.event(assignment.eventId());
        int start = assignment.start();
        var room = problem.room(assignment.roomId());
        var item = new Placed(start, start + event.duration(), grid.dayOf(start), room, event.preparationDays());
        placed.put(event.id(), item);
        if (!room.online()) {
            roomBusy.get(room.id()).set(start, item.end());
        }
        event.participants().forEach(person -> calendars.get(person).assignments.add(item));
        for (long member : assignment.commission()) {
            calendars.get(member).assignments.add(item);
            var pool = problem.commissionPool();
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).personId() == member) {
                    workloads[i] += event.duration();
                    break;
                }
            }
        }
    }

    private static boolean free(BitSet busy, int start, int end) {
        int first = busy.nextSetBit(start);
        return first < 0 || first >= end;
    }

    private record Placed(int start, int end, int day, Room room, int preparation) {
    }

    private record Eligible(int poolIndex, CommissionCandidate member, Calendar calendar) {
    }

    private record EventOptions(Event event, Calendar[] people, Room[] rooms, List<Eligible> candidates,
                                BitSet starts, long count, long tie) {
    }

    private static final class Calendar {
        private final Person person;
        private final BitSet[] hard = new BitSet[Event.Scope.values().length];
        private final int[][] softPrefix = new int[Event.Scope.values().length][];
        private final List<Placed> assignments = new ArrayList<>();

        Calendar(Person person, int slots) {
            this.person = person;
            for (Event.Scope scope : Event.Scope.values()) {
                var blocked = new BitSet();
                int[] prefix = new int[slots + 1];
                for (var busy : person.busy()) {
                    if (BusyRules.isHard(person.role(), busy.kind(), scope)) {
                        blocked.set(busy.range().start(), busy.range().end());
                    } else {
                        int weight = BusyRules.softCoefficient(person.role(), busy.kind(), scope);
                        for (int slot = busy.range().start(); slot < busy.range().end(); slot++) {
                            prefix[slot + 1] = Math.max(prefix[slot + 1], weight);
                        }
                    }
                }
                for (int i = 1; i < prefix.length; i++) {
                    prefix[i] += prefix[i - 1];
                }
                hard[scope.ordinal()] = blocked;
                softPrefix[scope.ordinal()] = prefix;
            }
        }

        BitSet hard(Event.Scope scope) {
            return hard[scope.ordinal()];
        }

        int soft(Event.Scope scope, int start, int end) {
            return softPrefix[scope.ordinal()][end] - softPrefix[scope.ordinal()][start];
        }
    }

    /**
     * For a fixed slot and room, minimize the panel's local penalty exactly. With lower and upper
     * limits on final workloads, below-minimum members must join and above-maximum choices are
     * excluded. The remaining seats go to the cheapest eligible members, with a chair if needed.
     */
    private final class CommissionSelection {
        private final EventOptions options;
        private final long[] costs;
        private final int[] byCost;
        private final int[] candidateByPool = new int[workloads.length];
        private final boolean[] selected = new boolean[workloads.length];
        private final boolean[] trial = new boolean[workloads.length];
        private final int[] lowerBounds;
        private final int[] upperBounds;
        private final int startingSpread;
        private long cost;
        private OptionalLong chair;

        CommissionSelection(EventOptions options) {
            this.options = options;
            costs = new long[options.candidates().size()];
            byCost = new int[costs.length];
            Arrays.fill(candidateByPool, -1);
            for (int i = 0; i < costs.length; i++) {
                candidateByPool[options.candidates().get(i).poolIndex()] = i;
            }
            int[] loads = workloads.clone();
            Arrays.sort(loads);
            if (loads.length == 0 || options.event().commission().size() == 0) {
                lowerBounds = upperBounds = new int[0];
                startingSpread = 0;
                return;
            }
            int maximum = loads[loads.length - 1];
            int minimum = loads[0];
            int duration = options.event().duration();
            int seats = options.event().commission().size();
            startingSpread = maximum - minimum;
            // At most k existing workloads can be raised, and the smallest increases by at most duration.
            var minima = new int[Math.min(loads.length, seats + 1) + 1];
            int count = 0;
            for (int load : loads) {
                if (count == minima.length - 1 || load > minimum + duration) {
                    break;
                }
                minima[count++] = load;
            }
            minima[count++] = minimum + duration;
            lowerBounds = Arrays.stream(minima, 0, count).distinct().toArray();
            upperBounds = IntStream.concat(IntStream.of(maximum),
                    Arrays.stream(loads).map(load -> Math.max(maximum, load + duration))).distinct().toArray();
        }

        boolean choose(int start, int end, int day, Room room) {
            cost = 0;
            chair = OptionalLong.empty();
            var event = options.event();
            int seats = event.commission().size();
            if (seats == 0) {
                return true;
            }
            int eligible = 0;
            boolean hasChair = false;
            for (int i = 0; i < costs.length; i++) {
                var candidate = options.candidates().get(i);
                costs[i] = PlacementSearch.this.cost(candidate.calendar(), event, start, end, day, room);
                if (costs[i] == FORBIDDEN) {
                    continue;
                }
                hasChair |= candidate.member().canChair();
                int at = eligible++;
                while (at > 0 && costs[byCost[at - 1]] > costs[i]) {
                    byCost[at] = byCost[at - 1];
                    at--;
                }
                byCost[at] = i;
            }
            boolean chairRequired = event.commission().chairRequired();
            if (eligible < seats || chairRequired && !hasChair) {
                return false;
            }

            long personalLowerBound = 0;
            for (int i = 0; i < seats; i++) {
                personalLowerBound += costs[byCost[i]];
            }
            long spreadWeight = (long) grid.slotMinutes() * problem.weights().commissionLoadSpread();
            long bestCost = FORBIDDEN;
            for (int upper : upperBounds) {
                for (int minIndex = lowerBounds.length - 1; minIndex >= 0; minIndex--) {
                    int lower = lowerBounds[minIndex];
                    if (lower > upper) {
                        continue;
                    }
                    long spreadCost = (upper - lower - startingSpread) * spreadWeight;
                    if (personalLowerBound + spreadCost >= bestCost) {
                        break;
                    }
                    long personal = select(lower, upper, eligible, seats, chairRequired);
                    if (personal != FORBIDDEN && personal + spreadCost < bestCost) {
                        bestCost = personal + spreadCost;
                        System.arraycopy(trial, 0, selected, 0, trial.length);
                    }
                }
            }
            if (bestCost == FORBIDDEN) {
                return false;
            }
            cost = bestCost;
            if (chairRequired) {
                for (var candidate : options.candidates()) {
                    if (selected[candidate.poolIndex()] && candidate.member().canChair()) {
                        chair = OptionalLong.of(candidate.member().personId());
                        break;
                    }
                }
            }
            return true;
        }

        private long select(int lower, int upper, int eligible, int seats, boolean chairRequired) {
            Arrays.fill(trial, false);
            long total = 0;
            int duration = options.event().duration();
            int remaining = seats;
            boolean chairFound = !chairRequired;
            for (int i = 0; i < workloads.length; i++) {
                if (workloads[i] >= lower) {
                    continue;
                }
                int candidate = candidateByPool[i];
                int updated = workloads[i] + duration;
                if (candidate < 0 || costs[candidate] == FORBIDDEN || updated < lower || updated > upper || remaining == 0) {
                    return FORBIDDEN;
                }
                trial[i] = true;
                remaining--;
                total += costs[candidate];
                chairFound |= options.candidates().get(candidate).member().canChair();
            }

            for (int i = 0; i < eligible && remaining > 0; i++) {
                int index = byCost[i];
                var candidate = options.candidates().get(index);
                int poolIndex = candidate.poolIndex();
                if (trial[poolIndex] || workloads[poolIndex] + duration > upper
                        || remaining == 1 && !chairFound && !candidate.member().canChair()) {
                    continue;
                }
                trial[poolIndex] = true;
                remaining--;
                total += costs[index];
                chairFound |= candidate.member().canChair();
            }
            return remaining == 0 && chairFound ? total : FORBIDDEN;
        }

        List<Long> members() {
            if (options.event().commission().size() == 0) {
                return List.of();
            }
            var members = new ArrayList<Long>();
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) {
                    members.add(problem.commissionPool().get(i).personId());
                }
            }
            return members;
        }

        OptionalLong chair() {
            return chair;
        }
    }
}
