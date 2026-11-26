package ru.vk.itmo.scheduler.slotwise.eventtype;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.EventTypeDependency;
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.EventTypeResponse;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventTypeCatalog {
    private final JdbcClient jdbc;

    private static final String SELECT = """
            select t.*,
                   array(select participant_role from slotwise.event_type_participant_role
                         where event_type_id=t.id order by participant_role) as roles,
                   array(select equipment_id from slotwise.event_type_equipment
                         where event_type_id=t.id order by equipment_id) as equipment
            from slotwise.event_type t
            """;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<EventTypeResponse> list(String search, Boolean active, int page, int size) {
        String filters = """
                 where (:search = '' or strpos(lower(t.name), lower(:search)) > 0)
                   and (cast(:active as boolean) is null or t.active=:active)
                """;
        String query = search == null ? "" : search.strip();
        long total = jdbc.sql("select count(*) from slotwise.event_type t" + filters)
                .param("search", query).param("active", active).query(Long.class).single();
        var rows = jdbc.sql(SELECT + filters + " order by t.id limit :size offset :offset")
                .param("search", query).param("active", active).param("size", size).param("offset", (long) page * size)
                .query(EventTypeCatalog::template).list();
        return new PageResponse<>(rows.stream().map(this::response).toList(), total, page, size);
    }

    @Transactional(readOnly = true)
    public EventTypeResponse get(long id) {
        return jdbc.sql(SELECT + " where t.id=:id").param("id", id).query(EventTypeCatalog::template).optional()
                .map(this::response).orElseThrow(() -> new PlanningException(HttpStatus.NOT_FOUND,
                        "EVENT_TYPE_NOT_FOUND", "Тип события не найден"));
    }

    private EventTypeResponse response(Template t) {
        var dependencies = jdbc.sql("""
                select predecessor_type_id,min_gap_days,max_gap_days from slotwise.event_type_dependency
                where successor_type_id=:id order by predecessor_type_id
                """).param("id", t.id()).query((rs, row) -> new EventTypeDependency(rs.getLong(1), rs.getInt(2),
                        rs.getObject(3, Integer.class))).list();
        return new EventTypeResponse(t.id(), t.name(), t.duration(), t.granularity(), t.preparationDays(), t.format(),
                t.commissionSize(), t.requiresChair(), t.active(), t.roles(), t.equipment(), dependencies, t.version());
    }

    private static Template template(ResultSet rs, int row) throws SQLException {
        var roles = Arrays.stream((String[]) rs.getArray("roles").getArray()).map(ParticipantRole::valueOf).toList();
        return new Template(rs.getLong("id"), rs.getString("name"), rs.getInt("default_duration_minutes"),
                EventGranularity.valueOf(rs.getString("granularity")), rs.getInt("preparation_days"),
                EventFormat.valueOf(rs.getString("default_format")), rs.getInt("commission_size"),
                rs.getBoolean("requires_chair"), rs.getBoolean("active"), roles,
                List.of((Long[]) rs.getArray("equipment").getArray()), rs.getLong("version"));
    }

    private record Template(long id, String name, int duration, EventGranularity granularity, int preparationDays,
                            EventFormat format, int commissionSize, boolean requiresChair, boolean active,
                            List<ParticipantRole> roles, List<Long> equipment, long version) {
    }
}
