alter table slotwise.solver_job
    add column incomplete_data boolean not null default false,
    add column completed_result jsonb check (jsonb_typeof(completed_result) = 'object');

update slotwise.solver_job j
set incomplete_data = coalesce((s.problem ->> 'incompleteData')::boolean, false)
from slotwise.input_snapshot s
where s.id = j.input_snapshot_id;

update slotwise.solver_job j
set completed_result = jsonb_build_object(
    'variantId', v.id,
    'solveStatus', v.solve_status,
    'unassignedEvents', coalesce((v.metrics ->> 'unassignedEvents')::int, 0),
    'penalty', coalesce(v.objective, 0),
    'bestBound', v.best_bound)
from slotwise.schedule_variant v
where v.solver_job_id = j.id and j.status = 'COMPLETED';
