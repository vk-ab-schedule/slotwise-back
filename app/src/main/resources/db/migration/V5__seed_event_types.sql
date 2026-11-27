-- Initial administrator-managed templates; changing a template does not change events.
insert into slotwise.event_type(name, default_duration_minutes, granularity, default_format, commission_size,
                                requires_chair)
values ('Экзамен по дисциплине', 90, 'PER_GROUP', 'ONSITE', 0, false),
       ('Консультация по дисциплине', 60, 'PER_GROUP', 'ONSITE', 0, false),
       ('Консультация с научным руководителем', 30, 'PER_STUDENT', 'ONSITE', 0, false),
       ('Предзащита', 30, 'PER_STUDENT', 'ONSITE', 3, true),
       ('Защита', 30, 'PER_STUDENT', 'ONSITE', 3, true);
insert into slotwise.event_type_participant_role(event_type_id, participant_role)

select id, 'STUDENT'
from slotwise.event_type;
insert into slotwise.event_type_participant_role(event_type_id, participant_role)

select id, 'DISCIPLINE_TEACHER'
from slotwise.event_type
where name in ('Экзамен по дисциплине', 'Консультация по дисциплине');
insert into slotwise.event_type_participant_role(event_type_id, participant_role)

select id, 'SUPERVISOR'
from slotwise.event_type
where name in ('Консультация с научным руководителем', 'Предзащита', 'Защита');
insert into slotwise.event_type_dependency(predecessor_type_id, successor_type_id, min_gap_days)

select p.id, s.id, 3
from slotwise.event_type p
         cross join slotwise.event_type s
where p.name = 'Предзащита'
  and s.name = 'Защита';
