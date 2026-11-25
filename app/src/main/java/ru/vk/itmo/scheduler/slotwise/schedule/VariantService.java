package ru.vk.itmo.scheduler.slotwise.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.validation.ScheduleValidator;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentUpdateRequest;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentUpdateResponse;
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
import ru.vk.itmo.scheduler.slotwise.solving.SolveStatus;
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
import java.util.TreeSet;
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
    private final ProblemLoader loader;
    private final JsonMapper json;
    private final Clock clock;

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
        sessions.findForUpdate(sessionId);
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
        if (header.metrics().hardViolations() > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "HARD_VIOLATIONS",
                    "Вариант нарушает жёсткие ограничения, его нельзя опубликовать");
        }
        if (header.metrics().unassignedEvents() > 0 && !request.confirmUnassigned()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNASSIGNED_NOT_CONFIRMED",
                    "В варианте есть неназначенные события: подтвердите публикацию");
        }
        if (header.stale() && !request.confirmStaleData()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "STALE_DATA_NOT_CONFIRMED",
                    "Вариант рассчитан на устаревших данных: подтвердите публикацию");
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

    /**
     * Moves, unassigns or locks one event of a draft (UX-14). The common validator checks the whole variant on the
     * current data; violations that involve the edited event return 422 and nothing is saved. A dry run only previews.
     */
    @Transactional
    public AssignmentUpdateResponse update(long variantId, long eventId, long user, boolean dryRun,
                                           AssignmentUpdateRequest request) {
        var header = ownHeader(variantId, user);
        if (header.status() != ScheduleVariantStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "VARIANT_NOT_DRAFT", "Править можно только черновик");
        }
        var params = Map.of("variant", variantId, "event", eventId);
        record Current(long version, int durationMinutes) {
        }
        var current = jdbc.query("""
                select a.version, e.duration_minutes from slotwise.assignment a join slotwise.event e on e.id = a.event_id
                where a.schedule_variant_id = :variant and a.event_id = :event
                """, params, (rs, row) -> new Current(rs.getLong(1), rs.getInt(2))).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSIGNMENT_NOT_FOUND", "Событие не найдено в варианте"));
        if (header.version() != request.expectedVariantVersion() || current.version() != request.expectedAssignmentVersion()) {
            throw stale();
        }

        var input = loader.load(sessions.findById(header.sessionId()).orElseThrow());
        var problem = (header.baseVariantId() == null ? input : loader.withBaseline(input, header.baseVariantId()))
                .toBuilder().clearPinned().build();
        List<Assignment> assignments = new ArrayList<>(loader.withBaseline(input, variantId).baseline().stream()
                .filter(assignment -> assignment.eventId() != eventId).toList());
        if (request.status() == AssignmentStatus.ASSIGNED) {
            assignments.add(edited(eventId, request, problem.grid().origin(), current.durationMinutes()));
        } else if (request.startsAt() != null || request.endsAt() != null || request.roomId() != null
                || !request.commissionMembers().isEmpty() || request.locked()) {
            throw invalid("У неназначенного события нет времени, аудитории, комиссии и фиксации");
        }
        var validation = ScheduleValidator.validate(problem, assignments);
        var issues = validation.violations().stream().filter(violation -> violation.eventIds().contains(eventId))
                .map(violation -> new ApiIssue(violation.reason().name(), violation.message(), IssueSeverity.ERROR,
                        violation.eventIds(), violation.personIds(), violation.roomIds()))
                .toList();
        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "HARD_VIOLATIONS",
                    "Правка нарушает жёсткие ограничения", Map.of("issues", issues));
        }
        // After a manual edit the proven bound no longer applies, so the variant is not known to be optimal.
        var metrics = ScheduleMetrics.of(validation, problem.weights(), SolveStatus.FEASIBLE,
                header.metrics().runtimeMillis(), OptionalLong.empty());
        var affected = new TreeSet<>(jdbc.queryForList("""
                select account_id from slotwise.event_participant where event_id = :event
                union select account_id from slotwise.assignment_commission_member
                where schedule_variant_id = :variant and event_id = :event
                """, params, Long.class));
        request.commissionMembers().forEach(member -> affected.add(member.accountId()));

        long variantVersion = header.version();
        long assignmentVersion = current.version();
        if (!dryRun) {
            save(header.sessionId(), variantId, eventId, request, metrics);
            variantVersion++;
            assignmentVersion++;
        }
        return new AssignmentUpdateResponse(variantId, variantVersion, dryRun,
                preview(eventId, request, assignmentVersion), metrics, List.copyOf(affected), List.of());
    }

    /**
     * Assigned events of the current publications where the person takes part or sits on the commission.
     * An event is marked changed if the previous publication placed it differently or did not have it.
     */
    public PersonalScheduleResponse personal(long user, Instant from, Instant to) {
        String timeZone = jdbc.queryForObject("select time_zone from slotwise.account where id = :user",
                Map.of("user", user), String.class);
        record Row(long sessionId, String sessionName, long variantId, long eventId, long eventTypeId, String title,
                   EventFormat format, Instant startsAt, Instant endsAt, ScheduleRoomResponse room) {
        }
        var rows = jdbc.query("""
                select ps.id, ps.name, v.id, e.id, e.event_type_id, e.title, e.format, a.starts_at, a.ends_at,
                       r.id, r.name, r.building, r.online
                from slotwise.schedule_variant v
                join slotwise.planning_session ps on ps.id = v.planning_session_id
                join slotwise.assignment a on a.schedule_variant_id = v.id and a.status = 'ASSIGNED'
                join slotwise.event e on e.id = a.event_id
                join slotwise.room r on r.id = a.room_id
                where v.status = 'PUBLISHED' and a.starts_at < :to and a.ends_at > :from
                  and (exists(select 1 from slotwise.event_participant p where p.event_id = e.id and p.account_id = :user)
                       or exists(select 1 from slotwise.assignment_commission_member m
                                 where m.schedule_variant_id = v.id and m.event_id = e.id and m.account_id = :user))
                order by a.starts_at, e.id
                """, Map.of("user", user, "from", Timestamp.from(from), "to", Timestamp.from(to)), (rs, row) -> new Row(
                rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getString(6),
                EventFormat.valueOf(rs.getString(7)), instant(rs, 8), instant(rs, 9), room(rs, 10)));

        Set<Long> variants = rows.stream().map(Row::variantId).collect(Collectors.toSet());
        Set<Long> events = rows.stream().map(Row::eventId).collect(Collectors.toSet());
        var participants = participants(events);
        var commissions = commissions(variants);
        var previous = previousPlacements(variants);
        var result = rows.stream().map(row -> {
            var key = new EventKey(row.variantId(), row.eventId());
            var commission = commissions.getOrDefault(key, List.of());
            var placement = new Placement(row.startsAt(), row.room().id(), commission.stream()
                    .map(ScheduleCommissionMemberResponse::accountId).collect(Collectors.toSet()));
            var before = previous.get(row.variantId());
            boolean changed = before != null && !placement.equals(before.get(row.eventId()));
            return new PersonalScheduleEventResponse(row.sessionId(), row.sessionName(), row.variantId(), row.eventId(),
                    row.eventTypeId(), row.title(), row.format(), row.startsAt(), row.endsAt(), row.room(),
                    participants.getOrDefault(row.eventId(), List.of()), commission, changed);
        }).toList();
        return new PersonalScheduleResponse(from, to, timeZone, result);
    }

    private static Assignment edited(long eventId, AssignmentUpdateRequest request, Instant origin, int durationMinutes) {
        if (request.startsAt() == null || request.endsAt() == null || request.roomId() == null) {
            throw invalid("Назначенному событию нужны начало, конец и аудитория");
        }
        Duration fromOrigin = Duration.between(origin, request.startsAt());
        if (fromOrigin.isNegative() || fromOrigin.toSeconds() % 300 != 0 || request.startsAt().getNano() != 0
                || !Duration.between(request.startsAt(), request.endsAt()).equals(Duration.ofMinutes(durationMinutes))) {
            throw invalid("Время должно быть внутри периода, кратно 5 минутам и совпадать с длительностью события");
        }
        var chairs = request.commissionMembers().stream().filter(AssignmentUpdateRequest.CommissionMember::chair)
                .map(AssignmentUpdateRequest.CommissionMember::accountId).toList();
        if (chairs.size() > 1) {
            throw invalid("У комиссии может быть только один председатель");
        }
        return new Assignment(eventId, Math.toIntExact(fromOrigin.toMinutes() / 5), request.roomId(),
                request.commissionMembers().stream().map(AssignmentUpdateRequest.CommissionMember::accountId).toList(),
                chairs.isEmpty() ? OptionalLong.empty() : OptionalLong.of(chairs.getFirst()));
    }

    private void save(long sessionId, long variantId, long eventId, AssignmentUpdateRequest request,
                      ScheduleMetrics metrics) {
        var params = new HashMap<String, Object>();
        params.put("variant", variantId);
        params.put("event", eventId);
        params.put("status", request.status().name());
        params.put("startsAt", request.startsAt() == null ? null : Timestamp.from(request.startsAt()));
        params.put("endsAt", request.endsAt() == null ? null : Timestamp.from(request.endsAt()));
        params.put("room", request.roomId());
        params.put("locked", request.locked());
        params.put("assignmentVersion", request.expectedAssignmentVersion());
        params.put("variantVersion", request.expectedVariantVersion());
        params.put("metrics", json.writeValueAsString(metrics));
        params.put("penalty", metrics.penalty());
        jdbc.update("""
                delete from slotwise.assignment_commission_member where schedule_variant_id = :variant and event_id = :event
                """, params);
        int changed = jdbc.update("""
                update slotwise.assignment set status = :status, starts_at = :startsAt, ends_at = :endsAt, room_id = :room,
                    locked = :locked, explanation = null, version = version + 1
                where schedule_variant_id = :variant and event_id = :event and version = :assignmentVersion
                """, params);
        changed += jdbc.update("""
                update slotwise.schedule_variant set metrics = cast(:metrics as jsonb), objective = :penalty,
                    best_bound = null, solve_status = 'FEASIBLE', version = version + 1
                where id = :variant and version = :variantVersion
                """, params);
        if (changed != 2) {
            throw stale();
        }
        for (var member : request.commissionMembers()) {
            jdbc.update("""
                    insert into slotwise.assignment_commission_member(planning_session_id, schedule_variant_id, event_id,
                        account_id, chair)
                    values (:session, :variant, :event, :account, :chair)
                    """, Map.of("session", sessionId, "variant", variantId, "event", eventId,
                    "account", member.accountId(), "chair", member.chair()));
        }
    }

    /**
     * The assignment as the request describes it, with the names of its room and commission.
     */
    private AssignmentResponse preview(long eventId, AssignmentUpdateRequest request, long version) {
        var participants = participants(List.of(eventId));
        var members = new HashMap<Long, ScheduleCommissionMemberResponse>();
        if (!request.commissionMembers().isEmpty()) {
            Set<Long> chairs = request.commissionMembers().stream().filter(AssignmentUpdateRequest.CommissionMember::chair)
                    .map(AssignmentUpdateRequest.CommissionMember::accountId).collect(Collectors.toSet());
            jdbc.query("select id, last_name, first_name, patronymic from slotwise.account where id in (:ids)",
                    Map.of("ids", request.commissionMembers().stream()
                            .map(AssignmentUpdateRequest.CommissionMember::accountId).toList()),
                    rs -> {
                        members.put(rs.getLong(1), new ScheduleCommissionMemberResponse(rs.getLong(1), rs.getString(2),
                                rs.getString(3), rs.getString(4), chairs.contains(rs.getLong(1))));
                    });
        }
        var room = request.roomId() == null ? null : jdbc.queryForObject(
                "select id, name, building, online from slotwise.room where id = :room",
                Map.of("room", request.roomId()), (rs, row) -> room(rs, 1));
        return jdbc.queryForObject("""
                select event_type_id, title, format, duration_minutes from slotwise.event where id = :event
                """, Map.of("event", eventId), (rs, row) -> new AssignmentResponse(eventId, rs.getLong(1),
                rs.getString(2), EventFormat.valueOf(rs.getString(3)), rs.getInt(4),
                participants.getOrDefault(eventId, List.of()), request.status(), request.startsAt(), request.endsAt(),
                room, request.commissionMembers().stream().map(member -> members.get(member.accountId())).toList(),
                request.locked(), version, List.of()));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ASSIGNMENT", message);
    }

    private static ApiException stale() {
        return new ApiException(HttpStatus.CONFLICT, "STALE_VARIANT_VERSION",
                "Вариант изменился; обновите его и повторите запрос");
    }

    private VariantResponse response(Header header) {
        var params = Map.of("variant", header.id(), "session", header.sessionId());
        var participants = participants(jdbc.queryForList(
                "select event_id from slotwise.assignment where schedule_variant_id = :variant", params, Long.class));
        var commissions = commissions(Set.of(header.id()));
        var assignments = jdbc.query("""
                select a.event_id, e.event_type_id, e.title, e.format, e.duration_minutes, a.status, a.starts_at,
                       a.ends_at, r.id, r.name, r.building, r.online, a.locked, a.version, a.explanation::text
                from slotwise.assignment a
                join slotwise.event e on e.id = a.event_id
                left join slotwise.room r on r.id = a.room_id
                where a.schedule_variant_id = :variant order by a.event_id
                """, params, (rs, row) -> {
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
        var members = new HashMap<EventKey, Set<Long>>();
        jdbc.query("""
                select v.id, a.event_id, a.starts_at, a.room_id, m.account_id
                from slotwise.schedule_variant v
                join lateral (select id from slotwise.schedule_variant p
                              where p.planning_session_id = v.planning_session_id and p.status = 'SUPERSEDED'
                              order by p.published_at desc limit 1) previous on true
                join slotwise.assignment a on a.schedule_variant_id = previous.id and a.status = 'ASSIGNED'
                left join slotwise.assignment_commission_member m
                       on m.schedule_variant_id = previous.id and m.event_id = a.event_id
                where v.id in (:variants)
                """, Map.of("variants", variants), rs -> {
            long variant = rs.getLong(1);
            long event = rs.getLong(2);
            var commission = members.computeIfAbsent(new EventKey(variant, event), key -> new HashSet<>());
            long member = rs.getLong(5);
            if (!rs.wasNull()) {
                commission.add(member);
            }
            result.computeIfAbsent(variant, id -> new HashMap<>())
                    .put(event, new Placement(instant(rs, 3), rs.getLong(4), commission));
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

    private record Placement(Instant startsAt, long roomId, Set<Long> commission) {
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
