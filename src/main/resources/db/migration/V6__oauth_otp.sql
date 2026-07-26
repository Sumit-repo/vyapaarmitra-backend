-- Google OAuth + email OTP login, added alongside the existing email/password flow.
-- Accounts created via Google or OTP may have no password, so password_hash becomes nullable.

alter table users alter column password_hash drop not null;
alter table users add column email_verified boolean not null default false;
alter table users add column google_sub text unique;

-- Short-lived one-time codes emailed for passwordless login and email-verified signup.
-- Codes are stored hashed (never in clear); a row is spent once consumed_at is set.
create table login_codes (
    id          uuid primary key default gen_random_uuid(),
    email       text not null,
    code_hash   text not null,
    purpose     text not null check (purpose in ('LOGIN', 'SIGNUP')),
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    attempts    integer not null default 0,
    created_at  timestamptz not null default now()
);

create index idx_login_codes_email on login_codes (email);
create index idx_login_codes_email_created on login_codes (email, created_at);
