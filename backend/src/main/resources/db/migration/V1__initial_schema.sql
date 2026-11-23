-- Argus: schema inicial. SQL compatível com PostgreSQL e H2 (modo PostgreSQL).

create table users (
    id                    uuid                     primary key,
    name                  varchar(120)             not null,
    email                 varchar(160)             not null,
    password_hash         varchar(100)             not null,
    role                  varchar(20)              not null,
    sector                varchar(80),
    active                boolean                  not null,
    failed_login_attempts integer                  not null,
    locked_until          timestamp with time zone,
    last_login_at         timestamp with time zone,
    created_at            timestamp with time zone not null,
    updated_at            timestamp with time zone not null,
    version               bigint                   not null,
    constraint uk_users_email unique (email),
    constraint ck_users_role check (role in ('COLLABORATOR', 'MANAGER', 'ADMIN'))
);

-- Uma linha por login (dispositivo). Guarda apenas o hash SHA-256 do refresh token.
create table auth_sessions (
    id                  uuid                     primary key,
    user_id             uuid                     not null references users (id),
    refresh_token_hash  varchar(64)              not null,
    previous_token_hash varchar(64),
    created_at          timestamp with time zone not null,
    rotated_at          timestamp with time zone,
    expires_at          timestamp with time zone not null,
    max_expires_at      timestamp with time zone not null,
    revoked_at          timestamp with time zone,
    revoked_reason      varchar(30),
    user_agent          varchar(255),
    ip_address          varchar(45),
    version             bigint                   not null
);
create index ix_auth_sessions_user on auth_sessions (user_id);
create index ix_auth_sessions_expires on auth_sessions (expires_at);

create table agents (
    id                  uuid                     primary key,
    name                varchar(120)             not null,
    description         varchar(500)             not null,
    purpose             varchar(2000)            not null,
    category            varchar(20)              not null,
    icon                varchar(16),
    status              varchar(20)              not null,
    release_version     varchar(20)              not null,
    model               varchar(120),
    data_classification varchar(20)              not null,
    tool_access         varchar(40)              not null,
    owner_id            uuid                     not null references users (id),
    created_by_id       uuid                     not null references users (id),
    submitted_by_id     uuid                     references users (id),
    submitted_at        timestamp with time zone,
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null,
    version             bigint                   not null,
    constraint ck_agents_status check (status in ('DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);
create index ix_agents_status on agents (status);

-- Histórico imutável de pareceres de homologação.
create table agent_reviews (
    id                 uuid                     primary key,
    agent_id           uuid                     not null references agents (id),
    reviewer_id        uuid                     not null references users (id),
    decision           varchar(20)              not null,
    justification      varchar(1000)            not null,
    reviewed_version   varchar(20)              not null,
    checklist_complete boolean                  not null,
    reviewed_at        timestamp with time zone not null
);
create index ix_agent_reviews_agent on agent_reviews (agent_id, reviewed_at);

-- Trilha de auditoria (somente metadados; sem FKs para preservar o histórico).
create table audit_events (
    id          uuid                     primary key,
    occurred_at timestamp with time zone not null,
    action      varchar(40)              not null,
    outcome     varchar(20)              not null,
    actor_id    uuid,
    actor_name  varchar(120),
    agent_id    uuid,
    agent_name  varchar(120),
    detail      varchar(255)
);
create index ix_audit_events_occurred_at on audit_events (occurred_at);
create index ix_audit_events_agent on audit_events (agent_id);
