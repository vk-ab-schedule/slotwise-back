-- A publication keeps its event data even if planning settings or the event roster change.
alter table slotwise.assignment add column published_event jsonb;

with published as (
    select a.schedule_variant_id, a.event_id, jsonb_build_object('sessionName', s.name, 'assignment', jsonb_build_object(
        'eventId', e.id, 'eventTypeId', e.event_type_id, 'title', e.title, 'format', e.format,
        'durationMinutes', e.duration_minutes, 'status', a.status, 'startsAt', a.starts_at, 'endsAt', a.ends_at,
        'room', case when r.id is not null then jsonb_build_object(
            'id', r.id, 'name', r.name, 'building', r.building, 'online', r.online) end,
        'locked', a.locked, 'version', a.version, 'issues', coalesce(a.explanation, '[]'::jsonb),
        'participants', coalesce((
            select jsonb_agg(jsonb_build_object('accountId', u.id, 'firstName', u.first_name,
                'lastName', u.last_name, 'patronymic', u.patronymic, 'role', p.participant_role)
                order by u.last_name, u.first_name, u.id)
            from slotwise.event_participant p join slotwise.account u on u.id = p.account_id
            where p.event_id = e.id), '[]'::jsonb),
        'commissionMembers', coalesce((
            select jsonb_agg(jsonb_build_object('accountId', u.id, 'firstName', u.first_name,
                'lastName', u.last_name, 'patronymic', u.patronymic, 'chair', m.chair)
                order by m.chair desc, u.last_name, u.first_name, u.id)
            from slotwise.assignment_commission_member m join slotwise.account u on u.id = m.account_id
            where m.schedule_variant_id = v.id and m.event_id = e.id), '[]'::jsonb)
    )) as snapshot
    from slotwise.assignment a
    join slotwise.schedule_variant v on v.id = a.schedule_variant_id
    join slotwise.planning_session s on s.id = v.planning_session_id
    join slotwise.event e on e.id = a.event_id
    left join slotwise.room r on r.id = a.room_id
    where v.status in ('PUBLISHED', 'SUPERSEDED')
)
update slotwise.assignment a set published_event = p.snapshot
from published p where a.schedule_variant_id = p.schedule_variant_id and a.event_id = p.event_id;
