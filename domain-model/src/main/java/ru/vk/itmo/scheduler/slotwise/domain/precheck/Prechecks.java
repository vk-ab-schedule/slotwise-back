package ru.vk.itmo.scheduler.slotwise.domain.precheck;

import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fast checks of the input before a solver runs (MODEL.md, «Предпроверки»). They use only hard busy time
 * and give the basic explanation why an event stays unassigned. Non-respondents and incomplete events
 * are known only to the application, which reports them itself.
 */
public final class Prechecks {
    private final Problem problem;
    private final TimeGrid grid;
    private final BitSet working = new BitSet();
    private final Map<Long, BitSet> busyWithoutCritical = new HashMap<>();
    private final Map<Long, BitSet> busyWithCritical = new HashMap<>();
    private final List<PrecheckIssue> issues = new ArrayList<>();

    private Prechecks(Problem problem) {
        this.problem = problem;
        this.grid = problem.grid();
        grid.workingPeriods().forEach(period -> working.set(period.start(), period.end()));
    }

    public static List<PrecheckIssue> run(Problem problem) {
        return new Prechecks(problem).check();
    }

    private List<PrecheckIssue> check() {
        for (Event event : problem.events()) {
            checkCommonWindow(event);
            checkRoom(event);
            checkCommissionPool(event);
        }
        checkTeacherLoad();
        checkPreparation();
        checkFreeTime();
        return List.copyOf(issues);
    }

    private void checkCommonWindow(Event event) {
        BitSet busy = new BitSet();
        for (long participant : event.participants()) {
            busy.or(hardBusy(problem.person(participant), event.scope()));
        }
        boolean fits = grid.workingPeriods().stream().anyMatch(period -> longestFreeRun(period, busy) >= event.duration());
        if (!fits) {
            issue(PrecheckIssue.Code.NO_COMMON_WINDOW, PrecheckIssue.Severity.ERROR,
                    "У обязательных участников события " + event.id() + " нет общего окна на " + minutes(event.duration()) + " мин",
                    List.of(event.id()), event.participants(), List.of());
        }
    }

    private void checkRoom(Event event) {
        int people = event.participants().size() + event.commission().size();
        boolean online = event.format() == Event.Format.ONLINE;
        boolean fits = problem.rooms().stream().anyMatch(room -> online ? room.online() : !room.online()
                && room.capacity() >= people && room.equipment().containsAll(event.equipment()));
        if (!fits) {
            issue(PrecheckIssue.Code.NO_ROOM, PrecheckIssue.Severity.ERROR, online
                    ? "Для онлайн-события " + event.id() + " нет онлайн-площадки"
                    : "Для события " + event.id() + " нет аудитории на " + people + " чел. с нужным оборудованием",
                    List.of(event.id()), List.of(), List.of());
        }
    }

    private void checkCommissionPool(Event event) {
        Event.CommissionRequirement requirement = event.commission();
        if (requirement.size() == 0) {
            return;
        }
        List<CommissionCandidate> candidates = problem.commissionPool().stream()
                .filter(c -> !event.participants().contains(c.personId()) && !event.commissionExcluded().contains(c.personId()))
                .toList();
        if (candidates.size() < requirement.size()) {
            issue(PrecheckIssue.Code.COMMISSION_POOL_TOO_SMALL, PrecheckIssue.Severity.ERROR, "Для комиссии события "
                    + event.id() + " нужно " + requirement.size() + " чел., а в пуле подходят " + candidates.size(),
                    List.of(event.id()), List.of(), List.of());
        } else if (requirement.chairRequired() && candidates.stream().noneMatch(CommissionCandidate::canChair)) {
            issue(PrecheckIssue.Code.COMMISSION_POOL_TOO_SMALL, PrecheckIssue.Severity.ERROR,
                    "В пуле нет председателя для комиссии события " + event.id(), List.of(event.id()), List.of(), List.of());
        }
    }

    /** Required events of a teacher against the teacher's free working time; commissions are not counted. */
    private void checkTeacherLoad() {
        Map<Long, Integer> load = new TreeMap<>();
        for (Event event : problem.events()) {
            for (long participant : event.participants()) {
                if (problem.person(participant).role() == Person.Role.TEACHER) {
                    load.merge(participant, event.duration(), Integer::sum);
                }
            }
        }
        load.forEach((teacher, slots) -> {
            int free = freeWorkingSlots(hardBusy(problem.person(teacher), Event.Scope.INDIVIDUAL));
            if (slots > free) {
                issue(PrecheckIssue.Code.TEACHER_OVERLOADED, PrecheckIssue.Severity.ERROR, "У преподавателя " + teacher
                        + " событий на " + minutes(slots) + " мин, а свободного рабочего времени " + minutes(free) + " мин",
                        List.of(), List.of(teacher), List.of());
            }
        });
    }

    /**
     * Every two events with preparation differ by at least the smaller preparation in days,
     * so n of them need at least (n - 1) · min days between the first and the last working day.
     */
    private void checkPreparation() {
        // Without working time every event already has no common window.
        if (grid.workingPeriods().isEmpty()) {
            return;
        }
        int firstDay = grid.dayOf(grid.workingPeriods().getFirst().start());
        int lastDay = grid.dayOf(grid.workingPeriods().getLast().start());
        Map<Long, List<Event>> byStudent = new TreeMap<>();
        for (Event event : problem.events()) {
            if (event.preparationDays() > 0) {
                for (long participant : event.participants()) {
                    if (problem.person(participant).role() == Person.Role.STUDENT) {
                        byStudent.computeIfAbsent(participant, _ -> new ArrayList<>()).add(event);
                    }
                }
            }
        }
        byStudent.forEach((student, events) -> {
            int minPreparation = events.stream().mapToInt(Event::preparationDays).min().orElse(0);
            if ((events.size() - 1) * minPreparation > lastDay - firstDay) {
                issue(PrecheckIssue.Code.PREPARATION_DOES_NOT_FIT, PrecheckIssue.Severity.ERROR, "У студента " + student
                        + " " + events.size() + " события с подготовкой не помещаются в дни сессии",
                        events.stream().map(Event::id).toList(), List.of(student), List.of());
            }
        });
    }

    private void checkFreeTime() {
        for (Person person : problem.people()) {
            if (freeWorkingSlots(hardBusy(person, Event.Scope.INDIVIDUAL)) == 0) {
                issue(PrecheckIssue.Code.NO_FREE_TIME, PrecheckIssue.Severity.WARNING,
                        "Участник " + person.id() + " не оставил себе свободного рабочего времени",
                        List.of(), List.of(person.id()), List.of());
            }
        }
    }

    /**
     * Slots a person cannot take: classes always, critical time for teachers always and for students
     * only in individual events (H4).
     */
    private BitSet hardBusy(Person person, Event.Scope scope) {
        boolean withCritical = person.role() == Person.Role.TEACHER || scope == Event.Scope.INDIVIDUAL;
        Map<Long, BitSet> cache = withCritical ? busyWithCritical : busyWithoutCritical;
        return cache.computeIfAbsent(person.id(), _ -> {
            BitSet busy = new BitSet();
            for (Person.Busy b : person.busy()) {
                if (b.kind() == Person.Busy.Kind.TIMETABLE || withCritical && b.kind() == Person.Busy.Kind.CRITICAL) {
                    busy.set(b.range().start(), b.range().end());
                }
            }
            return busy;
        });
    }

    private static int longestFreeRun(SlotRange period, BitSet busy) {
        int longest = 0;
        int run = 0;
        for (int slot = period.start(); slot < period.end(); slot++) {
            run = busy.get(slot) ? 0 : run + 1;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    private int freeWorkingSlots(BitSet busy) {
        BitSet free = (BitSet) working.clone();
        free.andNot(busy);
        return free.cardinality();
    }

    private int minutes(int slots) {
        return grid.toMinutes(slots);
    }

    private void issue(PrecheckIssue.Code code, PrecheckIssue.Severity severity, String message, List<Long> eventIds,
                       List<Long> personIds, List<Long> roomIds) {
        issues.add(new PrecheckIssue(code, severity, message, eventIds, personIds, roomIds));
    }
}
