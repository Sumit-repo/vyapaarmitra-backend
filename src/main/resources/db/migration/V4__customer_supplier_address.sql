-- Optional street/area address for customers and suppliers, shown in the
-- directory tables. "Last activity" is derived from the existing updated_at
-- (bumped on every ledger entry + profile edit) — no new column needed.

alter table customers add column address text;
alter table suppliers add column address text;
