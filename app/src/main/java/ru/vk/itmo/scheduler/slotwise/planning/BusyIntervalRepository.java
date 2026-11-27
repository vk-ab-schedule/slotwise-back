package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BusyIntervalRepository extends JpaRepository<BusyIntervalEntity, Long> {
    List<BusyIntervalEntity> findByPlanningSessionIdOrderByStartsAt(long sessionId);
    List<BusyIntervalEntity> findByPlanningSessionIdAndAccountIdOrderByStartsAt(long sessionId, long accountId);

    @Modifying
    @Query("delete from BusyIntervalEntity b where b.planningSessionId = :sessionId and b.accountId = :accountId")
    void deletePersonal(long sessionId, long accountId);
}
