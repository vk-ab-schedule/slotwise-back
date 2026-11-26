alter table slotwise.event_dependency add column automatic boolean not null default false;

-- In the original preparation API an edit replaced the entire list and disabled automatic edges.
update slotwise.event_dependency d set automatic=true
from slotwise.event s where s.id=d.successor_event_id and s.use_template_dependencies;

-- Omitting an edge in an edit must survive later automatic refreshes.
create table slotwise.event_dependency_exclusion
(
    predecessor_event_id bigint not null references slotwise.event (id) on delete cascade,
    successor_event_id bigint not null references slotwise.event (id) on delete cascade,
    primary key (predecessor_event_id, successor_event_id)
);
