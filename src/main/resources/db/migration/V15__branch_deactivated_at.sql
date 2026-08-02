-- Anchor for the short branch reactivation cooldown (see BranchController). NULL while active.
ALTER TABLE branches ADD COLUMN deactivated_at timestamptz;
