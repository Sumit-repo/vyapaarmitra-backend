-- Self-service account deletion with a 30-day soft-delete grace period.
-- See docs/account-deletion.md (single source of truth). Requesting deletion marks
-- the identity (and the businesses it OWNS) with deletion_scheduled_at = now + 30d;
-- a request guard freezes normal API calls during the grace window, and an idempotent
-- purge hard-deletes once it lapses. Nothing is destroyed until then.

-- users: the soft-delete marker (freeze + purge key)
alter table users add column deletion_scheduled_at timestamptz null;
create index idx_users_deletion_scheduled_at on users (deletion_scheduled_at)
    where deletion_scheduled_at is not null;

-- businesses: owned businesses are scheduled alongside the owner
alter table businesses add column deletion_scheduled_at timestamptz null;
create index idx_businesses_deletion_scheduled_at on businesses (deletion_scheduled_at)
    where deletion_scheduled_at is not null;

-- audit + churn dataset + who to contact during grace
create table account_deletion_requests (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references users (id) on delete cascade,
    reason          varchar(32) null,           -- DeletionReason enum, nullable
    feedback        varchar(500) null,
    requested_at    timestamptz not null default now(),
    scheduled_at    timestamptz not null,       -- requested_at + 30d
    cancelled_at    timestamptz null,           -- set on reactivation
    completed_at    timestamptz null            -- set when purge hard-deletes
);

create index idx_adr_user_active on account_deletion_requests (user_id)
    where cancelled_at is null and completed_at is null;
