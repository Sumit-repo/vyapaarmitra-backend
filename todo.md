# VyapaarMitra Backend — TODO

_Spring Boot API. Companion to `../vyapaarmitra-web/todo.md` (web BFF). Sequenced
as a punch-list. Last updated: 2026-07-30._

## ✅ Shipped 2026-07-28 — subscriptions & billing
New `com.vyapaarmitra.api.subscription` package + `V7__subscriptions.sql`:
- `subscription` + `billing_events` tables (backfilled existing businesses onto a
  trial anchored to `created_at`).
- `PlanService` — source of truth: `effectivePlan` (trial → PRO, dunning grace,
  cancel-until-period-end) + usage **derived** from invoices/ledger in
  Asia/Kolkata. `PlanCatalog` mirrors the web `lib/plan-model.ts`.
- Trial bootstrap on every new business (`BusinessProvisioningService`).
- `/me` plan block + `GET /api/v1/plan`.
- Enforcement via `PlanGuard` → `402 PLAN_LIMIT` (+ `reason`): metered caps on
  invoice/ledger create; feature locks on recovery, reports, branches, staff.
- Razorpay: `BillingService`/`BillingController` (checkout + cancel),
  HMAC-verified idempotent `/webhooks/razorpay` (the only activation path).
- Tests: `PlanCatalog`, `PlanService.effectivePlan`, `RazorpaySignature`,
  `RazorpayWebhookService` (activation + idempotency). Suite: **95 green**.

## ✅ Shipped 2026-07-29 — ledger running balance (server-computed)
`GET /customers/{id}/ledger` and `GET /suppliers/{id}/ledger` now return a per-entry
`balanceAfter` on `EntryResponse`/`SupplierEntryResponse`. It's anchored to the account's
current balance minus `signedSumAfter(...)` (an aggregate over entries newer than the
page's top row) and walked down the page via the pure `LedgerMath.runningBalancesDesc`
(unit-tested) — so a partial page renders correct balances with no full replay. This
unblocks real ledger pagination on the web (see `../vyapaarmitra-web/todo.md` §D) and
fixes a latent bug where >100 entries truncated + mis-summed balances client-side.

## ✅ Shipped 2026-07-30 — plan tightening (recovery→Pro, trust strip, GST invoices)
Follows `docs/subscriptions.md` §10 in the web repo:
- **Recovery is now Pro-only.** `PlanCatalog` `LITE.recovery = false` (+ `PlanCatalogTest`
  `lite.recovery()).isFalse()`). No controller change — `RecoveryController` already gates
  on `Feature.RECOVERY` via `PlanGuard`, so the catalog flip does it. §10.1.
- **Trust analytics field-strip.** `trustAnalytics` rides inside customer/dashboard
  responses (no route to guard), so it's stripped at the DTO layer: `CustomerService` and
  `DashboardService` inject `PlanService`, compute `trustEntitled(authUser)`, and thread
  `includeTrust` into `CustomerDtos.CustomerResponse/CustomerListItem.from(c, includeTrust)`
  and `TopDebtor` — emitting `trustScore=0, trustBucket=null` when not entitled. One check
  per request. §10.3. _(Web adds a client-side gate too as defense-in-depth.)_
- **Billing facts on `PlanView`.** Added `billingPeriod`, `currentPeriodEnd`,
  `cancelAtPeriodEnd` to `PlanDtos.PlanView`; `PlanService.view()` populates them from the
  subscription so the web can show renewal/cancel dates. §10.2.
- **GST invoice endpoint.** `GET /api/v1/billing/invoices` → `BillingService.invoices()`
  loads the caller's subscription and calls `RazorpayClient.listInvoices(subscription_id)`
  (`GET /v1/invoices?subscription_id=…`), returning `[{ id, status, amount, issuedAt,
  shortUrl }]` (`BillingDtos.InvoiceItem`). Auth-scoped (id never from the client); empty
  until they've paid. §10.4.
- Suite: **99 green** (contract + service tests updated).

### ⏭️ Open follow-up from today
- [ ] **Stamp GSTIN on the Razorpay invoice.** Razorpay only puts the business GSTIN on
      the invoice if it's set on the customer/subscription. Pass the business GSTIN through
      `createSubscription(...)` so the SaaS-charge invoice is GST-valid. _§10.4 "Still open"._

---

## ⏭️ Left for tomorrow (2026-07-29) — pick up here

### P1 — correctness / hardening (do first)
- [x] **`PlanService.getOrCreate` can run inside a read-only tx.** ✅ Done. The lazy
      seed is split out into `createTrial(...)` annotated
      `@Transactional(propagation = REQUIRES_NEW)`, reached through a `@Lazy` self-proxy
      so it commits in its own read-write tx even when the caller
      (`AuthService.me/login/refresh`) is `readOnly`. `getOrCreate` itself is now
      `readOnly = true`; `createTrial` re-checks inside the new tx to avoid a
      double-insert race. _File: `subscription/PlanService.java`._
- [ ] **DB/migration integration test.** ⚠️ Deferred — needs Testcontainers Postgres.
      `V7` uses `jsonb` / `gen_random_uuid()` / `timestamptz`, so an H2 `@DataJpaTest`
      can't run the migration. Add `org.testcontainers:postgresql` + a `@Testcontainers`
      `@DataJpaTest` (Flyway on, `ddl-auto=validate`) that saves/reads a `Subscription`
      + `BillingEvent` and asserts the unique `(gateway, gateway_event_id)` constraint.
      Left out here to keep the suite green + offline; wire it with CI Docker.
- [ ] **Confirm the real Razorpay webhook shape in test mode.** We assume the
      `X-Razorpay-Event-Id` header and `payload.subscription.entity.current_end`.
      Fire real test-mode events and verify field paths before going live.
      _File: `subscription/RazorpayWebhookController/Service.java`._

### P2 — billing completeness (from docs/subscriptions.md)
- [ ] **Enforce the `automation` (Pro) feature.** `PlanGuard` covers recovery,
      reports, branches, staff — but scheduled/auto-send reminders aren't a distinct
      endpoint yet, so `automation` isn't gated. Add the guard when a scheduling
      endpoint exists (don't gate the manual reminder/template flows). _§5b._
- [ ] **Cancel/downgrade portal polish + annual proration** (§4.4, §9). Current
      cancel marks `CANCELLED` (access to period end); mid-cycle Lite→Pro upgrade
      just starts a fresh period. Proration deferred.
- [x] **Daily job: flip `TRIALING → EXPIRED`** past `trial_ends_at`. ✅ Done —
      `SubscriptionMaintenance.expireLapsedTrials()` (`@Scheduled` cron `0 15 0 * * *`
      zone `Asia/Kolkata`) runs a bulk `@Modifying` update
      (`SubscriptionRepository.expireLapsedTrials`); `@EnableScheduling` added on the app.
      Effective-plan already treats a lapsed trial as FREE at read time, so this is
      purely for clean stored status + win-back targeting. Unit test:
      `SubscriptionMaintenanceTest`. **Win-back on `EXPIRED` still TODO.** _§6, §8 P3._
- [ ] **Populate `gateway_customer_id`.** Hosted-checkout subscriptions don't
      pre-create a customer, so this column stays null. Fine today; revisit if we
      need per-customer lookups or the Razorpay customer portal.

### P3 — cross-cutting
- [x] **CI runs `./mvnw test`** on push/PR. ✅ `.github/workflows/ci.yml` already runs
      `./mvnw -B verify`; the web repo now has a matching workflow too (see
      `../vyapaarmitra-web/todo.md` §A).
- [ ] **Cross-repo catalog contract.** `PlanCatalogTest` pins tiers/pricing to the
      spec on the Java side; the web `lib/plan-model.ts` is kept in lockstep by
      hand. Consider a shared fixture / generated constants so they can't drift.

### Setup notes for tomorrow-me
- Run tests: `./mvnw test` (offline: `./mvnw -o test`). Suite is **99 green**
  (2026-07-30 plan tightening: recovery→Pro, trust strip, billing facts, GST invoices).
- Razorpay config lives under `app.razorpay.*` — see `.env.example` (test-mode
  `rzp_test_*` keys work immediately, no KYC). Billing is disabled (clear 402-style
  error) when keys are blank.
- Nothing committed yet — all working-tree changes.
