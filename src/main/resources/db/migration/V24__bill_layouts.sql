-- Bill design (Pro): the shop's chosen bill preset + toggles (UPI QR, shop logo, footer
-- note). One row per business; an absent row means CLASSIC with empty options — the free
-- default, never seeded, so existing bills don't change. Logo lives on businesses (set
-- through the bill-layout upsert after an /attachments upload).
-- Preset gating lives in the controller (PlanGuard → Feature.BILL_LAYOUTS).

create table bill_layouts (
    id          uuid primary key default gen_random_uuid(),
    business_id uuid not null unique references businesses (id),
    layout      varchar not null check (layout in ('CLASSIC', 'MINIMAL', 'BOLD')),
    options     jsonb not null default '{}',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

alter table businesses add column logo_url text;
alter table businesses add column logo_public_id text;