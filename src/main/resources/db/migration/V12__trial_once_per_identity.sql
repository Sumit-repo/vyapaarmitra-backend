-- Trial is once per IDENTITY, not per business. After the identity/membership split a
-- single person can create many businesses; without this each would seed a fresh 14-day
-- Pro trial (trial-farming). The first business a person creates gets the trial; later
-- ones start FREE.
alter table users add column trial_used boolean not null default false;

-- Backfill: anyone who already owns a business has effectively consumed their one trial.
update users u set trial_used = true
where exists (select 1 from memberships m where m.user_id = u.id and m.role = 'OWNER');

-- A business created by someone who already trialed starts with no trial, so a
-- subscription must be able to exist without a trial window.
alter table subscriptions alter column trial_ends_at drop not null;
