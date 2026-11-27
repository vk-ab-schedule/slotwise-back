package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionWorkingDayRepository extends JpaRepository<SessionWorkingDayEntity, SessionWorkingDayEntity.Key> {
    List<SessionWorkingDayEntity> findByPlanningSessionId(long sessionId);
}
