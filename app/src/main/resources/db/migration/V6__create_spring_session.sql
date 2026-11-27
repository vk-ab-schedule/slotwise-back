-- Spring Session JDBC tables (schema-postgresql.sql from spring-session-jdbc), moved to the slotwise schema.
-- principal_name holds the account id, so all sessions of an account can be found and removed.
create table slotwise.spring_session
(
    primary_id            char(36) primary key,
    session_id            char(36) not null,
    creation_time         bigint   not null,
    last_access_time      bigint   not null,
    max_inactive_interval int      not null,
    expiry_time           bigint   not null,
    principal_name        varchar(100)
);

create unique index ux_spring_session_session_id on slotwise.spring_session (session_id);
create index ix_spring_session_expiry_time on slotwise.spring_session (expiry_time);
create index ix_spring_session_principal_name on slotwise.spring_session (principal_name);

create table slotwise.spring_session_attributes
(
    session_primary_id char(36)     not null references slotwise.spring_session (primary_id) on delete cascade,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    primary key (session_primary_id, attribute_name)
);
