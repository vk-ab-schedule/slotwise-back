-- Constraint model revision (D-16): 5-minute slots, preparation days, optional maximum gap of dependencies.

alter table slotwise.event_type
    drop constraint event_type_default_duration_minutes_check,
    add constraint event_type_default_duration_minutes_check
        check (default_duration_minutes > 0 and default_duration_minutes % 5 = 0),
    add column preparation_days integer not null default 0 check (preparation_days >= 0);

alter table slotwise.event
    drop constraint event_duration_minutes_check,
    add constraint event_duration_minutes_check
        check (duration_minutes > 0 and duration_minutes % 5 = 0),
    add column preparation_days integer not null default 0 check (preparation_days >= 0);

alter table slotwise.session_rule
    drop constraint session_rule_room_change_buffer_minutes_check,
    add constraint session_rule_room_change_buffer_minutes_check
        check (room_change_buffer_minutes >= 0 and room_change_buffer_minutes % 5 = 0);

alter table slotwise.event_type_dependency
    add column max_gap_days integer,
    add check (max_gap_days is null or max_gap_days >= min_gap_days);

alter table slotwise.event_dependency
    add column max_gap_days integer,
    add check (max_gap_days is null or max_gap_days >= min_gap_days);

-- Two days between exams of a student, a consultation one or two days before the exam.
update slotwise.event_type
set preparation_days = 2
where name = 'Экзамен по дисциплине';

insert into slotwise.event_type_dependency(predecessor_type_id, successor_type_id, min_gap_days, max_gap_days)
select p.id, s.id, 1, 2
from slotwise.event_type p
         cross join slotwise.event_type s
where p.name = 'Консультация по дисциплине'
  and s.name = 'Экзамен по дисциплине';
