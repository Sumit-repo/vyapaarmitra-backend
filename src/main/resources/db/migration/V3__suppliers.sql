-- Suppliers: the other side of the shop's khata — parties the business BUYS from.
-- Mirrors customers/ledger_entries. Balance semantics: current_balance > 0 means
-- the business owes the supplier (payable). No trust scoring for suppliers.

create table suppliers (
    id              uuid primary key default gen_random_uuid(),
    business_id     uuid not null references businesses (id),
    branch_id       uuid not null references branches (id),
    name            text not null,
    phone           text,
    tags            jsonb not null default '[]',
    notes           text,
    -- denormalized aggregates, recomputed by the supplier service on every entry
    current_balance numeric(14, 2) not null default 0,
    oldest_due_date date,
    active          boolean not null default true,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index idx_suppliers_branch_name on suppliers (branch_id, name);
create index idx_suppliers_phone on suppliers (phone);

create table supplier_ledger_entries (
    id          uuid primary key default gen_random_uuid(),
    business_id uuid not null references businesses (id),
    branch_id   uuid not null references branches (id),
    supplier_id uuid not null references suppliers (id),
    entry_type  text not null check (entry_type in ('CREDIT', 'PAYMENT')),
    amount      numeric(14, 2) not null check (amount > 0),
    method      text,
    note        text,
    due_date    date,
    entry_at    timestamptz not null default now(),
    created_by  uuid references users (id),
    created_at  timestamptz not null default now()
);

create index idx_supplier_ledger_supplier_time on supplier_ledger_entries (supplier_id, entry_at);
create index idx_supplier_ledger_branch_time on supplier_ledger_entries (branch_id, entry_at);
