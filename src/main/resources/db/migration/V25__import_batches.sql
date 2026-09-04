-- Data import (Feature B): merchants moving from OkCredit bring their khata in as
-- statement PDFs. One row per committed import, plus a nullable import_batch_id on
-- both ledger tables so imported entries can be traced back to their batch and
-- excluded from plan usage (a one-time backfill must not burn the daily entry cap).

create table import_batches (
    id              uuid primary key default gen_random_uuid(),
    business_id     uuid not null references businesses (id),
    kind            text not null check (kind in ('CUSTOMER', 'SUPPLIER')),
    source          text not null check (source = 'OKCREDIT'),
    parties_created int not null default 0,
    entries_created int not null default 0,
    file_name       text,
    created_by      uuid references users (id),
    created_at      timestamptz not null default now()
);

create index idx_import_batches_business on import_batches (business_id, created_at);

alter table ledger_entries add column import_batch_id uuid;
alter table supplier_ledger_entries add column import_batch_id uuid;

create index idx_ledger_entries_import_batch on ledger_entries (import_batch_id);
create index idx_supplier_ledger_entries_import_batch on supplier_ledger_entries (import_batch_id);