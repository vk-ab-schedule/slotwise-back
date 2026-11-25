package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.EventTypeResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventParticipant;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventPredecessor;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdateEventRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.conflict;
import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.invalid;

@Repository
@RequiredArgsConstructor
class PlanningEventStore {
    private final JdbcClient jdbc;

    private static final String EVENTS = """
            select e.*,coalesce(e.granularity,t.granularity) as event_granularity,
                   array(select equipment_id from slotwise.event_equipment where event_id=e.id) as equipment
            from slotwise.event e join slotwise.event_type t on t.id=e.event_type_id
            where e.planning_session_id=:id
            """;

    PageResponse<EventResponse> list(long id, int page, int size) {
        long total = jdbc.sql("select count(*) from slotwise.event where planning_session_id=:id")
                .param("id", id).query(Long.class).single();
        var rows = jdbc.sql(EVENTS + " order by e.id limit :size offset :offset").param("id", id)
                .param("size", size).param("offset", (long) page * size).query(PlanningEventStore::map).list();
        return new PageResponse<>(responses(rows), total, page, size);
    }

    EventResponse get(long id, long event) {
        var row = jdbc.sql(EVENTS + " and e.id=:event").param("id", id).param("event", event)
                .query(PlanningEventStore::map).optional().orElseThrow(() -> new PlanningException(
                        HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Событие не найдено"));
        return responses(List.of(row)).getFirst();
    }

    List<EventResponse> get(long id, List<Long> events) {
        var rows = jdbc.sql(EVENTS + " and e.id in (:events) order by e.id").param("id", id).param("events", events)
                .query(PlanningEventStore::map).list();
        return responses(rows);
    }

    private List<EventResponse> responses(List<EventRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        var ids = rows.stream().map(EventRow::id).toList();
        var participants = new HashMap<Long, List<EventParticipant>>();
        jdbc.sql("""
                select event_id,account_id,participant_role from slotwise.event_participant
                where event_id in (:ids) order by event_id,account_id
                """).param("ids", ids).query((rs, row) -> {
                    participants.computeIfAbsent(rs.getLong(1), ignored -> new ArrayList<>())
                            .add(new EventParticipant(rs.getLong(2), ParticipantRole.valueOf(rs.getString(3))));
                    return rs.getLong(1);
                }).list();
        var dependencies = new HashMap<Long, List<EventPredecessor>>();
        jdbc.sql("""
                select successor_event_id,predecessor_event_id,min_gap_days,max_gap_days from slotwise.event_dependency
                where successor_event_id in (:ids) order by successor_event_id,predecessor_event_id
                """).param("ids", ids).query((rs, row) -> {
                    dependencies.computeIfAbsent(rs.getLong(1), ignored -> new ArrayList<>())
                            .add(new EventPredecessor(rs.getLong(2), rs.getInt(3), rs.getObject(4, Integer.class)));
                    return rs.getLong(1);
                }).list();
        return rows.stream().map(e -> new EventResponse(e.id(), e.session(), e.type(), e.discipline(), e.group(),
                e.title(), e.description(), e.duration(), e.format(), e.granularity(), e.preparationDays(),
                e.commissionSize(), e.requiresChair(), participants.getOrDefault(e.id(), List.of()), e.equipment(),
                dependencies.getOrDefault(e.id(), List.of()), e.complete(), e.version())).toList();
    }

    long create(long id, EventTypeResponse type, Long discipline, Long group, String title, boolean complete) {
        long event = jdbc.sql("""
                insert into slotwise.event (planning_session_id,event_type_id,discipline_id,study_group_id,title,
                    duration_minutes,format,granularity,preparation_days,commission_size,requires_chair,complete,
                    use_template_dependencies)
                values (:id,:type,:discipline,:group,:title,:duration,:format,:granularity,:preparation,
                    :commission,:chair,:complete,true) returning id
                """).param("id", id).param("type", type.id()).param("discipline", discipline).param("group", group)
                .param("title", title).param("duration", type.defaultDurationMinutes()).param("format", type.defaultFormat().name())
                .param("granularity", type.granularity().name()).param("preparation", type.preparationDays())
                .param("commission", type.commissionSize()).param("chair", type.requiresChair()).param("complete", complete)
                .query(Long.class).single();
        for (var role : type.participantRoles()) {
            jdbc.sql("insert into slotwise.event_required_role values (:event,:role)")
                    .param("event", event).param("role", role.name()).update();
        }
        for (var dependency : type.dependencies()) {
            jdbc.sql("insert into slotwise.event_template_dependency values (:event,:type,:min,:max)")
                    .param("event", event).param("type", dependency.predecessorTypeId())
                    .param("min", dependency.minGapDays()).param("max", dependency.maxGapDays()).update();
        }
        writeEquipment(event, Set.copyOf(type.equipmentIds()));
        return event;
    }

    void update(long id, long event, UpdateEventRequest request, boolean complete, boolean dependenciesChanged) {
        jdbc.sql("""
                update slotwise.event set title=:title,description=:description,duration_minutes=:duration,format=:format,
                    preparation_days=:preparation,commission_size=:commission,requires_chair=:chair,complete=:complete,
                    use_template_dependencies=use_template_dependencies and not :dependenciesChanged,version=version+1
                where planning_session_id=:id and id=:event
                """).param("id", id).param("event", event).param("title", request.title().strip())
                .param("description", request.description()).param("duration", request.durationMinutes())
                .param("format", request.format().name()).param("preparation", request.preparationDays())
                .param("commission", request.commissionSize()).param("chair", request.requiresChair())
                .param("complete", complete).param("dependenciesChanged", dependenciesChanged).update();
        jdbc.sql("delete from slotwise.event_equipment where event_id=:event").param("event", event).update();
        writeEquipment(event, request.equipmentIds());
        if (dependenciesChanged) {
            replaceDependencies(id, event, request.dependencies());
            requireAcyclic(id);
        }
    }

    void writeParticipants(long id, long event, List<EventParticipant> participants) {
        jdbc.sql("delete from slotwise.event_participant where event_id=:event").param("event", event).update();
        for (var participant : participants) {
            jdbc.sql("""
                    insert into slotwise.event_participant (planning_session_id,event_id,account_id,participant_role)
                    values (:id,:event,:account,:role)
                    """).param("id", id).param("event", event).param("account", participant.accountId())
                    .param("role", participant.role().name()).update();
        }
    }

    List<ParticipantRole> requiredRoles(long event) {
        return jdbc.sql("""
                select participant_role from slotwise.event_required_role where event_id=:event
                union all
                select t.participant_role from slotwise.event e join slotwise.event_type_participant_role t
                    on t.event_type_id=e.event_type_id
                where e.id=:event and e.granularity is null
                """).param("event", event).query(String.class).list().stream().map(ParticipantRole::valueOf).toList();
    }

    void addTemplateEdges(long session, List<Long> created) {
        jdbc.sql("""
                with added as (
                    insert into slotwise.event_dependency
                        (planning_session_id,predecessor_event_id,successor_event_id,min_gap_days,max_gap_days)
                    select :id,p.id,s.id,d.min_gap_days,d.max_gap_days
                    from slotwise.event_template_dependency d join slotwise.event s on s.id=d.event_id
                    join slotwise.event p on p.planning_session_id=s.planning_session_id and p.event_type_id=d.predecessor_type_id
                    where s.planning_session_id=:id and s.use_template_dependencies
                      and (s.id in (:created) or p.id in (:created))
                      and (s.discipline_id is null or p.discipline_id is null or s.discipline_id=p.discipline_id)
                      and exists (
                        select from slotwise.event_participant sp join slotwise.event_participant pp
                            on sp.account_id=pp.account_id and pp.event_id=p.id and pp.participant_role='STUDENT'
                        where sp.event_id=s.id and sp.participant_role='STUDENT')
                    on conflict do nothing returning successor_event_id
                )
                update slotwise.event set version=version+1
                where id in (select successor_event_id from added) and id not in (:created)
                """).param("id", session).param("created", created).update();
        requireAcyclic(session);
    }

    void delete(long id, long event) {
        if (jdbc.sql("""
                select exists(select from slotwise.assignment where planning_session_id=:id and event_id=:event)
                    or exists(select from slotwise.change_request where planning_session_id=:id and event_id=:event)
                """).param("id", id).param("event", event).query(Boolean.class).single()) {
            throw conflict("EVENT_IN_USE", "Событие используется в расписании или запросе на перенос");
        }
        jdbc.sql("""
                with removed as (
                    delete from slotwise.event_dependency
                    where planning_session_id=:id and (predecessor_event_id=:event or successor_event_id=:event)
                    returning successor_event_id)
                update slotwise.event set version=version+1
                where id in (select successor_event_id from removed) and id<>:event
                """).param("id", id).param("event", event).update();
        jdbc.sql("delete from slotwise.event_participant where event_id=:event").param("event", event).update();
        jdbc.sql("delete from slotwise.event_equipment where event_id=:event").param("event", event).update();
        jdbc.sql("delete from slotwise.event where id=:event").param("event", event).update();
    }

    private void writeEquipment(long event, Set<Long> equipment) {
        for (long item : equipment) {
            jdbc.sql("insert into slotwise.event_equipment values (:event,:equipment)")
                    .param("event", event).param("equipment", item).update();
        }
    }

    void requirePredecessors(long session, Set<Long> ids) {
        if (!ids.isEmpty() && jdbc.sql("""
                select count(*) from slotwise.event where planning_session_id=:id and id in (:events)
                """).param("id", session).param("events", ids).query(Long.class).single() != ids.size()) {
            throw invalid("INVALID_EVENT_DEPENDENCY", "Предшествующее событие должно быть в той же сессии");
        }
    }

    private void replaceDependencies(long id, long event, List<EventPredecessor> dependencies) {
        jdbc.sql("delete from slotwise.event_dependency where successor_event_id=:event").param("event", event).update();
        for (var dependency : dependencies) {
            jdbc.sql("""
                    insert into slotwise.event_dependency
                        (planning_session_id,predecessor_event_id,successor_event_id,min_gap_days,max_gap_days)
                    values (:id,:predecessor,:event,:min,:max)
                    """).param("id", id).param("event", event).param("predecessor", dependency.predecessorEventId())
                    .param("min", dependency.minGapDays()).param("max", dependency.maxGapDays()).update();
        }
    }

    private void requireAcyclic(long session) {
        var successors = new HashMap<Long, List<Long>>();
        var incoming = new HashMap<Long, Integer>();
        jdbc.sql("""
                select predecessor_event_id,successor_event_id from slotwise.event_dependency where planning_session_id=:id
                """).param("id", session).query((rs, row) -> {
                    long from = rs.getLong(1);
                    long to = rs.getLong(2);
                    successors.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
                    incoming.putIfAbsent(from, 0);
                    incoming.merge(to, 1, Integer::sum);
                    return to;
                }).list();
        var ready = new ArrayDeque<Long>();
        incoming.forEach((event, count) -> {
            if (count == 0) {
                ready.add(event);
            }
        });
        int processed = 0;
        while (!ready.isEmpty()) {
            long event = ready.remove();
            processed++;
            for (long next : successors.getOrDefault(event, List.of())) {
                if (incoming.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        if (processed != incoming.size()) {
            throw invalid("CYCLIC_EVENT_DEPENDENCIES", "Зависимости образуют цикл");
        }
    }

    private static EventRow map(ResultSet rs, int row) throws SQLException {
        return new EventRow(rs.getLong("id"), rs.getLong("planning_session_id"), rs.getLong("event_type_id"),
                rs.getObject("discipline_id", Long.class), rs.getObject("study_group_id", Long.class),
                rs.getString("title"), rs.getString("description"), rs.getInt("duration_minutes"),
                EventFormat.valueOf(rs.getString("format")), EventGranularity.valueOf(rs.getString("event_granularity")),
                rs.getInt("preparation_days"), rs.getInt("commission_size"), rs.getBoolean("requires_chair"),
                Set.of((Long[]) rs.getArray("equipment").getArray()), rs.getBoolean("complete"), rs.getLong("version"));
    }

    private record EventRow(long id, long session, long type, Long discipline, Long group, String title,
                            String description, int duration, EventFormat format, EventGranularity granularity,
                            int preparationDays, int commissionSize, boolean requiresChair, Set<Long> equipment,
                            boolean complete, long version) {
    }
}
