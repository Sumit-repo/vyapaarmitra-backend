-- Identity/membership split — phase 0 (additive, dual-model; no behaviour change).
--
-- Splits the two jobs the `users` row was doing: the PERSON (identity: email,
-- phone, password, google) from their ROLE IN A BUSINESS (membership). One
-- identity can hold many memberships over time — so an owner can close a shop and
-- open another, and a staffer can leave one shop and join another, all under the
-- same login. See docs/adr / the identity-membership plan.
--
-- This migration only ADDS. The legacy users.business_id / users.role /
-- user_branch_access are deliberately left in place: auth still reads them until
-- the phase-1 cutover. Dropping them happens in a later migration.

-- Phone is a global identity key once set, but existing rows have none yet, so the
-- column is nullable here (mandatory is enforced in the API for new signups) and
-- uniqueness is a partial index that ignores the legacy NULLs.
alter table users add column phone text;
create unique index uq_users_phone on users (phone) where phone is not null;

create table memberships (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users (id),
    business_id uuid not null references businesses (id),
    role        text not null check (role in ('OWNER', 'BRANCH_MANAGER', 'STAFF')),
    active      boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (user_id, business_id)
);

create index idx_memberships_user on memberships (user_id);
create index idx_memberships_business on memberships (business_id);

-- Branch assignments per membership (mirror of the old user_branch_access).
-- OWNER memberships implicitly have all branches; this holds MANAGER/STAFF scope.
create table membership_branch_access (
    membership_id uuid not null references memberships (id) on delete cascade,
    branch_id     uuid not null references branches (id) on delete cascade,
    primary key (membership_id, branch_id)
);

-- Backfill: exactly one membership per existing user, preserving role/active/timestamps.
insert into memberships (id, user_id, business_id, role, active, created_at, updated_at)
select gen_random_uuid(), u.id, u.business_id, u.role, u.active, u.created_at, u.updated_at
from users u;

-- Backfill branch scope. Unambiguous right now: one membership per user, so we can
-- map each old assignment by user_id.
insert into membership_branch_access (membership_id, branch_id)
select m.id, uba.branch_id
from user_branch_access uba
join memberships m on m.user_id = uba.user_id;
