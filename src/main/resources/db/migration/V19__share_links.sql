-- Public, verified share links to a customer ledger or a bill (docs/customer-web-viewer.md).
-- The token is the first factor; the phone last-4 is the second. Stable + revocable, no time
-- expiry; brute-force is contained by failed_attempts + locked_until.
create table share_links (
    id              uuid primary key,
    token           text        not null unique,
    business_id     uuid        not null references businesses (id),
    type            text        not null check (type in ('LEDGER', 'BILL')),
    customer_id     uuid        references customers (id),
    invoice_id      uuid        references invoices (id),
    created_by      uuid        not null,
    revoked         boolean     not null default false,
    failed_attempts integer     not null default 0,
    locked_until    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index idx_share_links_token on share_links (token);
