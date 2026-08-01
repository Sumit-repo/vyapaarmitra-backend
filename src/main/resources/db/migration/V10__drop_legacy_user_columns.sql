-- Identity/membership split — phase 2 (DESTRUCTIVE). Drops the legacy per-business
-- fields the `users` row used to carry. Auth cut over to memberships in phase 1
-- (V9 + the phase-1 code), so nothing reads these anymore. Verified on live Postgres
-- before this ran: memberships == users, membership_branch_access == user_branch_access,
-- 0 role/business mismatches, every user has >=1 membership.
--
-- After this, a person's identity (email/phone/password/google) lives on `users`;
-- their role + branch scope in each business lives on `memberships` /
-- `membership_branch_access`.

drop table if exists user_branch_access;

alter table users drop column if exists role;
alter table users drop column if exists business_id;
