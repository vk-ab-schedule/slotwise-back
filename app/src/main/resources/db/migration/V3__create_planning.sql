create table slotwise.event_type
(
    id                       bigint generated always as identity primary key,
    name                     varchar(255) not null unique,
    default_duration_minutes integer      not null check (default_duration_minutes > 0 and default_duration_minutes % 15 = 0),
    granularity              varchar(16)  not null check (granularity in ('PER_STUDENT', 'PER_GROUP')),
    default_format           varchar(16)  not null check (default_format in ('ONSITE', 'ONLINE', 'HYBRID')),
    commission_size          integer      not null default 0 check (commission_size >= 0),
    requires_chair           boolean      not null default false,
    active                   boolean      not null default true,
    version                  bigint       not null default 0,
    check (not requires_chair or commission_size > 0)
);

create table slotwise.event_type_participant_role
(
    event_type_id    bigint      not null references slotwise.event_type (id),
    participant_role varchar(24) not null check (participant_role in ('STUDENT', 'DISCIPLINE_TEACHER', 'SUPERVISOR')),
    primary key (event_type_id, participant_role)
);

create table slotwise.event_type_equipment
(
    event_type_id bigint not null references slotwise.event_type (id),
    equipment_id  bigint not null references slotwise.equipment (id),
    primary key (event_type_id, equipment_id)
);

create table slotwise.event_type_dependency
(
    predecessor_type_id bigint  not null references slotwise.event_type (id),
    successor_type_id   bigint  not null references slotwise.event_type (id),
    min_gap_days        integer not null check (min_gap_days >= 0),
    primary key (predecessor_type_id, successor_type_id),
    check (predecessor_type_id <> successor_type_id)
);

create table slotwise.planning_session
(
    id                           bigint generated always as identity primary key,
    organizer_id                 bigint       not null references slotwise.account (id),
    name                         varchar(255) not null,
    starts_at                    timestamptz  not null,
    ends_at                      timestamptz  not null,
    time_zone                    varchar(64)  not null default 'Europe/Moscow',
    working_day_start            time         not null default '09:00',
    working_day_end              time         not null default '19:00',
    collection_starts_at         timestamptz  not null,
    collection_ends_at           timestamptz  not null,
    collection_status            varchar(16)  not null default 'PENDING' check (collection_status in ('PENDING', 'OPEN', 'CLOSED')),
    automatic_solve_requested_at timestamptz,
    input_version                bigint       not null default 0 check (input_version >= 0),
    version                      bigint       not null default 0,
    created_at                   timestamptz  not null default now(),
    unique (organizer_id, name),
    check (starts_at < ends_at),
    check (working_day_start < working_day_end),
    check (collection_starts_at < collection_ends_at and collection_ends_at < starts_at)
);

create table slotwise.session_working_day
(
    planning_session_id bigint   not null references slotwise.planning_session (id),
    day_of_week         smallint not null check (day_of_week between 1 and 7),
    primary key (planning_session_id, day_of_week)
);

create table slotwise.session_participant
(
    planning_session_id     bigint      not null references slotwise.planning_session (id),
    account_id              bigint      not null references slotwise.account (id),
    response_status         varchar(16) not null default 'NOT_RESPONDED' check (response_status in ('NOT_RESPONDED', 'SUBMITTED')),
    submitted_at            timestamptz,
    last_schedule_viewed_at timestamptz,
    primary key (planning_session_id, account_id),
    check ((response_status = 'SUBMITTED') = (submitted_at is not null))
);
create index ix_session_participant_account_id on slotwise.session_participant (account_id);

create table slotwise.session_room
(
    planning_session_id bigint not null references slotwise.planning_session (id),
    room_id             bigint not null references slotwise.room (id),
    primary key (planning_session_id, room_id)
);

create table slotwise.commission_candidate
(
    planning_session_id bigint  not null,
    account_id          bigint  not null,
    can_chair           boolean not null default false,
    primary key (planning_session_id, account_id),
    foreign key (planning_session_id, account_id) references slotwise.session_participant (planning_session_id, account_id)
);

create table slotwise.session_rule
(
    planning_session_id        bigint primary key references slotwise.planning_session (id),
    weights                    jsonb       not null default '{}'::jsonb check (jsonb_typeof(weights) = 'object'),
    room_change_buffer_minutes integer     not null default 15 check (room_change_buffer_minutes >= 0 and room_change_buffer_minutes % 15 = 0),
    default_algorithm          varchar(16) not null default 'GREEDY' check (default_algorithm in ('GREEDY', 'CP_SAT', 'TIMEFOLD')),
    time_limit_seconds         integer     not null default 60 check (time_limit_seconds > 0),
    seed                       bigint      not null default 0
);

create table slotwise.event
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint       not null references slotwise.planning_session (id),
    event_type_id       bigint       not null references slotwise.event_type (id),
    discipline_id       bigint references slotwise.discipline (id),
    study_group_id      bigint references slotwise.study_group (id),
    title               varchar(255) not null,
    description         text,
    duration_minutes    integer      not null check (duration_minutes > 0 and duration_minutes % 15 = 0),
    format              varchar(16)  not null check (format in ('ONSITE', 'ONLINE', 'HYBRID')),
    commission_size     integer      not null default 0 check (commission_size >= 0),
    requires_chair      boolean      not null default false,
    complete            boolean      not null default false,
    version             bigint       not null default 0,
    unique (planning_session_id, id),
    check (not requires_chair or commission_size > 0)
);

create table slotwise.event_participant
(
    planning_session_id bigint      not null,
    event_id            bigint      not null,
    account_id          bigint      not null,
    participant_role    varchar(24) not null check (participant_role in ('STUDENT', 'DISCIPLINE_TEACHER', 'SUPERVISOR')),
    primary key (event_id, account_id),
    foreign key (planning_session_id, event_id) references slotwise.event (planning_session_id, id),
    foreign key (planning_session_id, account_id) references slotwise.session_participant (planning_session_id, account_id)
);
create index ix_event_participant_account_id on slotwise.event_participant (account_id);
create table slotwise.event_equipment
(
    event_id     bigint not null references slotwise.event (id),
    equipment_id bigint not null references slotwise.equipment (id),
    primary key (event_id, equipment_id)
);

create table slotwise.event_dependency
(
    planning_session_id  bigint  not null,
    predecessor_event_id bigint  not null,
    successor_event_id   bigint  not null,
    min_gap_days         integer not null check (min_gap_days >= 0),
    primary key (predecessor_event_id, successor_event_id),
    foreign key (planning_session_id, predecessor_event_id) references slotwise.event (planning_session_id, id),
    foreign key (planning_session_id, successor_event_id) references slotwise.event (planning_session_id, id),
    check (predecessor_event_id <> successor_event_id)
);

create table slotwise.busy_interval
(
    id                  bigint generated always as identity primary key,
    planning_session_id bigint      not null,
    account_id          bigint      not null,
    starts_at           timestamptz not null,
    ends_at             timestamptz not null,
    importance          varchar(16) not null check (importance in ('CRITICAL', 'MEDIUM', 'LOW')),
    version             bigint      not null default 0,
    foreign key (planning_session_id, account_id) references slotwise.session_participant (planning_session_id, account_id),
    check (starts_at < ends_at)
);
create index ix_busy_interval_session_account on slotwise.busy_interval (planning_session_id, account_id, starts_at);
create index ix_planning_session_collection on slotwise.planning_session (collection_status, collection_ends_at);
