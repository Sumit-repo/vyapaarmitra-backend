-- Per-customer SMS reminder preferences.
-- customer_id is the PK (strict 1:1 with customers). Settings are created
-- lazily on first access from the backend, not at customer-creation time.
-- SMS sending itself is an Android client responsibility (opens the OS SMS
-- composer); the backend owns scheduling metadata and reminder history only.

create table customer_reminder_settings (
    customer_id               uuid primary key references customers (id) on delete cascade,
    business_id               uuid not null references businesses (id),
    branch_id                 uuid not null references branches (id),
    sms_reminder_enabled      boolean not null default false,
    preferred_channel         text not null default 'SMS'
                                  check (preferred_channel in ('SMS', 'WHATSAPP')),
    reminder_template_id      uuid references message_templates (id) on delete set null,
    last_reminder_prompted_at timestamptz,
    last_reminder_sent_at     timestamptz,
    last_reminder_type        text,
    next_reminder_due_at      timestamptz,
    auto_schedule_enabled     boolean not null default false,
    reminder_notes            text,
    updated_at                timestamptz not null default now()
);

-- Used by the /reminders/due query: find sms-enabled customers with a
-- scheduled next_reminder_due_at, scoped to branch.
create index idx_reminder_settings_due
    on customer_reminder_settings (branch_id, next_reminder_due_at)
    where sms_reminder_enabled = true;
