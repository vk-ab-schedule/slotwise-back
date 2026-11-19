package ru.vk.itmo.scheduler.slotwise.domain.precheck;

import ru.vk.itmo.scheduler.slotwise.domain.BusyRules;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.PrecheckIssue.Code;
import ru.vk.itmo.scheduler.slotwise.domain.precheck.PrecheckIssue.Severity;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fast checks of the input before a solver runs (MODEL.md, «Предпроверки»). They use only hard busy time
 * ({@link BusyRules}) and give the basic explanation why an event stays unassigned. Non-respondents and
 * incomplete events are known only to the application, which reports them itself.
 */
public final class Prechecks {
    private final Problem problem;
    private final TimeGrid grid;
    private final BitSet working = new BitSet();
    private final List<Integer> workingDays;
    private final int longestWorkingPeriod;
    private final Map<List<Object>, BitSet> hardBusy = new HashMap<>();
    private final List<PrecheckIssue> issues = new ArrayList<>();

    private Prechecks(Problem problem) {
        this.problem = problem;
        this.grid = problem.grid();
        grid.workingPeriods().forEach(period -> working.set(period.start(), period.end()));
        this.workingDays = grid.workingPeriods().stream().map(period -> grid.dayOf(period.start())).distinct().toList();
        this.longestWorkingPeriod = grid.workingPeriods().stream().mapToInt(SlotRange::length).max().orElse(0);
    }

    public static List<PrecheckIssue> run(Problem problem) {
        return new Prechecks(problem).check();
    }

    private List<PrecheckIssue> check() {
        problem.events().forEach(this::checkRoom);
        problem.events().forEach(this::checkCommissionPool);
        checkPinned();
        if (workingDays.isEmpty()) {
            // Every time-based check would fail for every event; one issue says why.
            issue(Code.NO_WORKING_TIME, Severity.ERROR, "В периоде сессии нет рабочего времени", List.of(), List.of());
            return List.copyOf(issues);
        }
        problem.events().forEach(this::checkWindow);
        checkTeacherLoad();
        checkPreparation();
        checkDependencies();
        checkFreeTime();
        return List.copyOf(issues);
    }

    private void checkWindow(Event event) {
        if (event.duration() > longestWorkingPeriod) {
            issue(Code.EVENT_LONGER_THAN_WORKING_DAY, Severity.ERROR, "Событие на " + minutes(event.duration())
                    + " мин длиннее рабочего дня", List.of(event.id()), List.of());
            return;
        }
        BitSet busy = new BitSet();
        for (long participant : event.participants()) {
            busy.or(hardBusy(problem.person(participant), event.scope()));
        }
        if (grid.workingPeriods().stream().noneMatch(period -> longestFreeRun(period, busy) >= event.duration())) {
            issue(Code.NO_COMMON_WINDOW, Severity.ERROR, "У обязательных участников нет общего свободного окна на "
                    + minutes(event.duration()) + " мин", List.of(event.id()), event.participants());
        }
    }

    private void checkRoom(Event event) {
        int people = event.participants().size() + event.commission().size();
        boolean online = event.format() == Event.Format.ONLINE;
        boolean fits = problem.rooms().stream().anyMatch(room -> online ? room.online() : !room.online()
                && room.capacity() >= people && room.equipment().containsAll(event.equipment()));
        if (!fits) {
            issue(Code.NO_ROOM, Severity.ERROR, online ? "Нет онлайн-площадки для онлайн-события"
                    : "Нет аудитории на " + people + " чел. с нужным оборудованием", List.of(event.id()), List.of());
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
            issue(Code.COMMISSION_POOL_TOO_SMALL, Severity.ERROR, "Для комиссии нужно " + requirement.size()
                    + " чел., а в пуле подходят " + candidates.size(), List.of(event.id()), List.of());
        } else if (requirement.chairRequired() && candidates.stream().noneMatch(CommissionCandidate::canChair)) {
            issue(Code.COMMISSION_POOL_TOO_SMALL, Severity.ERROR, "В пуле нет председателя для комиссии",
                    List.of(event.id()), List.of());
        }
    }

    /** Pinned assignments that break hard constraints leave a solver without any valid schedule. */
    private void checkPinned() {
        if (problem.pinned().isEmpty()) {
            return;
        }
        ScheduleValidator.validate(problem, problem.pinned()).violations().forEach(violation ->
                issues.add(new PrecheckIssue(Code.PINNED_CONFLICT, Severity.ERROR, "Закреплённые назначения: " + violation.message(),
                        violation.eventIds(), violation.personIds(), violation.roomIds())));
    }

    /** Required events of a teacher against the teacher's free working time; commissions are not counted. */
    private void checkTeacherLoad() {
        Map<Long, List<Event>> byTeacher = new TreeMap<>();
        for (Event event : problem.events()) {
            event.participants().stream().filter(p -> problem.person(p).role() == Person.Role.TEACHER)
                    .forEach(teacher -> byTeacher.computeIfAbsent(teacher, _ -> new ArrayList<>()).add(event));
        }
        byTeacher.forEach((teacher, events) -> {
            int load = events.stream().mapToInt(Event::duration).sum();
            int free = freeWorkingSlots(hardBusy(problem.person(teacher), Event.Scope.INDIVIDUAL));
            if (load > free) {
                issue(Code.TEACHER_OVERLOADED, Severity.ERROR, "У преподавателя событий на " + minutes(load)
                        + " мин, а свободного рабочего времени " + minutes(free) + " мин",
                        events.stream().map(Event::id).toList(), List.of(teacher));
            }
        });
    }

    /**
     * Every two events with preparation differ by at least the smaller preparation in days, so n of them need
     * at least (n - 1) · min days between the first and the last working day. Students with the same events,
     * such as a whole group, get one issue.
     */
    private void checkPreparation() {
        Map<Long, List<Long>> eventsByStudent = new TreeMap<>();
        for (Event event : problem.events()) {
            if (event.preparationDays() > 0) {
                event.participants().stream().filter(p -> problem.person(p).role() == Person.Role.STUDENT)
                        .forEach(student -> eventsByStudent.computeIfAbsent(student, _ -> new ArrayList<>()).add(event.id()));
            }
        }
        int span = workingDays.getLast() - workingDays.getFirst();
        Map<List<Long>, List<Long>> studentsByEvents = new LinkedHashMap<>();
        eventsByStudent.forEach((student, events) -> studentsByEvents.computeIfAbsent(events, _ -> new ArrayList<>()).add(student));
        studentsByEvents.forEach((events, students) -> {
            int minPreparation = events.stream().mapToInt(id -> problem.event(id).preparationDays()).min().orElse(0);
            if ((events.size() - 1) * minPreparation > span) {
                issue(Code.PREPARATION_DOES_NOT_FIT, Severity.ERROR, events.size() + " события с подготовкой по "
                        + minPreparation + " дн. не помещаются в дни сессии", events, students);
            }
        });
    }

    /** Some pair of working days must be as far apart as the dependency needs. */
    private void checkDependencies() {
        for (Dependency dependency : problem.dependencies()) {
            boolean fits = workingDays.stream().anyMatch(first -> workingDays.stream().anyMatch(second -> {
                int gap = second - first;
                return gap >= dependency.minGapDays()
                        && (dependency.maxGapDays().isEmpty() || gap <= dependency.maxGapDays().getAsInt());
            }));
            if (!fits) {
                String gap = dependency.maxGapDays().isPresent()
                        ? dependency.minGapDays() + "–" + dependency.maxGapDays().getAsInt() + " дн."
                        : "не меньше чем " + dependency.minGapDays() + " дн.";
                issue(Code.DEPENDENCY_DOES_NOT_FIT, Severity.ERROR, "В рабочих днях сессии нельзя развести связанные события на " + gap,
                        List.of(dependency.before(), dependency.after()), List.of());
            }
        }
    }

    private void checkFreeTime() {
        List<Long> withoutFreeTime = problem.people().stream()
                .filter(person -> freeWorkingSlots(hardBusy(person, Event.Scope.INDIVIDUAL)) == 0)
                .map(Person::id).toList();
        if (!withoutFreeTime.isEmpty()) {
            issue(Code.NO_FREE_TIME, Severity.WARNING, "Участники не оставили себе свободного рабочего времени",
                    List.of(), withoutFreeTime);
        }
    }

    private BitSet hardBusy(Person person, Event.Scope scope) {
        return hardBusy.computeIfAbsent(List.of(person.id(), scope), _ -> {
            BitSet busy = new BitSet();
            person.busy().stream().filter(b -> BusyRules.isHard(person.role(), b.kind(), scope))
                    .forEach(b -> busy.set(b.range().start(), b.range().end()));
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

    private void issue(Code code, Severity severity, String message, List<Long> eventIds, List<Long> personIds) {
        issues.add(new PrecheckIssue(code, severity, message, eventIds, personIds, List.of()));
    }
}
