create schema if not exists slotwise;

create table slotwise.account
(
    id            bigint generated always as identity primary key,
    external_id   varchar(255) unique,
    email         varchar(320) not null check (btrim(email) <> ''),
    password_hash varchar(255),
    first_name    varchar(100) not null,
    last_name     varchar(100) not null,
    patronymic    varchar(100),
    role          varchar(16)  not null check (role in ('ADMIN', 'ORGANIZER', 'TEACHER', 'STUDENT')),
    time_zone     varchar(64)  not null default 'Europe/Moscow',
    enabled       boolean      not null default true,
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create unique index ux_account_email on slotwise.account (lower(btrim(email)));
