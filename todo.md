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
- [x] **Stamp GSTIN on the Razorpay invoice.** ✅ Implemented. New `businesses.gstin`
      (`V11__business_gstin.sql`) + `Business.gstin`; owner-only `PATCH /business` now accepts
      `gstin` (upper/trim, blank clears) and `BusinessResponse` returns it. `BillingService.checkout`
      pre-creates a Razorpay customer carrying the GSTIN (`RazorpayClient.createCustomerWithGstin`,
      `fail_existing:0`) and ties the subscription to it via `customer_id` — which also **populates
      `gateway_customer_id`** for GST shops (see P2 item below). Non-GST shops behave as before
      (null customer). **Suite 116 green.** ⚠️ Needs Razorpay **test-mode** verification of the
      `/customers` + `customer_id` field shape before go-live (same bucket as the webhook-shape check).

## ✅ Shipped 2026-08-01 — identity/membership split (phase 0+1) + editable shop name
Pushed to `main` (commit `23756a8`). See memory `vyapaarmitra-identity-membership-split`.
- **Phase 0 (`V9__identity_memberships.sql`):** `memberships` + `membership_branch_access`
  + `users.phone` (partial-unique); backfills one membership per existing user + branch
  access. Legacy `users.business_id/role/user_branch_access` kept (dual-model). New
  `membership/` pkg (`Membership`, `MembershipRepository`, `MembershipService`).
- **Phase 1 (auth cutover):** tokens scoped to the active membership (access `bid`/`role`,
  refresh carries `bid`). Login → default (newest active) membership; `GET /memberships`,
  `POST /session` (switch). Provisioning creates the OWNER membership + takes phone.
  `BranchAccessService` reads membership branch scope. Staff invite = find-or-create
  identity + membership (no more `EMAIL_TAKEN`; `ALREADY_MEMBER` + `LAST_OWNER` guards);
  deactivate = membership-only revoke (other shops untouched). Phone mandatory on password
  signup + staff invite. Owner-only `PATCH /business` rename. Tests: `MembershipServiceTest`,
  `UserServiceTest`, + register-phone/select-business validation. **Suite 116 green.**

### ⏭️ Open — identity/membership (sequenced; each blocks the next)
- [x] **Verify Phase 1 on real Postgres — BLOCKS EVERYTHING BELOW.** ✅ Verified live 2026-08-01
      on Render: `users==memberships` (1==1, diff 0), `user_branch_access==membership_branch_access`
      (0==0), 0 role/business mismatches, every user has ≥1 membership. Single-owner account, clean pass.
- [x] **Phase 2 — drop legacy columns** (`V10__drop_legacy_user_columns.sql`): dropped
      `user_branch_access`, `users.role`, `users.business_id` (all `if exists`, idempotent).
      Removed `businessId`/`role`/`branchIds` from `User`; `UserDirectory` now scopes name
      resolution via `memberships` (was `users.business_id`); stopped writing the legacy columns
      in `BusinessProvisioningService` + `UserService.create`. Fixed `scripts/demo_seed.sql` to
      resolve owner/business through `memberships`. **Suite 116 green.** ⚠️ On next Render deploy
      watch the boot: Flyway V10 + V11 then `ddl-auto=validate` must pass (entity now matches the
      post-drop schema) — verify by hand.
- [x] **Phase 2b — per-person subscription: CANCELLED (decided 2026-08-01).** After working the
      loopholes (cap-evasion + Pro-plan-sharing via admin transfer), decided to **keep billing
      per-business** — which kills the whole loophole class and is already how the code works
      (`subscriptions.business_id`). No admin marker, no admin-transfer plan-switching, no
      per-person resolution, **no `maxBusinesses` cap** (unlimited business creation = more paid
      conversions). Full rationale in web `docs/subscriptions.md` §11.
- [x] **Trial once per identity + create-another-business endpoint.** ✅ Done (2026-08-01).
      `V12__trial_once_per_identity.sql`: adds `users.trial_used` (backfills existing owners→true)
      and makes `subscriptions.trial_ends_at` nullable. `User.trialUsed`; `Subscription.trialEndsAt`
      now nullable. `BusinessProvisioningService` refactored — `provisionAdditionalBusiness(owner,
      name, branch)` reuses an existing identity; `seedSubscription` gives the Pro trial **only if
      `!owner.trialUsed`** (flipping it true), else creates the sub `EXPIRED`/`FREE` with null trial
      (→ `effectivePlan` = FREE). New **`POST /api/v1/business`** (any authenticated identity →
      business + first branch + OWNER membership + templates + sub; 201) — no cap. Client switches
      via existing `POST /session` + `GET /memberships`. Tests: `BusinessProvisioningServiceTest`
      (trial vs no-trial) + create-validation. **Suite 119 green.**
- [ ] **Phase 3 — business lifecycle + create-another-business.** `maxBusinesses` is **dropped**
      (see Phase 2b cancellation — no per-user cap). Remaining: (a) **create-another-business**
      endpoint — authenticated identity POSTs → provisioning makes the business + their OWNER
      membership + a subscription (trial if first-ever per the trial-once-per-identity guard, else
      FREE); unblocks the web "create another business" + select-business UIs. (b) `business.status`
      (ACTIVE/CLOSED) + `closed_at`; close/reopen endpoints; **30-day reactivate grace** (same for a
      deactivated staff membership → after grace, re-invite). **DECISION NEEDED:** after-30-day
      behavior (proposed: reactivate-with-confirmation).
- [ ] **Phase 4 — mobile.** Mirror phone (register/login) + switcher + staff toggles.
      ⚠️ mobile `register` 400s vs the new backend until it sends `phone`.
- [x] **`created_by` attribution surfacing.** ✅ Done. New `UserDirectory` (user pkg) batch-
      resolves user ids → full names, business-scoped (`findByBusinessIdAndIdIn`). `createdBy`
      + `createdByName` now on `EntryResponse`, `SupplierEntryResponse`, `InvoiceResponse`,
      `ReminderResponse`; ledger/supplier/reminder list endpoints resolve one map per page,
      single-entity paths resolve one. Suite **116 green**.
- [ ] **Google/OTP phone.** Those signups create null-phone identities — add a phone-capture
      prompt (or Settings phone field) so every account has one. Dev-OTP `001001` intentionally
      skipped.
- [ ] **Cross-business defaulter network — UNPARKED, SPEC LOCKED (2026-08-01).** Full spec in
      web `docs/defaulter-network.md`. Merchant-network reputation keyed by **normalized customer
      phone**, anonymized (flagged/not — no amount/merchant/history), exact-match-only lookup (no
      directory/search). **Lifecycle:** 90-day overdue qualifies → **merchant manually sends the
      warning SMS** (the one required comms) → **+7 days** unresolved → `ACTIVE` (Defaulter) →
      **pay-to-clear** removes it. Consent captured at signup (no retro prompt — dev). ⚠️ **Do not
      go live without counsel** (CICRA / DPDP 2023 / defamation; ToS+consent copy). **Phases:**
      - [x] **A — mechanism (no visibility): ✅ Done (2026-08-01, backend 136 green).**
            `V13__defaulter_network.sql` (`defaulter_reports` + `users.defaulter_network_consent`).
            New `defaulter/` pkg: `DefaulterReport`/`Status`/`Repository`, `DefaulterService.warn`
            (guards 90-day overdue + phone; logs a `ReminderLog`; upserts a `WARNING` report with the
            overdue snapshot) + `clearForCustomerIfSettled` (pay-to-clear), `DefaulterMaintenance`
            (`@Scheduled` daily 00:20 IST: WARNING past +7d → ACTIVE if still owed, else CLEARED),
            `POST /api/v1/defaulter/warn`. Pay-to-clear wired into `LedgerService.createEntry`. Tests:
            `DefaulterServiceTest`, `DefaulterMaintenanceTest`, `DefaulterControllerValidationTest`.
            Nothing exposed cross-merchant yet (safe to deploy). ⚠️ V13 on next Render deploy → watch boot.
      - [x] **B — lookup + badge: ✅ Done (2026-08-01; backend 140 green, web 55 green).**
            `GET /api/v1/risk?phone=<exact>` → `{flagged}` (`DefaulterService.isPhoneFlagged`,
            normalizes + `existsByNormalizedPhoneAndStatus(ACTIVE)`, exact-match only, no search).
            Also fixed a latent bug: missing required `@RequestParam` now 400 (`MISSING_PARAMETER`),
            was 500 — helps every endpoint. Web: `lib/api/risk.ts` (`useRiskCheck` exact-10-digit
            only + `useSendDefaulterWarning`); `AccountAvatar` red ring; `LedgerWorkspace`
            `headerFlagged` → ring + "Defaulter" pill; customer ledger shows a **"Report defaulter"**
            action when 90+ days overdue (→ `POST /defaulter/warn`); new-customer form shows an inline
            defaulter warning as the phone is typed. Tests: `RiskControllerValidationTest`,
            `DefaulterServiceTest` (isPhoneFlagged). ⚠️ Consent gating still Phase C — until then any
            authenticated caller can look up. Rate-limit relies on the global filter.
      - [x] **C — consent gating + ToS: ✅ Done (2026-08-01; backend 142 green, web 55 green).**
            `users.defaulter_network_consent` captured at signup (`RegisterRequest.defaulterNetworkConsent`
            → provisioning; plumbed web signup checkbox → BFF → `backendRegister`). Reciprocity enforced:
            `warn` throws 403 `CONSENT_REQUIRED` if the reporter hasn't consented; `isPhoneFlagged` returns
            false for a non-consented caller. Settings toggle: `GET`/`PUT /api/v1/defaulter/consent`
            (identity-level) + web "Defaulter network" section. Consent copy written in signup + Settings
            (plain-language, not formal ToS). Tests: consent cases in `DefaulterServiceTest`, `setConsent`
            validation. _Note: user opted to ship without a legal review (dev phase) — real ToS/CICRA/DPDP
            sign-off still recommended before a public launch._

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
- [x] **Populate `gateway_customer_id`.** ✅ Now set for GST-registered shops — the GSTIN
      flow pre-creates a Razorpay customer and stores its id on the subscription. Non-GST
      hosted checkouts still leave it null (gateway creates the customer at payment); revisit
      only if we need per-customer lookups / the Razorpay customer portal for those.

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
