-- Settable defaults for the "which shop / which branch do I land on" experience.
--
--  * users.default_business_id   — the identity's preferred shop at login. defaultActive()
--    prefers it (when still an active membership) over the "newest membership" fallback.
--  * memberships.preferred_branch_id — the person's preferred/last-used branch *within one
--    business*. NULL means "no preference": All-Branches for an owner, first assigned branch
--    for staff. Lives on the membership because branch scope is per-(person, business).
--
-- Both FKs SET NULL on delete so removing a business/branch can never orphan a stale pointer;
-- the app also re-validates against current access on load.

ALTER TABLE users
    ADD COLUMN default_business_id uuid
        REFERENCES businesses (id) ON DELETE SET NULL;

ALTER TABLE memberships
    ADD COLUMN preferred_branch_id uuid
        REFERENCES branches (id) ON DELETE SET NULL;
