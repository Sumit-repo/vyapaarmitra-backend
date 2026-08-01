-- Cross-business defaulter network — Phase A (mechanism only; nothing is exposed to other
-- merchants yet). Full spec: web docs/defaulter-network.md. LEGAL REVIEW before Phase B/C.

-- Consent to contribute to / see the network. Captured at signup (ToS); dev phase, no retro
-- prompt, so this defaults false and is only flipped for consenting new signups.
alter table users add column defaulter_network_consent boolean not null default false;

-- A merchant's report against a customer phone. Keyed by normalized phone (the network key);
-- customer_id links it to the reporting shop's ledger for pay-to-clear.
create table defaulter_reports (
    id                    uuid primary key default gen_random_uuid(),
    normalized_phone      text not null,
    business_id           uuid not null references businesses (id),
    reported_by_user_id   uuid not null references users (id),
    customer_id           uuid not null references customers (id),
    status                text not null check (status in ('WARNING', 'ACTIVE', 'CLEARED')),
    overdue_days_at_report int not null,
    warning_sent_at       timestamptz not null,
    activated_at          timestamptz,
    cleared_at            timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    unique (normalized_phone, business_id)
);

-- Network lookup (flagged?) is keyed by phone+status; pay-to-clear + the activation job key by customer.
create index idx_defaulter_reports_phone_status on defaulter_reports (normalized_phone, status);
create index idx_defaulter_reports_customer on defaulter_reports (customer_id);
create index idx_defaulter_reports_status_warned on defaulter_reports (status, warning_sent_at);
