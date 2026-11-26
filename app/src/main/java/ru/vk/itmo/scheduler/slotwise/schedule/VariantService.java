package ru.vk.itmo.scheduler.slotwise.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.PersonalScheduleEventResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.PersonalScheduleResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.PublishVariantRequest;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleCommissionMemberResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleMetrics;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleParticipantResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleRoomResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.VariantResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.VariantSummaryResponse;
import ru.vk.itmo.scheduler.slotwise.solving.ProblemLoader;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverParameters;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Viewing and publishing variants (UX-13, UX-15) and the personal schedule of participants (UX-17).
 * Only the organizer who owns the session sees its variants; others get 404.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VariantService {
    private static final String HEADER = """
            select v.id, v.planning_session_id, v.input_snapshot_id, s.input_version, ps.input_version as current_version,
                   coalesce((s.problem ->> 'incompleteData')::boolean, false) as incomplete,
                   coalesce(s.problem -> 'nonRespondentIds', '[]')::text as non_respondents,
                   v.solver_job_id, j.algorithm, j.parameters::text as parameters, v.base_variant_id, v.status,
                   v.version, v.metrics::text as metrics, v.created_at, v.published_at, v.published_by
            from slotwise.schedule_variant v
            join slotwise.input_snapshot s on s.id = v.input_snapshot_id
            join slotwise.planning_session ps on ps.id = v.planning_session_id
            join slotwise.solver_job j on j.id = v.solver_job_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PlanningSessionRepository sessions;
    private final JsonMapper json;
    private final Clock clock;
    private final ProblemLoader problems;

    public PageResponse<VariantSummaryResponse> list(long sessionId, long user, int page, int size) {
        var session = sessions.findById(sessionId).filter(found -> found.getOrganizerId() == user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена"));
        Long total = jdbc.queryForObject("select count(*) from slotwise.schedule_variant where planning_session_id = :session",
                Map.of("session", session.getId()), Long.class);
        var items = jdbc.query(HEADER + """
                where v.planning_session_id = :session order by v.created_at desc, v.id desc limit :size offset :offset
                """, Map.of("session", sessionId, "size", size, "offset", (long) page * size), (rs, row) -> {
            var header = header(rs);
            return new VariantSummaryResponse(header.id(), header.sessionId(), header.jobId(), header.status(),
                    header.algorithm(), header.baseVariantId(), header.inputVersion(), header.currentInputVersion(),
                    header.stale(), header.incompleteData(), header.metrics(), header.version(), header.createdAt(),
                    header.publishedAt());
        });
        return new PageResponse<>(items, Objects.requireNonNull(total), page, size);
    }

    public VariantResponse get(long id, long user) {
        var header = ownHeader(id, user);
        return response(header);
    }

    /**
     * Publishes the whole draft and supersedes the previous publication of the session. Repeating the publication
     * of the current variant changes nothing.
     */
    @Transactional
    public VariantResponse publish(long id, long user, PublishVariantRequest request) {
        long sessionId = ownHeader(id, user).sessionId();
        var session = sessions.findForUpdate(sessionId).orElseThrow();
        jdbc.queryForObject("select id from slotwise.schedule_variant where id = :id for update",
                Map.of("id", id), Long.class);
        var header = ownHeader(id, user);
        if (header.status() == ScheduleVariantStatus.PUBLISHED) {
            return response(header);
        }
        if (header.status() != ScheduleVariantStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "VARIANT_NOT_DRAFT", "Заменённый вариант нельзя опубликовать");
        }
        if (header.version() != request.expectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VARIANT_VERSION",
                    "Вариант изменился; обновите его и повторите запрос");
        }
        var assignments = response(header).assignments();
        int unassigned = validateForPublication(problems.load(session), assignments);
        boolean hasUnassigned = unassigned > 0 || assignments.stream().anyMatch(a -> a.status() == AssignmentStatus.UNASSIGNED);
        if (hasUnassigned && !request.confirmUnassigned()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNASSIGNED_NOT_CONFIRMED",
                    "В варианте есть неназначенные события: подтвердите публикацию");
        }
        if (header.stale() && !request.confirmStaleData()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "STALE_DATA_NOT_CONFIRMED",
                    "Вариант рассчитан на устаревших данных: подтвердите публикацию");
        }
        for (var assignment : assignments) {
            jdbc.update("""
                    update slotwise.assignment set published_event = cast(:snapshot as jsonb)
                    where schedule_variant_id = :variant and event_id = :event
                    """, Map.of("variant", id, "event", assignment.eventId(), "snapshot",
                    json.writeValueAsString(new PublishedEvent(session.getName(), assignment))));
        }
        jdbc.update("""
                update slotwise.schedule_variant set status = 'SUPERSEDED', version = version + 1
                where planning_session_id = :session and status = 'PUBLISHED'
                """, Map.of("session", sessionId));
        jdbc.update("""
                update slotwise.schedule_variant
                set status = 'PUBLISHED', published_at = :now, published_by = :user, version = version + 1
                where id = :id
                """, Map.of("id", id, "now", Timestamp.from(clock.instant()), "user", user));
        return response(ownHeader(id, user));
    }

    private static int validateForPublication(Problem problem, List<AssignmentResponse> rows) {
        var grid = problem.grid();
        long slotSeconds = grid.slotMinutes() * 60L;
        var events = problem.events().stream().collect(Collectors.toMap(Event::id, Function.identity()));
        var placements = new ArrayList<Assignment>();
        var issues = new ArrayList<ApiIssue>();
        for (var row : rows) {
            if (row.status() != AssignmentStatus.ASSIGNED) {
                continue;
            }
            var event = events.get(row.eventId());
            var offset = Duration.between(grid.origin(), row.startsAt());
            long start = offset.toSeconds() / slotSeconds;
            if (event == null || offset.isNegative() || offset.getNano() != 0 || offset.toSeconds() % slotSeconds != 0
                    || start + event.duration() > grid.slotCount()
                    || !grid.toInstant((int) start + event.duration()).equals(row.endsAt())) {
                issues.add(new ApiIssue("INVALID_PUBLISHED_PLACEMENT",
                        "Событие или интервал назначения не соответствует текущим данным сессии", IssueSeverity.ERROR,
                        List.of(row.eventId()), List.of(), List.of(row.room().id())));
                continue;
            }
            var members = row.commissionMembers().stream().map(ScheduleCommissionMemberResponse::accountId).toList();
            var chairs = row.commissionMembers().stream().filter(ScheduleCommissionMemberResponse::chair)
                    .mapToLong(ScheduleCommissionMemberResponse::accountId).toArray();
            if (chairs.length > 1) {
                issues.add(new ApiIssue("INVALID_COMMISSION", "У события несколько председателей", IssueSeverity.ERROR,
                        List.of(row.eventId()), members, List.of()));
                continue;
            }
            var chair = chairs.length == 0 ? OptionalLong.empty() : OptionalLong.of(chairs[0]);
            placements.add(new Assignment(row.eventId(), (int) start, row.room().id(), members, chair));
        }
        var validation = ScheduleValidator.validate(problem, placements);
        validation.violations().forEach(violation -> issues.add(new ApiIssue(violation.reason().name(),
                violation.message(), IssueSeverity.ERROR, violation.eventIds(), violation.personIds(), violation.roomIds())));
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "HARD_VIOLATIONS",
                    "Вариант нарушает жёсткие ограничения, его нельзя опубликовать", Map.of("issues", issues));
        }
        return validation.score().unassigned();
    }

    /**
     * Assigned events of the current publications where the person takes part or sits on the commission.
     * An event is marked changed if the previous publication placed it differently or did not have it.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PersonalScheduleResponse personal(long user, Instant from, Instant to) {
        String timeZone = jdbc.queryForObject("select time_zone from slotwise.account where id = :user",
                Map.of("user", user), String.class);
        record Row(long sessionId, long variantId, PublishedEvent snapshot) {
        }
        var rows = jdbc.query("""
                select v.planning_session_id, v.id, a.published_event::text
                from slotwise.schedule_variant v
                join slotwise.assignment a on a.schedule_variant_id = v.id and a.status = 'ASSIGNED'
                where v.status = 'PUBLISHED' and a.starts_at < :to and a.ends_at > :from
                  and ((a.published_event->'assignment'->'participants') @>
                            jsonb_build_array(jsonb_build_object('accountId', cast(:user as bigint)))
                       or (a.published_event->'assignment'->'commissionMembers') @>
                            jsonb_build_array(jsonb_build_object('accountId', cast(:user as bigint))))
                order by a.starts_at, a.event_id
                """, Map.of("user", user, "from", Timestamp.from(from), "to", Timestamp.from(to)), (rs, row) ->
                new Row(rs.getLong(1), rs.getLong(2), publishedEvent(rs.getString(3))));

        Set<Long> variants = rows.stream().map(Row::variantId).collect(Collectors.toSet());
        var previous = previousPlacements(variants);
        var result = rows.stream().map(row -> {
            var assignment = row.snapshot().assignment();
            var placement = new Placement(assignment.startsAt(), assignment.endsAt(), assignment.room().id(),
                    assignment.commissionMembers().stream().map(member -> new CommissionRole(member.accountId(), member.chair()))
                            .collect(Collectors.toSet()));
            var before = previous.get(row.variantId());
            boolean changed = before != null && !placement.equals(before.get(assignment.eventId()));
            return new PersonalScheduleEventResponse(row.sessionId(), row.snapshot().sessionName(), row.variantId(),
                    assignment.eventId(), assignment.eventTypeId(), assignment.title(), assignment.format(),
                    assignment.startsAt(), assignment.endsAt(), assignment.room(), assignment.participants(),
                    assignment.commissionMembers(), changed);
        }).toList();
        return new PersonalScheduleResponse(from, to, timeZone, result);
    }

    private VariantResponse response(Header header) {
        var params = Map.of("variant", header.id(), "session", header.sessionId());
        var participants = participants(jdbc.queryForList(
                "select event_id from slotwise.assignment where schedule_variant_id = :variant and published_event is null",
                params, Long.class));
        var commissions = commissions(Set.of(header.id()));
        var assignments = jdbc.query("""
                select a.event_id, e.event_type_id, e.title, e.format, e.duration_minutes, a.status, a.starts_at,
                       a.ends_at, r.id, r.name, r.building, r.online, a.locked, a.version, a.explanation::text, a.published_event::text
                from slotwise.assignment a
                join slotwise.event e on e.id = a.event_id
                left join slotwise.room r on r.id = a.room_id
                where a.schedule_variant_id = :variant order by a.event_id
                """, params, (rs, row) -> {
            var snapshot = publishedEvent(rs.getString(16));
            if (snapshot != null) {
                return snapshot.assignment();
            }
            long event = rs.getLong(1);
            boolean assigned = rs.getString(6).equals("ASSIGNED");
            String explanation = rs.getString(15);
            return new AssignmentResponse(event, rs.getLong(2), rs.getString(3), EventFormat.valueOf(rs.getString(4)),
                    rs.getInt(5), participants.getOrDefault(event, List.of()), AssignmentStatus.valueOf(rs.getString(6)),
                    assigned ? instant(rs, 7) : null, assigned ? instant(rs, 8) : null, assigned ? room(rs, 9) : null,
                    commissions.getOrDefault(new EventKey(header.id(), event), List.of()), rs.getBoolean(13),
                    rs.getLong(14), explanation == null ? List.of() : List.of(json.readValue(explanation, ApiIssue[].class)));
        });
        return new VariantResponse(header.id(), header.sessionId(), header.snapshotId(), header.inputVersion(),
                header.currentInputVersion(), header.stale(), header.incompleteData(), header.nonRespondentIds(),
                header.jobId(), header.algorithm(), header.parameters(), header.baseVariantId(), header.status(),
                header.version(), header.metrics(), assignments, List.of(), header.createdAt(), header.publishedAt(),
                header.publishedBy());
    }

    private Header ownHeader(long id, long user) {
        return jdbc.query(HEADER + " where v.id = :id and ps.organizer_id = :user", Map.of("id", id, "user", user),
                        (rs, row) -> header(rs)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "Вариант не найден"));
    }

    private Header header(ResultSet rs) throws SQLException {
        var parameters = json.readTree(rs.getString("parameters"));
        return new Header(rs.getLong("id"), rs.getLong("planning_session_id"), rs.getLong("input_snapshot_id"),
                rs.getLong("input_version"), rs.getLong("current_version"), rs.getBoolean("incomplete"),
                List.of(json.readValue(rs.getString("non_respondents"), Long[].class)), rs.getLong("solver_job_id"),
                SolverAlgorithm.valueOf(rs.getString("algorithm")),
                new SolverParameters(parameters.get("timeLimitSeconds").intValue(), parameters.get("seed").longValue()),
                rs.getObject("base_variant_id", Long.class), ScheduleVariantStatus.valueOf(rs.getString("status")),
                rs.getLong("version"), json.readValue(rs.getString("metrics"), ScheduleMetrics.class),
                instant(rs, "created_at"), rs.getTimestamp("published_at") == null ? null : instant(rs, "published_at"),
                rs.getObject("published_by", Long.class));
    }

    private Map<Long, List<ScheduleParticipantResponse>> participants(Collection<Long> events) {
        var result = new HashMap<Long, List<ScheduleParticipantResponse>>();
        if (events.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select p.event_id, a.id, a.last_name, a.first_name, a.patronymic, p.participant_role
                from slotwise.event_participant p join slotwise.account a on a.id = p.account_id
                where p.event_id in (:events) order by a.last_name, a.first_name, a.id
                """, Map.of("events", events), rs -> {
            result.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>()).add(new ScheduleParticipantResponse(
                    rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    ParticipantRole.valueOf(rs.getString(6))));
        });
        return result;
    }

    private Map<EventKey, List<ScheduleCommissionMemberResponse>> commissions(Set<Long> variants) {
        var result = new HashMap<EventKey, List<ScheduleCommissionMemberResponse>>();
        if (variants.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select m.schedule_variant_id, m.event_id, a.id, a.last_name, a.first_name, a.patronymic, m.chair
                from slotwise.assignment_commission_member m join slotwise.account a on a.id = m.account_id
                where m.schedule_variant_id in (:variants) order by m.chair desc, a.last_name, a.first_name, a.id
                """, Map.of("variants", variants), rs -> {
            result.computeIfAbsent(new EventKey(rs.getLong(1), rs.getLong(2)), key -> new ArrayList<>())
                    .add(new ScheduleCommissionMemberResponse(rs.getLong(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getBoolean(7)));
        });
        return result;
    }

    /**
     * For each published variant, where the previous publication of its session placed every event.
     */
    private Map<Long, Map<Long, Placement>> previousPlacements(Set<Long> variants) {
        var result = new HashMap<Long, Map<Long, Placement>>();
        if (variants.isEmpty()) {
            return result;
        }
        var members = new HashMap<EventKey, Set<CommissionRole>>();
        jdbc.query("""
                select v.id, a.event_id, a.starts_at, a.ends_at, a.room_id, m.account_id, m.chair
                from slotwise.schedule_variant v
                join lateral (select id from slotwise.schedule_variant p
                              where p.planning_session_id = v.planning_session_id and p.status = 'SUPERSEDED'
                              order by p.published_at desc, p.id desc limit 1) previous on true
                left join slotwise.assignment a on a.schedule_variant_id = previous.id and a.status = 'ASSIGNED'
                left join slotwise.assignment_commission_member m
                       on m.schedule_variant_id = previous.id and m.event_id = a.event_id
                where v.id in (:variants)
                """, Map.of("variants", variants), rs -> {
            long variant = rs.getLong(1);
            var placements = result.computeIfAbsent(variant, id -> new HashMap<>());
            Long event = rs.getObject(2, Long.class);
            if (event == null) {
                return;
            }
            var commission = members.computeIfAbsent(new EventKey(variant, event), key -> new HashSet<>());
            Long member = rs.getObject(6, Long.class);
            if (member != null) {
                commission.add(new CommissionRole(member, rs.getBoolean(7)));
            }
            placements.put(event, new Placement(instant(rs, 3), instant(rs, 4), rs.getLong(5), commission));
        });
        return result;
    }

    private static ScheduleRoomResponse room(ResultSet rs, int firstColumn) throws SQLException {
        return new ScheduleRoomResponse(rs.getLong(firstColumn), rs.getString(firstColumn + 1),
                rs.getString(firstColumn + 2), rs.getBoolean(firstColumn + 3));
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private record EventKey(long variantId, long eventId) {
    }

    private PublishedEvent publishedEvent(String value) {
        return value == null ? null : json.readValue(value, PublishedEvent.class);
    }

    private record PublishedEvent(String sessionName, AssignmentResponse assignment) {
    }

    private record CommissionRole(long accountId, boolean chair) {
    }

    private record Placement(Instant startsAt, Instant endsAt, long roomId, Set<CommissionRole> commission) {
    }

    private record Header(long id, long sessionId, long snapshotId, long inputVersion, long currentInputVersion,
                          boolean incompleteData, List<Long> nonRespondentIds, long jobId, SolverAlgorithm algorithm,
                          SolverParameters parameters, Long baseVariantId, ScheduleVariantStatus status, long version,
                          ScheduleMetrics metrics, Instant createdAt, Instant publishedAt, Long publishedBy) {
        boolean stale() {
            return inputVersion != currentInputVersion;
        }
    }
}
