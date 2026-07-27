-- Subscriptions: the real source of truth for plan entitlement, replacing the
-- web BFF's signed cookie. One row per business. `plan`/`status` are denormalized
-- for a fast read on every request; the *effective* plan (trial → PRO, grace
-- windows) is computed at read time, never stored. Usage (entries/day, pakka/month)
-- is derived from invoices + ledger_entries by created_at, so there is no counter
-- to drift. See docs/subscriptions.md.

create table subscriptions (
    id                  uuid primary key default gen_random_uuid(),
    business_id         uuid not null unique references businesses (id),
    plan                text not null default 'FREE' check (plan in ('FREE', 'LITE', 'PRO')),
    status              text not null default 'TRIALING'
                            check (status in ('TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED')),
    billing_period      text check (billing_period in ('MONTHLY', 'YEARLY')),
    trial_ends_at       timestamptz not null,
    current_period_end  timestamptz,
    grace_until         timestamptz,
    gateway             text,
    gateway_sub_id      text unique,
    gateway_customer_id text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

-- Idempotency + audit for gateway callbacks. A given event is processed at most once.
create table billing_events (
    id               uuid primary key default gen_random_uuid(),
    gateway          text not null,
    gateway_event_id text not null,
    type             text not null,
    payload          jsonb not null,
    processed_at     timestamptz,
    received_at      timestamptz not null default now(),
    unique (gateway, gateway_event_id)
);

-- Backfill existing businesses onto a trial anchored to when they were created,
-- so nobody loses (or silently gains) access when this ships.
insert into subscriptions (business_id, plan, status, trial_ends_at)
select b.id, 'FREE', 'TRIALING', b.created_at + interval '14 days'
from businesses b
where not exists (select 1 from subscriptions s where s.business_id = b.id);

-- Usage is derived by (business_id, created_at); invoices already has a
-- branch/time index but usage counts are business-scoped, so add these.
create index idx_invoices_business_created on invoices (business_id, created_at);
create index idx_ledger_business_time on ledger_entries (business_id, entry_at);
