alter table slotwise.session_participant
    add column calendar_revision bigint not null default 0 check (calendar_revision >= 0);
