package ru.vk.itmo.scheduler.slotwise.university;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDisciplineResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityEquipmentResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityGroupResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityRoomResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversitySupervisionResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTeachingResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTimetableEntryResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class UniversityCatalogService {
    private final NamedParameterJdbcTemplate jdbc;

    private static final Catalog<PersonSummary> PEOPLE = new Catalog<>(
            "a.id, a.last_name, a.first_name, a.patronymic, a.role",
            "slotwise.account a", "a.last_name, a.first_name, a.id",
            (rs, _) -> new PersonSummary(rs.getLong("id"), rs.getString("last_name"), rs.getString("first_name"),
                    rs.getString("patronymic"), AccountRole.valueOf(rs.getString("role"))));

    private static final Catalog<UniversityGroupResponse> GROUPS = new Catalog<>("""
            g.id, g.external_id, g.name, g.active, g.version,
            array(select m.account_id from slotwise.study_group_member m
                  where m.study_group_id = g.id order by m.account_id) as student_ids
            """, "slotwise.study_group g", "g.name, g.id",
            (rs, _) -> new UniversityGroupResponse(rs.getLong("id"), rs.getString("external_id"),
                    rs.getString("name"), rs.getBoolean("active"), rs.getLong("version"), ids(rs, "student_ids")));

    private static final Catalog<UniversityRoomResponse> ROOMS = new Catalog<>("""
            r.id, r.external_id, r.name, r.building, r.capacity, r.online, r.active, r.version,
            array(select re.equipment_id from slotwise.room_equipment re
                  where re.room_id = r.id order by re.equipment_id) as equipment_ids
            """, "slotwise.room r", "r.name, r.id",
            (rs, _) -> new UniversityRoomResponse(rs.getLong("id"), rs.getString("external_id"),
                    rs.getString("name"), rs.getString("building"), rs.getObject("capacity", Integer.class),
                    rs.getBoolean("online"), rs.getBoolean("active"), rs.getLong("version"), ids(rs, "equipment_ids")));

    private static final Catalog<UniversityDisciplineResponse> DISCIPLINES = new Catalog<>(
            "d.id, d.external_id, d.name, d.active, d.version", "slotwise.discipline d", "d.name, d.id",
            (rs, _) -> new UniversityDisciplineResponse(rs.getLong("id"), rs.getString("external_id"),
                    rs.getString("name"), rs.getBoolean("active"), rs.getLong("version")));

    private static final Catalog<UniversityEquipmentResponse> EQUIPMENT = new Catalog<>(
            "e.id, e.name", "slotwise.equipment e", "e.name, e.id",
            (rs, _) -> new UniversityEquipmentResponse(rs.getLong("id"), rs.getString("name")));

    private static final Catalog<UniversityTeachingResponse> TEACHINGS = new Catalog<>(
            "t.id, t.teacher_id, t.discipline_id, t.study_group_id", """
            slotwise.teaching t
            join slotwise.account a on a.id = t.teacher_id
            join slotwise.discipline d on d.id = t.discipline_id
            join slotwise.study_group g on g.id = t.study_group_id
            """, "t.id",
            (rs, _) -> new UniversityTeachingResponse(rs.getLong("id"), rs.getLong("teacher_id"),
                    rs.getLong("discipline_id"), rs.getLong("study_group_id")));

    private static final Catalog<UniversitySupervisionResponse> SUPERVISIONS = new Catalog<>(
            "s.student_id, s.supervisor_id", """
            slotwise.supervision s
            join slotwise.account student on student.id = s.student_id
            join slotwise.account supervisor on supervisor.id = s.supervisor_id
            """, "s.student_id, s.supervisor_id",
            (rs, _) -> new UniversitySupervisionResponse(rs.getLong("student_id"), rs.getLong("supervisor_id")));

    private static final Catalog<UniversityTimetableEntryResponse> TIMETABLE = new Catalog<>(
            "t.id, t.external_id, t.account_id, t.room_id, t.starts_at, t.ends_at, t.description", """
            slotwise.timetable_entry t
            left join slotwise.account a on a.id = t.account_id
            left join slotwise.room r on r.id = t.room_id
            """, "t.starts_at, t.id",
            (rs, _) -> new UniversityTimetableEntryResponse(rs.getLong("id"), rs.getString("external_id"),
                    rs.getObject("account_id", Long.class), rs.getObject("room_id", Long.class),
                    rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                    rs.getString("description")));


    public PageResponse<PersonSummary> listPeople(int page, int size, String search, AccountRole role,
                                                  Long groupId, Boolean enabled) {
        return page(PEOPLE, page, size, new Filters()
                .search("a.last_name, a.first_name, a.patronymic, a.first_name, a.last_name", search)
                .add("a.role = :role", "role", role == null ? null : role.name())
                .add("""
                        exists (select 1 from slotwise.study_group_member m
                                where m.account_id = a.id and m.study_group_id = :groupId)
                        """, "groupId", groupId)
                .add("a.enabled = :enabled", "enabled", enabled));
    }

    public PageResponse<UniversityGroupResponse> listGroups(int page, int size, String search,
                                                            Long studentId, Boolean active) {
        return page(GROUPS, page, size, new Filters()
                .search("g.name, g.external_id", search)
                .add("""
                        exists (select 1 from slotwise.study_group_member m
                                where m.study_group_id = g.id and m.account_id = :studentId)
                        """, "studentId", studentId)
                .add("g.active = :active", "active", active));
    }

    public PageResponse<UniversityRoomResponse> listRooms(int page, int size, String search, Long equipmentId,
                                                          Integer minCapacity, Boolean online, String building,
                                                          Boolean active) {
        return page(ROOMS, page, size, new Filters()
                .search("r.name, r.external_id, r.building", search)
                .add("""
                        exists (select 1 from slotwise.room_equipment re
                                where re.room_id = r.id and re.equipment_id = :equipmentId)
                        """, "equipmentId", equipmentId)
                .add("r.capacity >= :minCapacity", "minCapacity", minCapacity)
                .add("r.online = :online", "online", online)
                .add("lower(r.building) = lower(:building)", "building", building)
                .add("r.active = :active", "active", active));
    }

    public PageResponse<UniversityDisciplineResponse> listDisciplines(int page, int size, String search,
                                                                      Boolean active) {
        return page(DISCIPLINES, page, size, new Filters().search("d.name, d.external_id", search)
                .add("d.active = :active", "active", active));
    }

    public PageResponse<UniversityEquipmentResponse> listEquipment(int page, int size, String search) {
        return page(EQUIPMENT, page, size, new Filters().search("e.name", search));
    }

    public PageResponse<UniversityTeachingResponse> listTeachings(int page, int size, String search,
                                                                  Long teacherId, Long disciplineId, Long groupId) {
        return page(TEACHINGS, page, size, new Filters()
                .search("a.last_name, a.first_name, a.patronymic, d.name, g.name", search)
                .add("t.teacher_id = :teacherId", "teacherId", teacherId)
                .add("t.discipline_id = :disciplineId", "disciplineId", disciplineId)
                .add("t.study_group_id = :groupId", "groupId", groupId));
    }

    public PageResponse<UniversitySupervisionResponse> listSupervisions(int page, int size, String search,
                                                                        Long studentId, Long supervisorId) {
        return page(SUPERVISIONS, page, size, new Filters()
                .search("""
                        student.first_name, student.last_name, student.patronymic,
                        supervisor.first_name, supervisor.last_name, supervisor.patronymic
                        """, search)
                .add("s.student_id = :studentId", "studentId", studentId)
                .add("s.supervisor_id = :supervisorId", "supervisorId", supervisorId));
    }

    public PageResponse<UniversityTimetableEntryResponse> listTimetable(int page, int size, String search,
                                                                        Long accountId, Long roomId,
                                                                        Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_TIMETABLE_RANGE", "Начало периода должно быть раньше конца");
        }
        return page(TIMETABLE, page, size, new Filters()
                .search("t.description, t.external_id, a.last_name, a.first_name, r.name", search)
                .add("t.account_id = :accountId", "accountId", accountId)
                .add("t.room_id = :roomId", "roomId", roomId)
                .add("t.ends_at > :startsAt", "startsAt", startsAt == null ? null : startsAt.atOffset(ZoneOffset.UTC))
                .add("t.starts_at < :endsAt", "endsAt", endsAt == null ? null : endsAt.atOffset(ZoneOffset.UTC)));
    }

    public PersonSummary getPerson(long id) {
        return findRecord(PEOPLE, new Filters().add("a.id = :id", "id", id));
    }

    public UniversityGroupResponse getGroup(long id) {
        return findRecord(GROUPS, new Filters().add("g.id = :id", "id", id));
    }

    public UniversityRoomResponse getRoom(long id) {
        return findRecord(ROOMS, new Filters().add("r.id = :id", "id", id));
    }

    public UniversityDisciplineResponse getDiscipline(long id) {
        return findRecord(DISCIPLINES, new Filters().add("d.id = :id", "id", id));
    }

    public UniversityEquipmentResponse getEquipment(long id) {
        return findRecord(EQUIPMENT, new Filters().add("e.id = :id", "id", id));
    }

    public UniversityTeachingResponse getTeaching(long id) {
        return findRecord(TEACHINGS, new Filters().add("t.id = :id", "id", id));
    }

    public UniversitySupervisionResponse getSupervision(long studentId, long supervisorId) {
        return findRecord(SUPERVISIONS, new Filters().add("s.student_id = :studentId", "studentId", studentId)
                .add("s.supervisor_id = :supervisorId", "supervisorId", supervisorId));
    }

    public UniversityTimetableEntryResponse getTimetableEntry(long id) {
        return findRecord(TIMETABLE, new Filters().add("t.id = :id", "id", id));
    }

    private <T> PageResponse<T> page(Catalog<T> catalog, int page, int size, Filters filters) {
        String from = " from " + catalog.from() + filters.where();
        long total = jdbc.queryForObject("select count(*)" + from, filters.params, Long.class);
        long offset = (long) page * size;
        if (offset >= total) {
            return new PageResponse<>(List.of(), total, page, size);
        }
        filters.params.addValue("size", size).addValue("offset", offset);
        List<T> items = jdbc.query("select " + catalog.columns() + from + " order by " + catalog.orderBy()
                + " limit :size offset :offset", filters.params, catalog.mapper());
        return new PageResponse<>(items, total, page, size);
    }

    private <T> T findRecord(Catalog<T> catalog, Filters filters) {
        return jdbc.query("select " + catalog.columns() + " from " + catalog.from() + filters.where(),
                        filters.params, catalog.mapper()).stream().findFirst()
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "UNIVERSITY_RECORD_NOT_FOUND", "Запись справочника не найдена"));
    }

    private static List<Long> ids(ResultSet rs, String column) throws SQLException {
        var array = rs.getArray(column);
        try {
            return List.of((Long[]) array.getArray());
        } finally {
            array.free();
        }
    }

    private static ErrorResponseException problem(HttpStatus status, String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setProperty("code", code);
        return new ErrorResponseException(status, problem, null);
    }

    private record Catalog<T>(String columns, String from, String orderBy, RowMapper<T> mapper) {
    }

    private static final class Filters {
        private final List<String> predicates = new ArrayList<>();
        private final MapSqlParameterSource params = new MapSqlParameterSource();

        private Filters add(String predicate, String name, Object value) {
            if (value != null) {
                predicates.add(predicate);
                params.addValue(name, value);
            }
            return this;
        }

        private Filters search(String columns, String search) {
            if (search == null || search.isBlank()) {
                return this;
            }
            String escaped = search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            return add("lower(concat_ws(' ', " + columns + ")) like lower(:search) escape '!'", "search",
                    "%" + escaped + "%");
        }

        private String where() {
            return predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        }
    }
}
