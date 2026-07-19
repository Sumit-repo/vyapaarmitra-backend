-- VyapaarMitra initial schema.
-- Multi-business / multi-branch aware from day one, even though v1 runs single-shop.
-- Supabase/Postgres is used as plain storage: no RLS, no vendor auth. All access
-- control lives in the backend service layer.

create table businesses (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table branches (
    id          uuid primary key default gen_random_uuid(),
    business_id uuid not null references businesses (id),
    name        text not null,
    address     text,
    active      boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index idx_branches_business on branches (business_id);

create table users (
    id            uuid primary key default gen_random_uuid(),
    business_id   uuid not null references businesses (id),
    email         text not null unique,
    password_hash text not null,
    full_name     text not null,
    role          text not null check (role in ('OWNER', 'BRANCH_MANAGER', 'STAFF')),
    active        boolean not null default true,
    -- bumped to invalidate outstanding refresh tokens (stateless revocation)
    token_version integer not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index idx_users_business on users (business_id);

-- Branch assignments for BRANCH_MANAGER / STAFF. OWNER implicitly has all branches.
create table user_branch_access (
    user_id   uuid not null references users (id) on delete cascade,
    branch_id uuid not null references branches (id) on delete cascade,
    primary key (user_id, branch_id)
);

create table customers (
    id              uuid primary key default gen_random_uuid(),
    business_id     uuid not null references businesses (id),
    branch_id       uuid not null references branches (id),
    name            text not null,
    phone           text,
    tags            jsonb not null default '[]',
    notes           text,
    trust_score     integer not null default 60,
    trust_bucket    text not null default 'WATCH' check (trust_bucket in ('GOOD', 'WATCH', 'RISKY')),
    -- denormalized aggregates, recomputed by the ledger service on every entry
    current_balance numeric(14, 2) not null default 0,
    oldest_due_date date,
    active          boolean not null default true,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index idx_customers_branch_name on customers (branch_id, name);
create index idx_customers_branch_recovery on customers (branch_id, oldest_due_date) where current_balance > 0;
create index idx_customers_phone on customers (phone);

create table ledger_entries (
    id          uuid primary key default gen_random_uuid(),
    business_id uuid not null references businesses (id),
    branch_id   uuid not null references branches (id),
    customer_id uuid not null references customers (id),
    entry_type  text not null check (entry_type in ('CREDIT', 'PAYMENT')),
    amount      numeric(14, 2) not null check (amount > 0),
    method      text,
    note        text,
    due_date    date,
    entry_at    timestamptz not null default now(),
    created_by  uuid references users (id),
    created_at  timestamptz not null default now()
);

create index idx_ledger_customer_time on ledger_entries (customer_id, entry_at);
create index idx_ledger_branch_time on ledger_entries (branch_id, entry_at);

create table message_templates (
    id          uuid primary key default gen_random_uuid(),
    business_id uuid not null references businesses (id),
    -- null branch_id = business-wide template
    branch_id   uuid references branches (id),
    channel     text not null check (channel in ('SMS', 'WHATSAPP')),
    category    text not null,
    name        text not null,
    body        text not null,
    enabled     boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create index idx_templates_business on message_templates (business_id);

create table reminder_logs (
    id            uuid primary key default gen_random_uuid(),
    business_id   uuid not null references businesses (id),
    branch_id     uuid not null references branches (id),
    customer_id   uuid not null references customers (id),
    template_id   uuid references message_templates (id),
    channel       text check (channel in ('SMS', 'WHATSAPP', 'CALL')),
    outcome       text not null check (outcome in ('REMINDER_SENT', 'PROMISE_MADE', 'PAID')),
    promised_date date,
    note          text,
    created_by    uuid references users (id),
    created_at    timestamptz not null default now()
);

create index idx_reminders_customer_time on reminder_logs (customer_id, created_at);
create index idx_reminders_branch_time on reminder_logs (branch_id, created_at);
