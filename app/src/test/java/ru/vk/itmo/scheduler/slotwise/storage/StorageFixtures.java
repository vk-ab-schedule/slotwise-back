package ru.vk.itmo.scheduler.slotwise.storage;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

final class StorageFixtures {
    private final JdbcTemplate jdbc;

    StorageFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    long session() {
        long owner = jdbc.queryForObject("""
                insert into slotwise.account(email, first_name, last_name, role)
                values (?, 'Test', 'Organizer', 'ORGANIZER') returning id
                """, Long.class, UUID.randomUUID() + "@example.org");
        return jdbc.queryForObject("""
                insert into slotwise.planning_session(organizer_id, name, starts_at, ends_at,
                    collection_starts_at, collection_ends_at)
                values (?, 'Test', '2027-01-15T09:00Z', '2027-01-16T19:00Z',
                    '2027-01-10T09:00Z', '2027-01-11T19:00Z') returning id
                """, Long.class, owner);
    }

    long snapshot(long session, long version) {
        return jdbc.queryForObject("""
                insert into slotwise.input_snapshot(planning_session_id, input_version, problem)
                values (?, ?, '{}') returning id
                """, Long.class, session, version);
    }

    AssignmentFixture assignment() {
        long session = session();
        long snapshot = snapshot(session, 0);
        long variant = jdbc.queryForObject("""
                insert into slotwise.schedule_variant(planning_session_id, input_snapshot_id, solve_status)
                values (?, ?, 'FEASIBLE') returning id
                """, Long.class, session, snapshot);
        long type = jdbc.queryForObject("select min(id) from slotwise.event_type", Long.class);
        long event = jdbc.queryForObject("""
                insert into slotwise.event(planning_session_id, event_type_id, title, duration_minutes, format)
                values (?, ?, 'Test', 30, 'ONLINE') returning id
                """, Long.class, session, type);
        long room = jdbc.queryForObject("insert into slotwise.room(name, online) values ('Online', true) returning id", Long.class);
        jdbc.update("insert into slotwise.session_room values (?, ?)", session, room);
        jdbc.update("""
                insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id,
                    status, starts_at, ends_at, room_id)
                values (?, ?, ?, 'ASSIGNED', '2027-01-15T10:00Z', '2027-01-15T10:30Z', ?)
                """, session, variant, event, room);
        return new AssignmentFixture(session, variant, event, room);
    }

    void delete(AssignmentFixture fixture) {
        long owner = jdbc.queryForObject("select organizer_id from slotwise.planning_session where id=?", Long.class, fixture.session());
        jdbc.update("delete from slotwise.assignment where schedule_variant_id=?", fixture.variant());
        jdbc.update("delete from slotwise.schedule_variant where id=?", fixture.variant());
        jdbc.update("delete from slotwise.input_snapshot where planning_session_id=?", fixture.session());
        jdbc.update("delete from slotwise.event where id=?", fixture.event());
        jdbc.update("delete from slotwise.session_room where planning_session_id=?", fixture.session());
        jdbc.update("delete from slotwise.planning_session where id=?", fixture.session());
        jdbc.update("delete from slotwise.room where id=?", fixture.room());
        jdbc.update("delete from slotwise.account where id=?", owner);
    }

    record AssignmentFixture(long session, long variant, long event, long room) {
    }
}
