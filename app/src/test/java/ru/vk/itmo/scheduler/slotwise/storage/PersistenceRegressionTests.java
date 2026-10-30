package ru.vk.itmo.scheduler.slotwise.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.planning.SessionRuleEntity;
import ru.vk.itmo.scheduler.slotwise.schedule.AssignmentEntity;
import ru.vk.itmo.scheduler.slotwise.solving.InputSnapshotEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PersistenceRegressionTests {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejectsStaleAssignmentEdit() {
        var fixtures = new StorageFixtures(jdbc);
        var fixture = fixtures.assignment();
        var key = new AssignmentEntity.Key();
        key.setScheduleVariantId(fixture.variant());
        key.setEventId(fixture.event());
        EntityManager first = emf.createEntityManager();
        EntityManager second = emf.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            var moved = first.find(AssignmentEntity.class, key);
            var stale = second.find(AssignmentEntity.class, key);
            moved.setStartsAt(Instant.parse("2027-01-15T11:00:00Z"));
            moved.setEndsAt(Instant.parse("2027-01-15T11:30:00Z"));
            first.getTransaction().commit();
            stale.setLocked(true);
            assertThrows(OptimisticLockException.class, second::flush);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (second.getTransaction().isActive()) second.getTransaction().rollback();
            first.close();
            second.close();
        }
        try {
            assertEquals(Instant.parse("2027-01-15T11:00:00Z"), jdbc.queryForObject("""
                    select starts_at from slotwise.assignment where schedule_variant_id=? and event_id=?
                    """, java.sql.Timestamp.class, fixture.variant(), fixture.event()).toInstant());
        } finally {
            fixtures.delete(fixture);
        }
    }

    @Test
    @Transactional
    void rejectsSnapshotRewrite() {
        var fixtures = new StorageFixtures(jdbc);
        long id = fixtures.snapshot(fixtures.session(), 0);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("""
                update slotwise.input_snapshot set problem='{"changed":true}' where id=?
                """, id));
    }

    @Test
    @Transactional
    void rejectsVariantLinkedToDifferentJobSnapshot() {
        var fixtures = new StorageFixtures(jdbc);
        long session = fixtures.session();
        long oldSnapshot = fixtures.snapshot(session, 0);
        long newSnapshot = fixtures.snapshot(session, 1);
        long job = jdbc.queryForObject("""
                insert into slotwise.solver_job(planning_session_id, input_snapshot_id, algorithm)
                values (?, ?, 'GREEDY') returning id
                """, Long.class, session, oldSnapshot);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into slotwise.schedule_variant(planning_session_id, input_snapshot_id, solver_job_id, solve_status)
                values (?, ?, ?, 'FEASIBLE')
                """, session, newSnapshot, job));
    }

    @Test
    @Transactional
    void readsJsonAndNumericDefaultsWithoutRefresh() {
        long session = new StorageFixtures(jdbc).session();
        var rules = BeanUtils.instantiateClass(SessionRuleEntity.class);
        rules.setPlanningSessionId(session);
        em.persist(rules);
        em.flush();
        assertEquals("{}", rules.getWeights());
        assertNotNull(rules.getTimeLimitSeconds());
        assertEquals(15, rules.getRoomChangeBufferMinutes());
        rules.setSeed(42L);
        em.flush();
    }

    @Test
    @Transactional
    void acceptsVariantLinkedToMatchingJobSnapshot() {
        var fixtures = new StorageFixtures(jdbc);
        long session = fixtures.session();
        long snapshot = fixtures.snapshot(session, 0);
        long job = jdbc.queryForObject("""
                insert into slotwise.solver_job(planning_session_id, input_snapshot_id, algorithm)
                values (?, ?, 'GREEDY') returning id
                """, Long.class, session, snapshot);
        Long variant = jdbc.queryForObject("""
                insert into slotwise.schedule_variant(planning_session_id, input_snapshot_id, solver_job_id, solve_status)
                values (?, ?, ?, 'FEASIBLE') returning id
                """, Long.class, session, snapshot, job);
        assertNotNull(variant);
    }

    @Test
    @Transactional
    void persistsImmutableSnapshotWithFactory() {
        long session = new StorageFixtures(jdbc).session();
        var snapshot = InputSnapshotEntity.create(session, 0, "{}");
        em.persist(snapshot);
        em.flush();
        em.clear();
        var loaded = em.find(InputSnapshotEntity.class, snapshot.getId());
        assertEquals(session, loaded.getPlanningSessionId());
        assertEquals(0L, loaded.getInputVersion());
        assertEquals("{}", loaded.getProblem());
    }
}
