# VyapaarMitra Backend — TODO

_Spring Boot API. Companion to `../vyapaarmitra-web/todo.md` (web BFF). Sequenced
as a punch-list. Last updated: 2026-07-28._

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

---

## ⏭️ Left for tomorrow (2026-07-29) — pick up here

### P1 — correctness / hardening (do first)
- [ ] **`PlanService.getOrCreate` can run inside a read-only tx.** `TokenIssuer.toMe`
      → `view()` → `getOrCreate()` is reached from `AuthService.me/login/refresh`
      (`@Transactional(readOnly = true)`). Backfill + trial bootstrap mean the
      lazy-create branch shouldn't fire in practice, but if it ever does the insert
      may silently not flush. Make it safe: `@Transactional(propagation = REQUIRES_NEW)`
      on the create, or guarantee the row always exists before read.
      _File: `subscription/PlanService.java`._
- [ ] **DB/migration integration test.** All subscription tests are unit/slice —
      nothing exercises `V7` + the JPA mappings. Add a `@DataJpaTest` (or Testcontainers
      Postgres) that runs the migration, saves/reads a `Subscription` + `BillingEvent`,
      and asserts the unique `(gateway, gateway_event_id)` constraint.
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
- [ ] **Daily job: flip `TRIALING → EXPIRED`** past `trial_ends_at` for clean
      reporting (effective-plan already handles this at read time, so read paths are
      correct without it). Win-back on `EXPIRED`. _§6, §8 P3._
- [ ] **Populate `gateway_customer_id`.** Hosted-checkout subscriptions don't
      pre-create a customer, so this column stays null. Fine today; revisit if we
      need per-customer lookups or the Razorpay customer portal.

### P3 — cross-cutting
- [ ] **CI runs `./mvnw test`** on push/PR (paired with the web suite — see
      `../vyapaarmitra-web/todo.md` §A). Nothing enforces green yet.
- [ ] **Cross-repo catalog contract.** `PlanCatalogTest` pins tiers/pricing to the
      spec on the Java side; the web `lib/plan-model.ts` is kept in lockstep by
      hand. Consider a shared fixture / generated constants so they can't drift.

### Setup notes for tomorrow-me
- Run tests: `./mvnw test` (offline: `./mvnw -o test`). Suite is **95 green**.
- Razorpay config lives under `app.razorpay.*` — see `.env.example` (test-mode
  `rzp_test_*` keys work immediately, no KYC). Billing is disabled (clear 402-style
  error) when keys are blank.
- Nothing committed yet — all working-tree changes.
