package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipantEntity, SessionParticipantEntity.Key> {
    List<SessionParticipantEntity> findByPlanningSessionIdOrderByAccountId(long sessionId);
}
