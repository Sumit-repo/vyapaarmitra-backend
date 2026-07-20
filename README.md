# VyapaarMitra Backend

Shared API for the VyapaarMitra merchant platform: a smart udhar/ledger companion for
Indian small shops. This service is the **single backend** consumed by both the Expo
mobile app (staff daily operations) and the Next.js web dashboard (owner analytics/admin)
— see the `vyapaarmitra-client` repo.

## Stack & principles

- **Spring Boot 4 / Java 21**, Maven.
- **Supabase Postgres as plain storage only** — no Supabase auth, no RLS. All auth,
  branch/shop access control, ledger logic, trust scoring, reminders, and template
  rendering live in this service.
- **Stateless & containerized** — safe to scale to N instances. No local state;
  sessions are JWTs; migrations run via Flyway on startup.
- **Cloud-portable** — no cloud-provider SDK dependencies. Plain JDBC + env vars
  means the same container runs on Render, Cloud Run, AWS App Runner, or Azure
  Container Apps unchanged. Currently deployed on **Render**.
- **Single-shop today, multi-branch ready** — every table is scoped by
  `business_id` + `branch_id`; the pilot just has one of each.

## Domain model

`businesses → branches → customers → ledger_entries` and the mirror
`suppliers → supplier_ledger_entries`, with `users` (+ `user_branch_access`),
`message_templates`, `reminder_logs`, and `customer_reminder_settings`. Roles:
`OWNER` (all branches), `BRANCH_MANAGER`, `STAFF` (assigned branches only).
Branch-level authorization is centralized in `BranchAccessService` — every
branch-scoped call goes through it.

Customer state (`current_balance`, `oldest_due_date`, `trust_score`) is denormalized
and recomputed on every ledger entry. Payments allocate FIFO against credits
(`LedgerMath`); the trust score formula is documented in `TrustScoreService`.
**Suppliers** reuse the same ledger math and access control but have **no trust
score**; `current_balance > 0` means the business owes the supplier (payable).
Customers and suppliers both carry an optional `address` and expose
`lastActivityAt` (= `updated_at`) for the directory tables.

## Auth

Custom JWT (HS256). `POST /api/v1/auth/login` returns a short-lived access token and
a long-lived refresh token. Refresh tokens carry a `ver` claim checked against
`users.token_version`, so deactivating a user or changing a password revokes sessions
statelessly. Note: access tokens stay valid until expiry (default 30 min) after
revocation — keep the access TTL short.

## API surface (v1)

```
POST  /api/v1/auth/login | /auth/refresh
GET   /api/v1/me
GET/POST/PATCH /api/v1/branches            (create/update: OWNER)
GET/POST/PATCH /api/v1/users               (OWNER only)
GET/POST/PATCH /api/v1/customers           (?branchId&q&page&size)
GET   /api/v1/customers/{id}
GET   /api/v1/customers/{id}/ledger
POST  /api/v1/entries                      (CREDIT/PAYMENT; returns updated customer)
GET/POST/PATCH /api/v1/suppliers           (?branchId&q&page&size)
GET   /api/v1/suppliers/{id}
GET   /api/v1/suppliers/{id}/ledger
POST  /api/v1/supplier-entries             (CREDIT/PAYMENT; returns updated supplier)
GET   /api/v1/recovery/today               (?branchId — "who to contact today")
GET/POST/PATCH /api/v1/templates           (create/update: OWNER/BRANCH_MANAGER)
POST  /api/v1/templates/{id}/render        ({{customer_name}}, {{amount_due}}, …)
GET/POST /api/v1/reminders                 (outcome log: sent/promised/paid)
GET   /api/v1/dashboard/summary            (?branchId or consolidated)
GET   /api/v1/dashboard/collections        (?branchId&days — daily collected/given trend)
GET   /healthz                             (liveness; /actuator/health = readiness)
```

Omitting `branchId` on scoped endpoints gives the consolidated view of all branches
the caller can access. List endpoints are paginated and capped at 100 per page.

## Local development

```bash
cp .env.example .env   # fill in Supabase credentials + JWT secret
./mvnw test            # unit tests (no DB needed)
./mvnw spring-boot:run # needs the env vars from .env exported
```

On first boot with an empty database, `BootstrapRunner` seeds the business, main
branch, owner account, and starter Hinglish reminder templates from `BOOTSTRAP_*`
env vars.

## Deploy to Render

`render.yaml` is a Render Blueprint: in the Render dashboard choose
**New → Blueprint**, point it at this repo, and fill in the secret env vars when
prompted (`DATABASE_*`, `CORS_ALLOWED_ORIGINS`, `BOOTSTRAP_*`). Render builds the
`Dockerfile`, injects `PORT`, and uses `/healthz` as the health check.

Notes:

- **Free tier spins down after ~15 min idle** — first request then takes 30–60 s
  (JVM cold start + Flyway). Fine for testing; use the Starter plan for the shop
  pilot so the counter flow is never waiting on a cold start.
- Region `singapore` is the closest to India.
- Keep instance count × `DB_POOL_SIZE` (default 5) under Supabase's connection
  limit; use the Supabase connection pooler (port 6543) if you scale out.
- The container is provider-agnostic — moving to Cloud Run/AWS later is just
  pointing a different platform at the same Dockerfile and env vars.
