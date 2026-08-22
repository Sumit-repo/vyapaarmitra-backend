# Backend Hosting Migration — Render → GCP Cloud Run (Mumbai)

**Status:** Proposed · **Author:** Sumit · **Date:** 2026-08-22
**Scope:** Backend (Spring Boot) only. **Database stays on Supabase.**

---

## 1. Why

Move the Spring Boot API from **Render (Singapore, free tier)** to **GCP Cloud Run
(asia-south1 / Mumbai)**. Primary driver: **lower latency for Indian shop owners**.
Secondary: escape Render free-tier cold spin-down with a near-zero-cost keep-warm.

Non-goals (explicitly out of scope):

- **Postgres stays on Supabase.** No Cloud SQL. (Cloud SQL has no free tier and would
  add ₹1,000–2,500/mo for zero functional gain here.)
- Web (Next.js) stays on Vercel. Only its `BACKEND_API_URL` changes.
- Media stays on Cloudinary.

---

## 2. Current state (verified in repo)

| Piece | Where | Notes |
|---|---|---|
| Backend | Render, `render.yaml`, Singapore, `plan: free` | Spins down ~15 min idle |
| Container | `Dockerfile` (multi-stage, Temurin 21, reads `$PORT`) | Already Cloud-Run-ready — comment on line 15 even says so |
| Liveness | `GET /actuator/health/liveness` → `{"status":"UP"}` | No DB touch — **keep-warm target**. ⚠️ `/healthz` is intercepted by Cloud Run's edge (returns Google 404, never reaches the container) — do NOT use it for pings or probes |
| Readiness | `GET /actuator/health/readiness` (or `/actuator/health`) | DB-backed; confirms Supabase reachable |
| DB | Supabase Postgres (`DATABASE_URL` jdbc) | **Region unknown — see §7 open item** |
| Web → backend | `lib/server/backend.ts` reads `BACKEND_API_URL` (server-only, BFF) | Single env var to swap |
| Secrets today | Render dashboard (`sync: false` vars in `render.yaml`) | Re-enter in GCP |

**Env vars to carry over** (from `.env.example` / `render.yaml`): `DATABASE_URL`,
`DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `JWT_ACCESS_TTL_MINUTES`,
`JWT_REFRESH_TTL_DAYS`, `CORS_ALLOWED_ORIGINS`, `GOOGLE_CLIENT_IDS`, `RESEND_API_KEY`,
`MAIL_FROM`, `OTP_TTL_MINUTES`, `RAZORPAY_*`, `NEWRELIC_*`, `APP_TIMEZONE=Asia/Kolkata`,
`DB_POOL_SIZE=5`, and the one-time `BOOTSTRAP_*` vars.

---

## 3. Cost analysis (Mumbai, ~5,000 req/month)

DB cost = **₹0** (unchanged, Supabase free). GCP cost = **Cloud Run only**. At this
traffic, request count is negligible — cost is decided purely by warm-vs-cold.

| Approach | Behaviour | Est. cost/mo |
|---|---|---|
| `min-instances=0`, no keep-warm | 5–15s Spring cold start per idle visitor | **~₹0** |
| `min-instances=0` **+ keep-warm cron** (chosen) | Mostly warm, idle not billed | **~₹0** |
| `min-instances=1`, 0.5 vCPU / 512Mi | Guaranteed warm, CPU billed 24/7 | ~₹1,000–1,600 |
| `min-instances=1`, 1 vCPU / 512Mi | Guaranteed warm | ~₹1,800–2,800 |

**Chosen: `min-instances=0` + keep-warm cron ≈ ₹0/month.**

Why it's free: on Cloud Run's **default request-based billing**, CPU is billed only
*while a request is processing*. An idle-but-alive instance costs nothing, so a cheap
ping every few minutes keeps the instance loaded without idle charges.

**Free-tier headroom** (per billing account/month): 2M requests, 180k vCPU-s, 360k
GB-s. A `/healthz` ping every 5 min = ~8,640 pings/mo + ~5,000 real = well inside.
New GCP accounts also get **$300 / 90-day credit** — covers even `min-instances=1`
for the first ~3 months while validating the latency win.

⚠️ **Cost trap:** do **not** switch the service to "CPU always allocated"
(instance-based billing). That bills idle time and makes the keep-warm pointless.
Keep the default (CPU during requests only).

---

## 4. Migration plan (phased, each phase reversible)

### Phase 0 — Prep (no traffic impact) — DONE 2026-08-22 (console/Cloud Shell)
0. Install Google Cloud SDK (gcloud) — not present on the dev machine.
1. `gcloud auth login`; create GCP project; set as default.
2. ~~Confirm Supabase region~~ — **DONE: Mumbai (ap-south-1)**, straight lift.
3. Link billing account + activate $300 / 90-day free-trial credit.
4. Enable APIs: Cloud Run, Artifact Registry, Cloud Scheduler, Secret Manager, Cloud Build.
5. Set default region `asia-south1`; create a ₹500 budget alert.

### Phase 1 — Deploy alongside Render (parallel, no cutover) — DONE 2026-08-22
Deployed to `https://vyapaarmitra-backend-894028376218.asia-south1.run.app`.
Smoke test passed: `/actuator/health/{liveness,readiness}` → 200 UP (Supabase reached,
Flyway applied). Request-based billing, min=0/max=4, 1 vCPU / 512Mi, CPU boost + gen1.
4. Build & push image to Artifact Registry (`asia-south1`), or let
   `gcloud run deploy --source .` build from the existing Dockerfile.
5. Deploy to Cloud Run `asia-south1`: `min-instances=0`, `max-instances` small (e.g. 4),
   0.5 vCPU / 512Mi, concurrency default, `--allow-unauthenticated`,
   health check → `/healthz`.
6. Set all env vars/secrets (§2). Store secrets in **Secret Manager**, not plain env.
7. Smoke test the Cloud Run URL directly: `/healthz`, login, one read, one write.
   Confirm it reads the **same Supabase DB** (no data migration — same connection string).

### Phase 2 — Keep-warm
8. **cron-job.org** (chosen — external, zero GCP coupling): GET
   `https://<cloud-run-url>/actuator/health/liveness` every **5 min**, no auth,
   expect 200. NOT `/healthz` — Cloud Run's edge swallows that path (verified
   2026-08-22). (Cloud Scheduler is the in-GCP alternative if ever wanted.)
   Alt: external pinger (cron-job.org / UptimeRobot / GitHub Actions cron) for zero
   GCP coupling.

### Phase 3 — Cutover
9. Add the Cloud Run origin to `CORS_ALLOWED_ORIGINS` if needed (web origin unchanged,
   so likely no change — the browser calls Vercel, not the backend).
10. Point web at the new backend: set `BACKEND_API_URL` = Cloud Run URL in **Vercel**
    env, redeploy web. Update mobile's backend URL config likewise if it targets prod.
11. Verify end-to-end on prod web: auth, ledger read/write, PDF, Razorpay webhook path.

### Phase 4 — Decommission
12. Watch Cloud Run + New Relic for 3–7 days (errors, p95 latency, cold-start count).
13. Suspend the Render service (don't delete immediately — keep as instant rollback).
14. After a clean week, delete Render service and remove `render.yaml` from repo (or
    keep it documented as the rollback target).

---

## 5. Risk & impact analysis

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | **Supabase in a far region** (e.g. Singapore/US) → backend↔DB latency *worsens* after Mumbai move, net latency win shrinks | Med | High | **Confirm region first (§7).** If not ap-south-1, either move Supabase project to Mumbai or reconsider co-locating |
| R2 | Cold start on evicted instance (keep-warm is best-effort, not guaranteed) | Med | Med | 5-min ping; accept rare 5–15s hit at pilot scale; upgrade to `min-instances=1` when paying users arrive |
| R3 | Concurrency cold start (2 simultaneous users → 2nd instance boots) | Low | Low | Rare at 167 req/day; `max-instances` caps blast radius |
| R4 | Accidental "CPU always allocated" → surprise bill | Low | Med | Keep default request-based billing; set a **budget alert** (₹500) |
| R5 | Supabase free-tier connection limits hit by Cloud Run scaling out (each instance opens a pool of `DB_POOL_SIZE=5`) | Med | Med | Keep `DB_POOL_SIZE` small; cap `max-instances`; use Supabase **transaction pooler** (pgBouncer, port 6543) not direct 5432 |
| R6 | Secrets mis-entered on Cloud Run (JWT_SECRET/DB creds) → auth breaks or data-loss lockout | Med | High | Use Secret Manager; smoke test in Phase 1 before cutover; JWT_SECRET must match to keep existing refresh tokens valid — **reuse the current secret**, don't regenerate |
| R7 | CORS / cookie breakage after cutover | Low | Med | Web calls Vercel (BFF), not backend directly, so browser CORS unaffected; still verify `CORS_ALLOWED_ORIGINS` |
| R8 | Razorpay webhook still pointing at Render URL | Med | High | Update webhook target to Cloud Run URL in Razorpay dashboard during Phase 3; test one live event |
| R9 | Mobile app hardcoded to old backend URL | Med | High | Update mobile env/config; note shipped app binaries can't be changed — keep Render alias or use a stable custom domain (see R10) |
| R10 | Cloud Run auto-URL changes / not memorable | Low | Med | Map a **custom domain** (e.g. `api.vyapaarmitra.app`) so both web & mobile point at a stable host and future host swaps need no client change |
| R11 | Vendor lock-in / ops overhead vs Render's simplicity | Low | Low | Dockerfile is portable; Cloud Run is standard containers — low lock-in |
| R12 | Google OAuth: authorized origins/redirects don't include new backend | Low | Med | Backend verifies ID tokens (no redirect), so likely unaffected; confirm `GOOGLE_CLIENT_IDS` carried over |

### Impact summary
- **Users:** during pilot, occasional cold start (R2) until `min-instances=1`. Latency
  should improve for Indian users once backend (and ideally DB) sit in Mumbai.
- **Cost:** ₹0 target; ceiling ~₹1,600/mo if forced to always-warm. Budget alert set.
- **Data:** none — same Supabase DB, same connection string. No migration, no downtime
  from a DB standpoint.
- **Downtime:** ~zero. Parallel deploy + env-var cutover; instant rollback via Render.
- **Code:** effectively no app-code change. Only infra + one env var per client (web,
  mobile). `render.yaml` retired.

---

## 6. Rollback

Single lever: revert `BACKEND_API_URL` (web/Vercel + mobile config) to the Render URL
and redeploy web. Render service kept **suspended, not deleted**, through Phase 4 for
instant restore. Update Razorpay webhook back if it was changed.

---

## 7. Open items / decisions needed

1. ~~**Where is the Supabase project hosted?**~~ **RESOLVED 2026-08-22: Supabase is in
   Mumbai (ap-south-1).** R1 no longer applies — backend and DB will be co-located in
   Mumbai, so the latency win holds. Straight lift, no Supabase change.
2. ~~**Custom domain for the API?**~~ **DECIDED 2026-08-22: no custom domain.** Web and
   mobile point at the raw `...run.app` URL. Accepted trade-off: Cloud Run is now
   effectively permanent for shipped mobile binaries unless re-released (R10 accepted).
3. **Supabase connection mode:** switch backend `DATABASE_URL` to the transaction
   pooler (port 6543) before Cloud Run scales out (R5)?
4. ~~**Keep-warm host:**~~ **DECIDED 2026-08-22: cron-job.org** (external pinger),
   `/actuator/health/liveness` every 5 min.
5. **Trigger for `min-instances=1`:** define the point (first paying shop / N daily
   active) at which we stop tolerating cold starts and pay for guaranteed warm.
```
