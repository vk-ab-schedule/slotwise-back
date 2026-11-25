package ru.vk.itmo.scheduler.slotwise.solving;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolverJobRepository extends JpaRepository<SolverJobEntity, Long> {

    /**
     * A job that is queued, running or completed for the same input, algorithm and parameters (index ux_solver_job_request).
     */
    @Query(value = """
            select * from slotwise.solver_job
            where input_snapshot_id = :snapshot and algorithm = :algorithm and parameters = cast(:parameters as jsonb)
              and status in ('QUEUED', 'RUNNING', 'COMPLETED')
            """, nativeQuery = true)
    Optional<SolverJobEntity> findSameRequest(long snapshot, String algorithm, String parameters);

    Optional<SolverJobEntity> findFirstByPlanningSessionIdAndStatusIn(long sessionId, Collection<SolverJobStatus> statuses);

    List<SolverJobEntity> findByStatusIn(Collection<SolverJobStatus> statuses);

    long countByStatusAndIdLessThan(SolverJobStatus status, long id);
}
