-- Snapshot preserves the input data used by a calculation and cannot be changed.
create table slotwise.input_snapshot
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint      not null references slotwise.planning_session (id),
    input_version       bigint      not null check (input_version >= 0),
    problem             jsonb       not null check (jsonb_typeof(problem) = 'object'),
    created_at          timestamptz not null default now(),
    unique (planning_session_id, input_version),
    unique (planning_session_id, id)
);

create function slotwise.reject_input_snapshot_update() returns trigger
    language plpgsql as
$$
begin
    raise exception 'Input snapshots are immutable; create a new snapshot version'
        using errcode = '55000';
end;
$$;

create trigger input_snapshot_immutable
    before update
    on slotwise.input_snapshot
    for each row
    when (old.* is distinct from new.*)
execute function slotwise.reject_input_snapshot_update();

create table slotwise.solver_job
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint      not null,
    input_snapshot_id   bigint      not null,
    algorithm           varchar(16) not null check (algorithm in ('GREEDY', 'CP_SAT', 'TIMEFOLD')),
    parameters          jsonb       not null default '{}'::jsonb check (jsonb_typeof(parameters) = 'object'),
    status              varchar(16) not null default 'QUEUED' check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    requested_by        bigint references slotwise.account (id),
    created_at          timestamptz not null default now(),
    started_at          timestamptz,
    finished_at         timestamptz,
    error_message       text,
    version             bigint      not null default 0,
    foreign key (planning_session_id, input_snapshot_id) references slotwise.input_snapshot (planning_session_id, id),
    unique (planning_session_id, id),
    constraint ux_solver_job_session_id_snapshot unique (planning_session_id, id, input_snapshot_id),
    check (finished_at is null or (started_at is not null and finished_at >= started_at))
);
create unique index ux_solver_job_request on slotwise.solver_job (input_snapshot_id, algorithm, parameters)
    where status in ('QUEUED', 'RUNNING', 'COMPLETED');
create unique index ux_solver_job_active_session on slotwise.solver_job (planning_session_id)
    where status in ('QUEUED', 'RUNNING');
create index ix_solver_job_status_created_at on slotwise.solver_job (status, created_at);

create table slotwise.schedule_variant
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint      not null,
    input_snapshot_id   bigint      not null,
    solver_job_id       bigint,
    base_variant_id     bigint,
    status              varchar(16) not null default 'DRAFT' check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    solve_status        varchar(24) not null check (solve_status in ('OPTIMAL', 'FEASIBLE', 'NO_SOLUTION')),
    metrics             jsonb       not null default '{}'::jsonb check (jsonb_typeof(metrics) = 'object'),
    objective           numeric,
    best_bound          numeric,
    runtime_millis      bigint check (runtime_millis >= 0),
    version             bigint      not null default 0,
    created_at          timestamptz not null default now(),
    published_at        timestamptz,
    published_by        bigint references slotwise.account (id),
    unique (planning_session_id, id),
    foreign key (planning_session_id, input_snapshot_id) references slotwise.input_snapshot (planning_session_id, id),
    constraint fk_schedule_variant_job_snapshot
        foreign key (planning_session_id, solver_job_id, input_snapshot_id)
            references slotwise.solver_job (planning_session_id, id, input_snapshot_id),
    foreign key (planning_session_id, base_variant_id) references slotwise.schedule_variant (planning_session_id, id),
    check (base_variant_id is null or base_variant_id <> id),
    check ((status = 'DRAFT' and published_at is null and published_by is null)
        or (status in ('PUBLISHED', 'SUPERSEDED') and published_at is not null and published_by is not null))
);
create unique index ux_schedule_variant_published on slotwise.schedule_variant (planning_session_id) where status = 'PUBLISHED';

create table slotwise.assignment
(
    planning_session_id bigint      not null,
    schedule_variant_id bigint      not null,
    event_id            bigint      not null,
    status              varchar(16) not null check (status in ('ASSIGNED', 'UNASSIGNED')),
    starts_at           timestamptz,
    ends_at             timestamptz,
    room_id             bigint,
    locked              boolean     not null default false,
    explanation         jsonb,
    version             bigint      not null default 0,
    primary key (schedule_variant_id, event_id),
    unique (planning_session_id, schedule_variant_id, event_id),
    foreign key (planning_session_id, schedule_variant_id) references slotwise.schedule_variant (planning_session_id, id),
    foreign key (planning_session_id, event_id) references slotwise.event (planning_session_id, id),
    foreign key (planning_session_id, room_id) references slotwise.session_room (planning_session_id, room_id),
    check ((status = 'ASSIGNED' and starts_at is not null and ends_at is not null and starts_at < ends_at and
            room_id is not null)
        or (status = 'UNASSIGNED' and starts_at is null and ends_at is null and room_id is null and not locked))
);
create index ix_assignment_event_id on slotwise.assignment (event_id);

create table slotwise.assignment_commission_member
(
    planning_session_id bigint  not null,
    schedule_variant_id bigint  not null,
    event_id            bigint  not null,
    account_id          bigint  not null,
    chair               boolean not null default false,
    primary key (schedule_variant_id, event_id, account_id),
    foreign key (planning_session_id, schedule_variant_id, event_id) references slotwise.assignment (planning_session_id, schedule_variant_id, event_id),
    foreign key (planning_session_id, account_id) references slotwise.commission_candidate (planning_session_id, account_id)
);

create table slotwise.change_request
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint      not null,
    requester_id        bigint      not null,
    event_id            bigint,
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    reason              text        not null,
    status              varchar(16) not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REJECTED')),
    decision_comment    text,
    decided_by          bigint references slotwise.account (id),
    decided_at          timestamptz,
    created_at          timestamptz not null default now(),
    version             bigint      not null default 0,
    foreign key (planning_session_id, requester_id) references slotwise.session_participant (planning_session_id, account_id),
    foreign key (planning_session_id, event_id) references slotwise.event (planning_session_id, id),
    check (starts_at < ends_at),
    check ((status = 'PENDING' and decided_by is null and decided_at is null)
        or (status <> 'PENDING' and decided_by is not null and decided_at is not null))
);
create index ix_change_request_session_status on slotwise.change_request (planning_session_id, status);
