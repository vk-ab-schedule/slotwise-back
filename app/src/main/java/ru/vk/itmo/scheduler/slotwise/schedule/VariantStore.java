package ru.vk.itmo.scheduler.slotwise.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.domain.Assignment;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.ScheduleMetrics;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes calculated schedules as draft variants: one assignment row for every event of the problem,
 * unassigned events with the precheck issues that explain them.
 */
@Component
@RequiredArgsConstructor
public class VariantStore {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    /**
     * @param locked events whose assignment stays fixed in the next calculations
     */
    public record Draft(long sessionId, long snapshotId, long jobId, Long baseVariantId, Problem problem,
                        List<Assignment> assignments, Set<Long> locked, ScheduleMetrics metrics,
                        Map<Long, List<ApiIssue>> explanations) {
    }

    public long saveDraft(Draft draft) {
        var metrics = draft.metrics();
        long variant = jdbc.queryForObject("""
                insert into slotwise.schedule_variant(planning_session_id, input_snapshot_id, solver_job_id,
                    base_variant_id, solve_status, metrics, objective, best_bound, runtime_millis)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?) returning id
                """, Long.class, draft.sessionId(), draft.snapshotId(), draft.jobId(), draft.baseVariantId(),
                metrics.solveStatus().name(), json.writeValueAsString(metrics), metrics.penalty(), metrics.bestBound(),
                metrics.runtimeMillis());
        var grid = draft.problem().grid();
        Map<Long, Assignment> assigned = draft.assignments().stream()
                .collect(Collectors.toMap(Assignment::eventId, Function.identity()));
        for (Event event : draft.problem().events()) {
            var assignment = assigned.get(event.id());
            if (assignment == null) {
                jdbc.update("""
                        insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id, status, explanation)
                        values (?, ?, ?, 'UNASSIGNED', ?::jsonb)
                        """, draft.sessionId(), variant, event.id(),
                        json.writeValueAsString(draft.explanations().getOrDefault(event.id(), List.of())));
                continue;
            }
            jdbc.update("""
                    insert into slotwise.assignment(planning_session_id, schedule_variant_id, event_id, status,
                        starts_at, ends_at, room_id, locked)
                    values (?, ?, ?, 'ASSIGNED', ?, ?, ?, ?)
                    """, draft.sessionId(), variant, event.id(), Timestamp.from(grid.toInstant(assignment.start())),
                    Timestamp.from(grid.toInstant(assignment.start() + event.duration())), assignment.roomId(),
                    draft.locked().contains(event.id()));
            for (long member : assignment.commission()) {
                jdbc.update("""
                        insert into slotwise.assignment_commission_member(planning_session_id, schedule_variant_id,
                            event_id, account_id, chair)
                        values (?, ?, ?, ?, ?)
                        """, draft.sessionId(), variant, event.id(), member,
                        assignment.chair().isPresent() && assignment.chair().getAsLong() == member);
            }
        }
        return variant;
    }
}
