-- Checkout intent: the plan/period a business is *trying* to buy. These are applied
-- to plan/billing_period ONLY when a verified subscription.activated/charged webhook
-- arrives (RazorpayWebhookService). This keeps a started-but-unpaid — or cancelled —
-- checkout from granting the tier, especially for an already-ACTIVE customer upgrading,
-- whose effective plan would otherwise read the new tier the instant checkout opened.
alter table subscriptions
    add column pending_plan text check (pending_plan in ('LITE', 'PRO'));

alter table subscriptions
    add column pending_billing_period text check (pending_billing_period in ('MONTHLY', 'YEARLY'));
