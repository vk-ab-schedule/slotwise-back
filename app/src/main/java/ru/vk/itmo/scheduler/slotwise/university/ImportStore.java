package ru.vk.itmo.scheduler.slotwise.university;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RequiredArgsConstructor
class ImportStore {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    void lockImportTables() {
        jdbc.execute("""
                lock table slotwise.account, slotwise.study_group, slotwise.study_group_member,
                    slotwise.room, slotwise.equipment, slotwise.room_equipment, slotwise.discipline,
                    slotwise.teaching, slotwise.supervision, slotwise.timetable_entry
                in share row exclusive mode
                """);
    }

    Row<Account> account(String externalId) {
        return one("""
                select id, email, first_name, last_name, patronymic, role, time_zone, password_hash
                from slotwise.account where external_id = ?
                """, (rs, _) -> new Row<>(rs.getLong("id"), new Account(rs.getString("email"),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("patronymic"),
                AccountRole.valueOf(rs.getString("role")), rs.getString("time_zone"), rs.getString("password_hash"))),
                externalId);
    }

    Row<Group> group(String externalId) {
        return one("""
                select g.id, g.name, array(select m.account_id from slotwise.study_group_member m
                    where m.study_group_id = g.id) as students
                from slotwise.study_group g where g.external_id = ?
                """, (rs, _) -> new Row<>(rs.getLong("id"), new Group(rs.getString("name"),
                Set.of((Long[]) rs.getArray("students").getArray()))), externalId);
    }

    Row<Room> room(String externalId) {
        return one("""
                select r.id, r.name, r.building, r.online, r.capacity,
                    array(select e.name from slotwise.equipment e join slotwise.room_equipment re
                          on re.equipment_id = e.id where re.room_id = r.id) as equipment
                from slotwise.room r where r.external_id = ?
                """, (rs, _) -> new Row<>(rs.getLong("id"), new Room(rs.getString("name"), rs.getString("building"),
                rs.getBoolean("online"), rs.getObject("capacity", Integer.class),
                Set.of((String[]) rs.getArray("equipment").getArray()))), externalId);
    }

    Row<Discipline> discipline(String externalId) {
        return one("select id, name from slotwise.discipline where external_id = ?",
                (rs, _) -> new Row<>(rs.getLong("id"), new Discipline(rs.getString("name"))), externalId);
    }

    boolean emailUsedByOther(String email, Long id) {
        return exists("""
                select exists(select from slotwise.account
                    where lower(btrim(email)) = ? and id is distinct from ?)
                """, email, id);
    }

    boolean groupNameUsedByOther(String name, Long id) {
        return exists("""
                select exists(select from slotwise.study_group where name = ? and id is distinct from ?)
                """, name, id);
    }

    void saveAccount(String externalId, Row<Account> previous, Row<Account> row) {
        var a = row.value;
        var fields = new Object[]{a.email(), a.firstName(), a.lastName(), a.patronymic(), a.role().name(),
                a.timeZone(), a.passwordHash(), previous == null ? externalId : previous.id};
        if (previous == null) {
            row.id = insert("""
                    insert into slotwise.account (email, first_name, last_name, patronymic, role, time_zone,
                        password_hash, external_id) values (?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, fields);
        } else {
            jdbc.update("""
                    update slotwise.account set email = ?, first_name = ?, last_name = ?, patronymic = ?,
                        role = ?, time_zone = ?, password_hash = ?, version = version + 1, updated_at = now()
                    where id = ?
                    """, fields);
            if (previous.value.role() != a.role() || !Objects.equals(previous.value.passwordHash(), a.passwordHash())) {
                jdbc.update("delete from slotwise.spring_session where principal_name = ?", row.id.toString());
            }
        }
    }

    void saveGroup(String externalId, Row<Group> previous, Row<Group> row, List<Long> students) {
        if (previous == null) {
            row.id = insert("insert into slotwise.study_group (external_id, name) values (?, ?) returning id",
                    externalId, row.value.name());
        } else {
            jdbc.update("update slotwise.study_group set name = ?, version = version + 1 where id = ?",
                    row.value.name(), row.id);
            if (previous.value.students().equals(new HashSet<>(students))) {
                return;
            }
            jdbc.update("delete from slotwise.study_group_member where study_group_id = ?", row.id);
        }
        if (!students.isEmpty()) {
            jdbc.batchUpdate("insert into slotwise.study_group_member (study_group_id, account_id) values (?, ?)",
                    students.stream().map(student -> new Object[]{row.id, student}).toList());
        }
    }

    void saveRoom(String externalId, Row<Room> previous, Row<Room> row) {
        var r = row.value;
        if (previous == null) {
            row.id = insert("""
                    insert into slotwise.room (name, building, online, capacity, external_id)
                    values (?, ?, ?, ?, ?) returning id
                    """, r.name(), r.building(), r.online(), r.capacity(), externalId);
        } else {
            jdbc.update("""
                    update slotwise.room set name = ?, building = ?, online = ?, capacity = ?, version = version + 1
                    where id = ?
                    """, r.name(), r.building(), r.online(), r.capacity(), row.id);
            if (previous.value.equipment().equals(r.equipment())) {
                return;
            }
            jdbc.update("delete from slotwise.room_equipment where room_id = ?", row.id);
        }
        for (String name : r.equipment()) {
            jdbc.update("insert into slotwise.equipment (name) values (?) on conflict (name) do nothing", name);
            jdbc.update("""
                    insert into slotwise.room_equipment (room_id, equipment_id)
                    select ?, id from slotwise.equipment where name = ?
                    """, row.id, name);
        }
    }

    void saveDiscipline(String externalId, Row<Discipline> previous, Row<Discipline> row) {
        if (previous == null) {
            row.id = insert("insert into slotwise.discipline (external_id, name) values (?, ?) returning id",
                    externalId, row.value.name());
        } else {
            jdbc.update("update slotwise.discipline set name = ?, version = version + 1 where id = ?",
                    row.value.name(), row.id);
        }
    }

    boolean hasTeaching(Row<Account> teacher, Row<Discipline> discipline, Row<Group> group) {
        return teacher.id != null && discipline.id != null && group.id != null && exists("""
                select exists(select from slotwise.teaching
                    where teacher_id = ? and discipline_id = ? and study_group_id = ?)
                """, teacher.id, discipline.id, group.id);
    }

    void addTeaching(long teacher, long discipline, long group) {
        jdbc.update("insert into slotwise.teaching (teacher_id, discipline_id, study_group_id) values (?, ?, ?)",
                teacher, discipline, group);
    }

    boolean hasSupervision(Row<Account> student, Row<Account> supervisor) {
        return student.id != null && supervisor.id != null && exists("""
                select exists(select from slotwise.supervision where student_id = ? and supervisor_id = ?)
                """, student.id, supervisor.id);
    }

    void addSupervision(long student, long supervisor) {
        jdbc.update("insert into slotwise.supervision (student_id, supervisor_id) values (?, ?)", student, supervisor);
    }

    boolean hasTimetable(Row<Account> account, Row<Room> room, Instant start, Instant end) {
        Long resource = account != null ? account.id : room.id;
        if (resource == null) {
            return false;
        }
        String column = account != null ? "account_id" : "room_id";
        return exists("""
                select exists(select from slotwise.timetable_entry where %s = ? and starts_at = ? and ends_at = ?)
                """.formatted(column), resource, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC));
    }

    boolean roomHasTimetable(long room) {
        return exists("select exists(select from slotwise.timetable_entry where room_id = ?)", room);
    }

    void addTimetable(Long account, Long room, Instant start, Instant end) {
        jdbc.update("""
                insert into slotwise.timetable_entry (account_id, room_id, starts_at, ends_at)
                values (?, ?, ?, ?)
                """, account, room, start.atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC));
    }

    void invalidateSessions(Set<Long> accounts, Set<Long> groups, Set<Long> rooms, Set<Long> disciplines) {
        if (accounts.isEmpty() && groups.isEmpty() && rooms.isEmpty() && disciplines.isEmpty()) {
            return;
        }
        namedJdbc.update("""
                update slotwise.planning_session s set input_version = s.input_version + 1, version = s.version + 1
                where s.organizer_id in (:accounts)
                   or s.id in (select planning_session_id from slotwise.session_participant where account_id in (:accounts))
                   or s.id in (select planning_session_id from slotwise.session_room where room_id in (:rooms))
                   or s.id in (select planning_session_id from slotwise.event
                               where study_group_id in (:groups) or discipline_id in (:disciplines))
                """, Map.of("accounts", nonEmpty(accounts), "groups", nonEmpty(groups),
                "rooms", nonEmpty(rooms), "disciplines", nonEmpty(disciplines)));
    }

    private static Iterable<Long> nonEmpty(Set<Long> ids) {
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }

    private Long insert(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private <T> Row<T> one(String sql, RowMapper<Row<T>> rowMapper, String externalId) {
        return DataAccessUtils.singleResult(jdbc.query(sql, rowMapper, externalId));
    }

    static final class Row<T> {
        Long id;
        final T value;

        Row(Long id, T value) {
            this.id = id;
            this.value = value;
        }
    }

    record Account(String email, String firstName, String lastName, String patronymic, AccountRole role,
                   String timeZone, String passwordHash) {
        @Override
        public String toString() {
            return "Imported account";
        }
    }

    record Group(String name, Set<Long> students) {}

    record Room(String name, String building, boolean online, Integer capacity, Set<String> equipment) {}

    record Discipline(String name) {}
}
