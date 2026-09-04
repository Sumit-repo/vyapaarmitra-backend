-- NEW trust bucket: a customer with no CREDIT history is "new", not medium-risk.
-- TrustScoreService used to park zero-credit customers in WATCH (score 60), which
-- displayed a risk judgment the customer had done nothing to earn. The computed
-- score is unchanged — only the label separates "no history yet" from "watching".
--
-- Originally numbered V21, but V22 shipped first (revision 00015, Sep 2026) and this
-- was never applied anywhere — a pending migration numbered below the newest applied
-- one fails Flyway validate on every boot (outOfOrder=false), which is why all
-- deploys since 2026-09-01 failed with "failed to start and listen on PORT".

alter table customers drop constraint if exists customers_trust_bucket_check;
alter table customers add constraint customers_trust_bucket_check
    check (trust_bucket in ('GOOD', 'WATCH', 'RISKY', 'NEW'));
alter table customers alter column trust_bucket set default 'NEW';

-- Backfill: every customer who has never been given a credit entry still sits in WATCH.
update customers c
set trust_bucket = 'NEW'
where not exists (
    select 1 from ledger_entries le
    where le.customer_id = c.id and le.entry_type = 'CREDIT'
);