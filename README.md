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
- **Stateless & containerized** — safe to scale to N Cloud Run instances. No local
  state; sessions are JWTs; migrations run via Flyway on startup.
- **Cloud-portable** — no GCP SDK dependencies. Plain JDBC + env vars means the same
  container runs on AWS App Runner / Azure Container Apps unchanged.
- **Single-shop today, multi-branch ready** — every table is scoped by
  `business_id` + `branch_id`; the pilot just has one of each.

## Domain model

`businesses → branches → customers → ledger_entries` with `users` (+
`user_branch_access`), `message_templates`, and `reminder_logs`. Roles: `OWNER`
(all branches), `BRANCH_MANAGER`, `STAFF` (assigned branches only). Branch-level
authorization is centralized in `BranchAccessService` — every branch-scoped call
goes through it.

Customer state (`current_balance`, `oldest_due_date`, `trust_score`) is denormalized
and recomputed on every ledger entry. Payments allocate FIFO against credits
(`LedgerMath`); the trust score formula is documented in `TrustScoreService`.

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
GET   /api/v1/customers/{id}/ledger
POST  /api/v1/entries                      (CREDIT/PAYMENT; returns updated customer)
GET   /api/v1/recovery/today               (?branchId — "who to contact today")
GET/POST/PATCH /api/v1/templates           (create/update: OWNER/BRANCH_MANAGER)
POST  /api/v1/templates/{id}/render        ({{customer_name}}, {{amount_due}}, …)
GET/POST /api/v1/reminders                 (outcome log: sent/promised/paid)
GET   /api/v1/dashboard/summary            (?branchId or consolidated)
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

## Deploy to Cloud Run

```bash
gcloud run deploy vyapaarmitra-api \
  --source . \
  --region asia-south1 \
  --allow-unauthenticated \
  --set-env-vars "DATABASE_URL=...,DATABASE_USERNAME=...,CORS_ALLOWED_ORIGINS=https://your-dashboard.vercel.app,BOOTSTRAP_OWNER_EMAIL=...,BOOTSTRAP_OWNER_NAME=...,BOOTSTRAP_BUSINESS_NAME=..." \
  --set-secrets "DATABASE_PASSWORD=vm-db-password:latest,JWT_SECRET=vm-jwt-secret:latest,BOOTSTRAP_OWNER_PASSWORD=vm-owner-password:latest"
```

Store secrets in Secret Manager (`gcloud secrets create ...`), never in plain env vars
or the repo. Keep `--max-instances` low (e.g. 3) so the summed Hikari pools
(`DB_POOL_SIZE`, default 5) stay under Supabase's connection limit; use the Supabase
connection pooler (port 6543) if you raise instance counts.

Health checks: point Cloud Run's startup/liveness probes at `/healthz`.
