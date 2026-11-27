package ru.vk.itmo.scheduler.slotwise.storage;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventTypeEntity;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventTypeParticipantRoleEntity;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.planning.BusyImportance;
import ru.vk.itmo.scheduler.slotwise.planning.BusyIntervalEntity;
import ru.vk.itmo.scheduler.slotwise.planning.CollectionStatus;
import ru.vk.itmo.scheduler.slotwise.planning.EventEntity;
import ru.vk.itmo.scheduler.slotwise.planning.EventParticipantEntity;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionEntity;
import ru.vk.itmo.scheduler.slotwise.planning.ResponseStatus;
import ru.vk.itmo.scheduler.slotwise.planning.SessionParticipantEntity;
import ru.vk.itmo.scheduler.slotwise.planning.SessionRuleEntity;
import ru.vk.itmo.scheduler.slotwise.request.ChangeRequestEntity;
import ru.vk.itmo.scheduler.slotwise.request.ChangeRequestStatus;
import ru.vk.itmo.scheduler.slotwise.schedule.AssignmentEntity;
import ru.vk.itmo.scheduler.slotwise.schedule.AssignmentStatus;
import ru.vk.itmo.scheduler.slotwise.schedule.ScheduleVariantEntity;
import ru.vk.itmo.scheduler.slotwise.schedule.ScheduleVariantStatus;
import ru.vk.itmo.scheduler.slotwise.solving.SolveStatus;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;
import ru.vk.itmo.scheduler.slotwise.solving.SolverJobEntity;
import ru.vk.itmo.scheduler.slotwise.solving.SolverJobStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EnumPersistenceTests {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @Test
    void roundTripsTemplateEnumsIncludingCompositeKey() {
        var type = BeanUtils.instantiateClass(EventTypeEntity.class);
        type.setName("Enum template");
        type.setDefaultDurationMinutes(30);
        type.setGranularity(EventGranularity.PER_GROUP);
        type.setDefaultFormat(EventFormat.HYBRID);
        em.persist(type);

        for (ParticipantRole role : ParticipantRole.values()) {
            var participant = BeanUtils.instantiateClass(EventTypeParticipantRoleEntity.class);
            participant.setEventTypeId(type.getId());
            participant.setParticipantRole(role);
            em.persist(participant);
        }
        em.flush();
        em.clear();

        var loaded = em.find(EventTypeEntity.class, type.getId());
        assertEquals(EventGranularity.PER_GROUP, loaded.getGranularity());
        assertEquals(EventFormat.HYBRID, loaded.getDefaultFormat());
        assertEquals("HYBRID", jdbc.queryForObject(
                "select default_format from slotwise.event_type where id=?", String.class, type.getId()));
        for (ParticipantRole role : ParticipantRole.values()) {
            var key = new EventTypeParticipantRoleEntity.Key();
            key.setEventTypeId(type.getId());
            key.setParticipantRole(role);
            assertEquals(role, em.find(EventTypeParticipantRoleEntity.class, key).getParticipantRole());
        }
    }

    @Test
    void readsPlanningDefaultsAsEnumsAndPersistsChanges() {
        long sessionId = new StorageFixtures(jdbc).session();
        var session = em.find(PlanningSessionEntity.class, sessionId);
        var participant = BeanUtils.instantiateClass(SessionParticipantEntity.class);
        participant.setPlanningSessionId(sessionId);
        participant.setAccountId(session.getOrganizerId());
        em.persist(participant);
        var rules = BeanUtils.instantiateClass(SessionRuleEntity.class);
        rules.setPlanningSessionId(sessionId);
        em.persist(rules);
        var busy = BeanUtils.instantiateClass(BusyIntervalEntity.class);
        busy.setPlanningSessionId(sessionId);
        busy.setAccountId(session.getOrganizerId());
        busy.setStartsAt(Instant.parse("2027-01-15T09:00:00Z"));
        busy.setEndsAt(Instant.parse("2027-01-15T10:00:00Z"));
        busy.setImportance(BusyImportance.MEDIUM);
        em.persist(busy);
        em.flush();

        assertEquals(CollectionStatus.PENDING, session.getCollectionStatus());
        assertEquals(ResponseStatus.NOT_RESPONDED, participant.getResponseStatus());
        assertEquals(SolverAlgorithm.GREEDY, rules.getDefaultAlgorithm());
        session.setCollectionStatus(CollectionStatus.OPEN);
        participant.setResponseStatus(ResponseStatus.SUBMITTED);
        participant.setSubmittedAt(Instant.parse("2027-01-10T10:00:00Z"));
        rules.setDefaultAlgorithm(SolverAlgorithm.TIMEFOLD);
        em.flush();
        em.clear();

        var key = new SessionParticipantEntity.Key();
        key.setPlanningSessionId(sessionId);
        key.setAccountId(session.getOrganizerId());
        assertEquals(CollectionStatus.OPEN, em.find(PlanningSessionEntity.class, sessionId).getCollectionStatus());
        assertEquals(ResponseStatus.SUBMITTED, em.find(SessionParticipantEntity.class, key).getResponseStatus());
        assertEquals(SolverAlgorithm.TIMEFOLD, em.find(SessionRuleEntity.class, sessionId).getDefaultAlgorithm());
        assertEquals(BusyImportance.MEDIUM, em.find(BusyIntervalEntity.class, busy.getId()).getImportance());
    }

    @Test
    void roundTripsSolverScheduleAndRequestEnums() {
        var fixture = new StorageFixtures(jdbc).assignment();
        var variant = em.find(ScheduleVariantEntity.class, fixture.variant());
        var session = em.find(PlanningSessionEntity.class, fixture.session());
        jdbc.update("insert into slotwise.session_participant(planning_session_id, account_id) values (?, ?)",
                fixture.session(), session.getOrganizerId());
        var job = BeanUtils.instantiateClass(SolverJobEntity.class);
        job.setPlanningSessionId(fixture.session());
        job.setInputSnapshotId(variant.getInputSnapshotId());
        job.setAlgorithm(SolverAlgorithm.CP_SAT);
        em.persist(job);
        var request = BeanUtils.instantiateClass(ChangeRequestEntity.class);
        request.setPlanningSessionId(fixture.session());
        request.setRequesterId(session.getOrganizerId());
        request.setEventId(fixture.event());
        request.setStartsAt(Instant.parse("2027-01-15T10:00:00Z"));
        request.setEndsAt(Instant.parse("2027-01-15T10:30:00Z"));
        request.setReason("Unavailable");
        em.persist(request);
        var participant = BeanUtils.instantiateClass(EventParticipantEntity.class);
        participant.setPlanningSessionId(fixture.session());
        participant.setEventId(fixture.event());
        participant.setAccountId(session.getOrganizerId());
        participant.setParticipantRole(ParticipantRole.DISCIPLINE_TEACHER);
        em.persist(participant);
        em.flush();

        assertEquals(SolverJobStatus.QUEUED, job.getStatus());
        assertEquals(ChangeRequestStatus.PENDING, request.getStatus());
        assertEquals(ScheduleVariantStatus.DRAFT, variant.getStatus());
        assertEquals(SolveStatus.FEASIBLE, variant.getSolveStatus());
        job.setStatus(SolverJobStatus.RUNNING);
        job.setStartedAt(Instant.parse("2027-01-12T10:00:00Z"));
        request.setStatus(ChangeRequestStatus.ACCEPTED);
        request.setDecidedBy(session.getOrganizerId());
        request.setDecidedAt(Instant.parse("2027-01-12T10:00:00Z"));
        em.flush();
        em.clear();

        var assignmentKey = new AssignmentEntity.Key();
        assignmentKey.setScheduleVariantId(fixture.variant());
        assignmentKey.setEventId(fixture.event());
        var participantKey = new EventParticipantEntity.Key();
        participantKey.setEventId(fixture.event());
        participantKey.setAccountId(session.getOrganizerId());
        assertEquals(AssignmentStatus.ASSIGNED, em.find(AssignmentEntity.class, assignmentKey).getStatus());
        assertEquals(EventFormat.ONLINE, em.find(EventEntity.class, fixture.event()).getFormat());
        assertEquals(ParticipantRole.DISCIPLINE_TEACHER,
                em.find(EventParticipantEntity.class, participantKey).getParticipantRole());
        assertEquals(SolverAlgorithm.CP_SAT, em.find(SolverJobEntity.class, job.getId()).getAlgorithm());
        assertEquals(SolverJobStatus.RUNNING, em.find(SolverJobEntity.class, job.getId()).getStatus());
        assertEquals(ChangeRequestStatus.ACCEPTED, em.find(ChangeRequestEntity.class, request.getId()).getStatus());
        assertEquals("RUNNING", jdbc.queryForObject(
                "select status from slotwise.solver_job where id=?", String.class, job.getId()));
    }
}
