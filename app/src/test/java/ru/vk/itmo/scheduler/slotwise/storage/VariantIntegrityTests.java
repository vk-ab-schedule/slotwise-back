package ru.vk.itmo.scheduler.slotwise.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VariantIntegrityTests {
    @Autowired JdbcTemplate jdbc;

    private long session() {
        long owner = jdbc.queryForObject("""
                insert into slotwise.account(email, first_name, last_name, role)
                values (gen_random_uuid()::text || '@example.org', 'Test', 'Organizer', 'ORGANIZER') returning id
                """, Long.class);
        return jdbc.queryForObject("""
                insert into slotwise.planning_session(organizer_id, name, starts_at, ends_at,
                    collection_starts_at, collection_ends_at)
                values (?, 'Test', '2027-01-15T09:00Z', '2027-01-16T19:00Z',
                    '2027-01-10T09:00Z', '2027-01-11T19:00Z') returning id
                """, Long.class, owner);
    }

    private long variant(long session) {
        long snapshot = jdbc.queryForObject("""
                insert into slotwise.input_snapshot(planning_session_id, input_version, problem)
                values (?, 0, '{}') on conflict (planning_session_id, input_version)
                do update set input_version=excluded.input_version returning id
                """, Long.class, session);
        return jdbc.queryForObject("""
                insert into slotwise.schedule_variant(planning_session_id, input_snapshot_id, solve_status)
                values (?, ?, 'FEASIBLE') returning id
                """, Long.class, session, snapshot);
    }

    @Test
    void allowsMultipleDraftsButOnlyOnePublishedVariant() {
        long session = session();
        long first = variant(session);
        long second = variant(session);
        Long owner = jdbc.queryForObject("select organizer_id from slotwise.planning_session where id=?", Long.class, session);
        jdbc.update("update slotwise.schedule_variant set status='PUBLISHED', published_at=now(), published_by=? where id=?", owner, first);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update slotwise.schedule_variant set status='PUBLISHED', published_at=now(), published_by=? where id=?", owner, second));
    }

    @Test
    void rejectsAssignmentOfAnEventFromAnotherSession() {
        long first = session();
        long second = session();
        long variant = variant(first);
        Long type = jdbc.queryForObject("select min(id) from slotwise.event_type", Long.class);
        long event = jdbc.queryForObject("""
                insert into slotwise.event(planning_session_id, event_type_id, title, duration_minutes, format)
                values (?, ?, 'Test', 30, 'ONSITE') returning id
                """, Long.class, second, type);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id, status)
                values (?, ?, ?, 'UNASSIGNED')
                """, first, variant, event));
    }

    @Test
    void keepsDifferentAssignmentsInDifferentVariants() {
        long session = session();
        long first = variant(session);
        long second = variant(session);
        Long type = jdbc.queryForObject("select min(id) from slotwise.event_type", Long.class);
        long event = jdbc.queryForObject("""
                insert into slotwise.event(planning_session_id, event_type_id, title, duration_minutes, format)
                values (?, ?, 'Test', 30, 'ONSITE') returning id
                """, Long.class, session, type);
        long room = jdbc.queryForObject("insert into slotwise.room(name, online) values ('Online', true) returning id", Long.class);
        jdbc.update("insert into slotwise.session_room values (?, ?)", session, room);
        jdbc.update("""
                insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id, status)
                values (?, ?, ?, 'UNASSIGNED')
                """, session, first, event);
        jdbc.update("""
                insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id, status, starts_at, ends_at, room_id)
                values (?, ?, ?, 'ASSIGNED', '2027-01-15T10:00Z', '2027-01-15T10:30Z', ?)
                """, session, second, event, room);
        assertEquals(2, jdbc.queryForObject("select count(*) from slotwise.assignment where event_id=?", Integer.class, event));
        assertEquals("UNASSIGNED", jdbc.queryForObject("select status from slotwise.assignment where schedule_variant_id=? and event_id=?", String.class, first, event));
    }
}
