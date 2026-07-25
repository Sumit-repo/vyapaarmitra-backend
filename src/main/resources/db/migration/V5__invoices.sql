-- Bills / invoices: the kaccha (informal estimate, no GST) and pakka (GST tax
-- invoice) documents an Indian shop hands out. A credit bill posts its balance
-- to the customer's khata (ledger_entries) — which is why billing lives inside
-- the ledger app at all. Line items are stored inline as jsonb since they are
-- always read and written together with the parent bill.

create table invoices (
    id                uuid primary key default gen_random_uuid(),
    business_id       uuid not null references businesses (id),
    branch_id         uuid not null references branches (id),
    bill_type         text not null check (bill_type in ('KACCHA', 'PAKKA')),
    number            text not null,
    -- party: optional link to a customer; free-text fields for walk-ins
    party_customer_id uuid references customers (id),
    party_name        text,
    party_phone       text,
    party_gstin       text,
    -- pakka (GST) header
    seller_gstin      text,
    place_of_supply   text,
    inter_state       boolean not null default false,
    -- line items + money (totals are computed server-side by InvoiceMath)
    items             jsonb not null default '[]',
    discount          numeric(14, 2) not null default 0,
    subtotal          numeric(14, 2) not null default 0,
    tax_total         numeric(14, 2) not null default 0,
    grand_total       numeric(14, 2) not null default 0,
    amount_received   numeric(14, 2) not null default 0,
    payment_mode      text not null check (payment_mode in ('CASH', 'UPI', 'CREDIT')),
    status            text not null check (status in ('PAID', 'PARTIAL', 'UNPAID')),
    notes             text,
    -- the ledger CREDIT entry raised when a credit bill lands on a customer's khata
    ledger_entry_id   uuid references ledger_entries (id),
    created_by        uuid references users (id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index idx_invoices_branch_time on invoices (branch_id, created_at desc);
create index idx_invoices_branch_type on invoices (branch_id, bill_type);
create index idx_invoices_customer on invoices (party_customer_id);
create unique index uq_invoices_branch_number on invoices (branch_id, number);
