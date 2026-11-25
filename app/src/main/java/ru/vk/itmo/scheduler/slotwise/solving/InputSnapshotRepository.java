package ru.vk.itmo.scheduler.slotwise.solving;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InputSnapshotRepository extends JpaRepository<InputSnapshotEntity, Long> {

    Optional<InputSnapshotEntity> findByPlanningSessionIdAndInputVersion(long sessionId, long inputVersion);
}
