create table slotwise.study_group
(
    id          bigint generated always as identity primary key,
    external_id varchar(255) unique,
    name        varchar(255) not null unique,
    active      boolean      not null default true,
    version     bigint       not null default 0
);

create table slotwise.study_group_member
(
    study_group_id bigint not null references slotwise.study_group (id),
    account_id     bigint not null references slotwise.account (id),
    primary key (study_group_id, account_id)
);
create index ix_study_group_member_account_id on slotwise.study_group_member (account_id);

create table slotwise.discipline
(
    id          bigint generated always as identity primary key,
    external_id varchar(255) unique,
    name        varchar(255) not null,
    active      boolean      not null default true,
    version     bigint       not null default 0
);

create table slotwise.teaching
(
    id             bigint generated always as identity primary key,
    teacher_id     bigint not null references slotwise.account (id),
    discipline_id  bigint not null references slotwise.discipline (id),
    study_group_id bigint not null references slotwise.study_group (id),
    unique (teacher_id, discipline_id, study_group_id)
);
create index ix_teaching_study_group_id on slotwise.teaching (study_group_id, discipline_id);

create table slotwise.supervision
(
    student_id    bigint not null references slotwise.account (id),
    supervisor_id bigint not null references slotwise.account (id),
    primary key (student_id, supervisor_id),
    check (student_id <> supervisor_id)
);
create index ix_supervision_supervisor_id on slotwise.supervision (supervisor_id);

create table slotwise.room
(
    id          bigint generated always as identity primary key,
    external_id varchar(255) unique,
    name        varchar(255) not null,
    building    varchar(255),
    online      boolean      not null default false,
    capacity    integer,
    active      boolean      not null default true,
    version     bigint       not null default 0,
    check ((online and capacity is null) or (not online and capacity > 0 and capacity is not null))
);

create table slotwise.equipment
(
    id   bigint generated always as identity primary key,
    name varchar(100) not null unique
);

create table slotwise.room_equipment
(
    room_id      bigint not null references slotwise.room (id),
    equipment_id bigint not null references slotwise.equipment (id),
    primary key (room_id, equipment_id)
);

create table slotwise.timetable_entry
(
    id          bigint generated always as identity primary key,
    external_id varchar(255) unique,
    account_id  bigint references slotwise.account (id),
    room_id     bigint references slotwise.room (id),
    starts_at   timestamptz not null,
    ends_at     timestamptz not null,
    description text,
    check (starts_at < ends_at),
    check (num_nonnulls(account_id, room_id) = 1)
);
create index ix_timetable_entry_account_id on slotwise.timetable_entry (account_id, starts_at);
create index ix_timetable_entry_room_id on slotwise.timetable_entry (room_id, starts_at);
