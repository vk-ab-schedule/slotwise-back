package ru.vk.itmo.scheduler.slotwise.solving;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.planning.BusyIntervalNormalizer;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningAvailabilityService;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionEntity;
import ru.vk.itmo.scheduler.slotwise.planning.SessionWorkingDayRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the solver input from a planning session (MODEL.md, «Входные данные солвера»).
 *
 * <p>Only complete events take part. The role of a person comes from the session, not from the account,
 * so an import that changes the account role does not break a calculation: students of the events are
 * students, everybody else is a teacher. At a defense of one student the supervisor may not sit on the commission.
 */
@Component
@RequiredArgsConstructor
public class ProblemLoader {
    private static final int SLOT_MINUTES = TimeGrid.DEFAULT_SLOT_MINUTES;

    private final NamedParameterJdbcTemplate jdbc;
    private final BusyIntervalNormalizer normalizer;
    private final SessionWorkingDayRepository workingDays;
    private final PlanningAvailabilityService availability;

    public Problem load(PlanningSessionEntity session) {
        TimeGrid grid = grid(session);
        Map<String, Object> params = Map.of("session", session.getId(),
                "from", Timestamp.from(session.getStartsAt()), "to", Timestamp.from(session.getEndsAt()));

        var participants = pairs("""
                select event_id, account_id from slotwise.event_participant where planning_session_id = :session
                """, params);
        var pool = jdbc.query("""
                select account_id, can_chair from slotwise.commission_candidate where planning_session_id = :session
                """, params, (rs, row) -> new CommissionCandidate(rs.getLong(1), rs.getBoolean(2)));
        Set<Long> poolIds = pool.stream().map(CommissionCandidate::personId).collect(Collectors.toSet());
        Set<Long> students = new HashSet<>(jdbc.queryForList("""
                select account_id from slotwise.event_participant
                where planning_session_id = :session and participant_role = 'STUDENT'
                """, params, Long.class));
        students.removeAll(poolIds);

        var equipment = pairs("""
                select ee.event_id, ee.equipment_id from slotwise.event_equipment ee
                join slotwise.event e on e.id = ee.event_id where e.planning_session_id = :session
                """, params);
        var supervisors = pairs("""
                select s.student_id, s.supervisor_id from slotwise.supervision s
                join slotwise.session_participant p on p.account_id = s.student_id and p.planning_session_id = :session
                """, params);
        var events = jdbc.query("""
                select e.id, e.duration_minutes, e.format, e.commission_size, e.requires_chair, e.preparation_days,
                       coalesce(e.granularity, t.granularity)
                from slotwise.event e join slotwise.event_type t on t.id = e.event_type_id
                where e.planning_session_id = :session and e.complete
                """, params, (rs, row) -> event(rs, grid, participants, equipment, supervisors, poolIds)).stream()
                .filter(event -> !event.participants().isEmpty())
                .toList();

        Set<Long> people = new HashSet<>(poolIds);
        events.forEach(event -> people.addAll(event.participants()));
        var busy = new HashMap<Long, List<Person.Busy>>();
        jdbc.query("""
                select t.account_id, t.starts_at, t.ends_at, 'TIMETABLE' from slotwise.timetable_entry t
                join slotwise.session_participant p on p.account_id = t.account_id and p.planning_session_id = :session
                where t.ends_at > :from and t.starts_at < :to
                union all
                select b.account_id, b.starts_at, b.ends_at, b.importance from slotwise.busy_interval b
                join slotwise.session_participant p
                  on p.planning_session_id = b.planning_session_id and p.account_id = b.account_id
                where b.planning_session_id = :session and p.response_status = 'SUBMITTED'
                """, params, rs -> {
            long person = rs.getLong(1);
            var kind = Person.Busy.Kind.valueOf(rs.getString(4));
            if (people.contains(person)) {
                range(grid, rs, 2).ifPresent(range -> busy.computeIfAbsent(person, id -> new ArrayList<>())
                        .add(new Person.Busy(range, kind)));
            }
        });

        Set<Long> eventIds = events.stream().map(Event::id).collect(Collectors.toSet());
        var rules = availability.rules(session.getId(), session.getOrganizerId());
        var weights = rules.weights();
        return Problem.builder()
                .grid(grid)
                .people(people.stream().map(id -> new Person(id, students.contains(id) ? Person.Role.STUDENT
                        : Person.Role.TEACHER, busy.getOrDefault(id, List.of()))).toList())
                .rooms(rooms(grid, params))
                .commissionPool(pool)
                .events(events)
                .dependencies(jdbc.query("""
                        select predecessor_event_id, successor_event_id, min_gap_days, max_gap_days
                        from slotwise.event_dependency where planning_session_id = :session
                        """, params, (rs, row) -> new Dependency(rs.getLong(1), rs.getLong(2), rs.getInt(3),
                        rs.getObject(4) == null ? OptionalInt.empty() : OptionalInt.of(rs.getInt(4)))).stream()
                        .filter(dependency -> eventIds.contains(dependency.before()) && eventIds.contains(dependency.after()))
                        .toList())
                .roomChangeBuffer(rules.roomChangeBufferMinutes() / SLOT_MINUTES)
                .weights(new Weights(weights.teacherGaps(), weights.commissionLoadSpread(), weights.undesirableTime(),
                        weights.teacherRoomChanges(), weights.movedEvents()))
                .build();
    }

    /**
     * The problem with a variant of the session as the baseline for S5, its locked assignments pinned (H9).
     * Outdated assignments are left out when the caller is inspecting or editing an existing variant.
     */
    public Problem withBaseline(Problem problem, long variantId) {
        return withBaseline(problem, variantId, false);
    }

    Problem withBaseline(Problem problem, long variantId, boolean validateLocks) {
        var params = Map.of("variant", variantId);
        var members = new HashMap<Long, List<Long>>();
        var chairs = new HashMap<Long, Long>();
        jdbc.query("""
                select event_id, account_id, chair from slotwise.assignment_commission_member
                where schedule_variant_id = :variant
                """, params, rs -> {
            members.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>()).add(rs.getLong(2));
            if (rs.getBoolean(3)) {
                chairs.put(rs.getLong(1), rs.getLong(2));
            }
        });
        Set<Long> events = problem.events().stream().map(Event::id).collect(Collectors.toSet());
        Set<Long> rooms = problem.rooms().stream().map(Room::id).collect(Collectors.toSet());
        Set<Long> people = problem.people().stream().map(Person::id).collect(Collectors.toSet());
        Set<Long> canChair = problem.commissionPool().stream().filter(CommissionCandidate::canChair)
                .map(CommissionCandidate::personId).collect(Collectors.toSet());
        var grid = problem.grid();
        var baseline = new ArrayList<Assignment>();
        var pinned = new ArrayList<Assignment>();
        var pinConflicts = new ArrayList<ApiIssue>();
        long slotSeconds = SLOT_MINUTES * 60L;
        jdbc.query("""
                select event_id, starts_at, room_id, locked, ends_at from slotwise.assignment
                where schedule_variant_id = :variant and status = 'ASSIGNED'
                order by event_id
                """, params, rs -> {
            long event = rs.getLong(1);
            var startsAt = rs.getTimestamp(2).toInstant();
            var offset = Duration.between(grid.origin(), startsAt);
            long start = offset.getSeconds() / slotSeconds;
            long room = rs.getLong(3);
            boolean locked = rs.getBoolean(4);
            var commission = members.getOrDefault(event, List.of());
            Long chair = chairs.get(event);
            if (!events.contains(event) || !rooms.contains(room) || !people.containsAll(commission)
                    || chair != null && !canChair.contains(chair) || offset.isNegative()
                    || offset.getNano() != 0 || offset.getSeconds() % slotSeconds != 0
                    || start + problem.event(event).duration() > grid.slotCount()) {
                if (locked && validateLocks) {
                    pinConflicts.add(new ApiIssue("PINNED_CONFLICT",
                            "Зафиксированное назначение не соответствует текущему периоду или составу сессии",
                            IssueSeverity.ERROR, List.of(event), List.of(), List.of(room)));
                }
                return;
            }
            if (locked && validateLocks && !startsAt.plusSeconds(problem.event(event).duration() * slotSeconds)
                    .equals(rs.getTimestamp(5).toInstant())) {
                pinConflicts.add(new ApiIssue("PINNED_CONFLICT",
                        "Длительность события изменилась и больше не совпадает с зафиксированным интервалом",
                        IssueSeverity.ERROR, List.of(event), List.of(), List.of(room)));
                return;
            }
            var assignment = new Assignment(event, (int) start, room, commission,
                    chair == null ? OptionalLong.empty() : OptionalLong.of(chair));
            baseline.add(assignment);
            if (locked) {
                pinned.add(assignment);
            }
        });
        if (validateLocks && !pinned.isEmpty()) {
            ScheduleValidator.validate(problem, pinned).violations().forEach(violation ->
                    pinConflicts.add(new ApiIssue("PINNED_CONFLICT", violation.message(), IssueSeverity.ERROR,
                            violation.eventIds(), violation.personIds(), violation.roomIds())));
        }
        if (!pinConflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PINNED_CONFLICT",
                    "Проверьте зафиксированные назначения и исправьте конфликт перед запуском расчёта",
                    Map.of("issues", pinConflicts));
        }
        return problem.toBuilder().pinned(pinned).baseline(baseline).build();
    }

    /** Locks the version row until the caller has loaded the baseline assignments. */
    Optional<Long> lockVariantVersion(long variantId, long sessionId) {
        return jdbc.query("""
                select version from slotwise.schedule_variant
                where id = :variant and planning_session_id = :session for share
                """, Map.of("variant", variantId, "session", sessionId), (rs, row) -> rs.getLong(1)).stream().findFirst();
    }

    /**
     * Events without some required participants: they do not take part in a calculation (UX-08).
     */
    public List<Long> incompleteEvents(long sessionId) {
        return jdbc.queryForList("""
                select id from slotwise.event where planning_session_id = :session and not complete order by id
                """, Map.of("session", sessionId), Long.class);
    }

    public List<Long> nonRespondents(long sessionId) {
        return jdbc.queryForList("""
                select account_id from slotwise.session_participant
                where planning_session_id = :session and response_status = 'NOT_RESPONDED' order by account_id
                """, Map.of("session", sessionId), Long.class);
    }

    private TimeGrid grid(PlanningSessionEntity session) {
        ZoneId zone = ZoneId.of(session.getTimeZone());
        long slotSeconds = SLOT_MINUTES * 60L;
        Instant origin = Instant.ofEpochSecond(Math.floorDiv(session.getStartsAt().getEpochSecond(), slotSeconds) * slotSeconds);
        Duration span = Duration.between(origin, session.getEndsAt());
        int slots = Math.toIntExact(span.plusSeconds(slotSeconds).minusNanos(1).getSeconds() / slotSeconds);
        var withoutPeriods = new TimeGrid(origin, zone, SLOT_MINUTES, slots, List.of());
        Set<Integer> weekdays = workingDays.findByPlanningSessionId(session.getId()).stream()
                .map(day -> day.getDayOfWeek().intValue()).collect(Collectors.toSet());
        List<SlotRange> periods = normalizer.workingWindows(session, weekdays).stream()
                .flatMap(window -> withoutPeriods.containedIn(window.start(), window.end()).stream())
                .toList();
        return new TimeGrid(origin, zone, SLOT_MINUTES, slots, periods);
    }

    private List<Room> rooms(TimeGrid grid, Map<String, Object> params) {
        var equipment = pairs("""
                select re.room_id, re.equipment_id from slotwise.room_equipment re
                join slotwise.session_room sr on sr.room_id = re.room_id where sr.planning_session_id = :session
                """, params);
        var busy = new HashMap<Long, List<SlotRange>>();
        jdbc.query("""
                select t.room_id, t.starts_at, t.ends_at from slotwise.timetable_entry t
                join slotwise.session_room sr on sr.room_id = t.room_id
                where sr.planning_session_id = :session and t.ends_at > :from and t.starts_at < :to
                """, params, rs -> {
            long room = rs.getLong(1);
            range(grid, rs, 2).ifPresent(range -> busy.computeIfAbsent(room, id -> new ArrayList<>()).add(range));
        });
        return jdbc.query("""
                select r.id, r.online, r.capacity from slotwise.session_room sr join slotwise.room r on r.id = sr.room_id
                where sr.planning_session_id = :session
                """, params, (rs, row) -> {
            long id = rs.getLong(1);
            var roomEquipment = equipment.getOrDefault(id, List.of());
            return rs.getBoolean(2) ? new Room(id, null, roomEquipment, List.of(), true)
                    : new Room(id, rs.getInt(3), roomEquipment, busy.getOrDefault(id, List.of()));
        });
    }

    private static Event event(ResultSet rs, TimeGrid grid, Map<Long, List<Long>> participants,
                               Map<Long, List<Long>> equipment, Map<Long, List<Long>> supervisors,
                               Set<Long> pool) throws SQLException {
        long id = rs.getLong(1);
        List<Long> people = participants.getOrDefault(id, List.of());
        boolean individual = "PER_STUDENT".equals(rs.getString(7));
        List<Long> excluded = individual ? people.stream()
                .flatMap(person -> supervisors.getOrDefault(person, List.of()).stream())
                .filter(pool::contains).distinct().toList() : List.of();
        return new Event(id, grid.toSlots(Duration.ofMinutes(rs.getInt(2))),
                individual ? Event.Scope.INDIVIDUAL : Event.Scope.GROUP, Event.Format.valueOf(rs.getString(3)), people,
                new Event.CommissionRequirement(rs.getInt(4), rs.getBoolean(5)), excluded,
                equipment.getOrDefault(id, List.of()), rs.getInt(6));
    }

    private Map<Long, List<Long>> pairs(String sql, Map<String, Object> params) {
        var result = new HashMap<Long, List<Long>>();
        jdbc.query(sql, params, rs -> {
            result.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>()).add(rs.getLong(2));
        });
        return result;
    }

    private static Optional<SlotRange> range(TimeGrid grid, ResultSet rs, int startColumn) throws SQLException {
        return grid.covering(rs.getTimestamp(startColumn).toInstant(), rs.getTimestamp(startColumn + 1).toInstant());
    }
}
