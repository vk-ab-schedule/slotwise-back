package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface PlanningSessionRepository extends JpaRepository<PlanningSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlanningSessionEntity s where s.id = :id")
    Optional<PlanningSessionEntity> findForUpdate(long id);
}
