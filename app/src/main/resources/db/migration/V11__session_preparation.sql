create table slotwise.session_selected_group
(
    planning_session_id bigint not null references slotwise.planning_session (id),
    study_group_id bigint not null references slotwise.study_group (id),
    primary key (planning_session_id, study_group_id)
);

create table slotwise.session_selected_account
(
    planning_session_id bigint not null references slotwise.planning_session (id),
    account_id bigint not null references slotwise.account (id),
    primary key (planning_session_id, account_id)
);

-- Existing memberships had no selection provenance. Retain them as explicit selections.
insert into slotwise.session_selected_account (planning_session_id, account_id)
select planning_session_id, account_id from slotwise.session_participant;

-- Null denotes a legacy event with no copied template details.
alter table slotwise.event
    add column granularity varchar(16) check (granularity in ('PER_STUDENT', 'PER_GROUP')),
    add column use_template_dependencies boolean not null default false;

create table slotwise.event_required_role
(
    event_id bigint not null references slotwise.event (id) on delete cascade,
    participant_role varchar(24) not null check (participant_role in ('STUDENT', 'DISCIPLINE_TEACHER', 'SUPERVISOR')),
    primary key (event_id, participant_role)
);

create table slotwise.event_template_dependency
(
    event_id bigint not null references slotwise.event (id) on delete cascade,
    predecessor_type_id bigint not null references slotwise.event_type (id),
    min_gap_days integer not null check (min_gap_days >= 0),
    max_gap_days integer check (max_gap_days >= min_gap_days),
    primary key (event_id, predecessor_type_id)
);
