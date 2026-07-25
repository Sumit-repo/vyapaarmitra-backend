# Security

Security posture of the VyapaarMitra backend (Spring Boot on Cloud Run/Render).

## Reporting a vulnerability

Email **1020sumit@gmail.com** with details and reproduction steps. Please do not
open a public issue for security reports.

## Authentication & authorization

- **JWT** access + refresh tokens (jjwt). Access tokens are short-lived
  (`JWT_ACCESS_TTL_MINUTES`, default 30); refresh tokens rotate on use.
- **Token revocation** via a per-user `tokenVersion` — bumping it invalidates all
  outstanding refresh tokens ("log out everywhere").
- **Passwords** hashed with BCrypt (`BCryptPasswordEncoder`). Plaintext is never
  stored or logged.
- **Role model** OWNER / MANAGER / STAFF, enforced with `@EnableMethodSecurity`
  and `@PreAuthorize` on privileged endpoints (e.g. template management).
- **Branch scoping** — every data query is scoped to the caller's accessible
  branches; cross-tenant access is not possible via the API surface.
- Stateless sessions (`SessionCreationPolicy.STATELESS`); no server session state.

## Transport & headers

- HTTPS terminated at the platform edge. **HSTS** (`includeSubDomains`, 1 year)
  and **Referrer-Policy: no-referrer** set in `SecurityConfig`; Spring defaults add
  `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`.
- **CORS** is allow-listed from `CORS_ALLOWED_ORIGINS` with an explicit method and
  header set — no wildcard origins.

## Abuse protection

- **Rate limiting** on `/api/v1/auth/**` (login/refresh/register), keyed by client
  IP (`X-Forwarded-For`), configurable via `RATE_LIMIT_AUTH_PER_MINUTE`
  (default 20/min). Returns `429` with `Retry-After`. Guards against brute force
  and credential stuffing.

## Input handling & data access

- All request bodies are validated with Jakarta Bean Validation; failures return a
  structured `VALIDATION_ERROR` with field details, never a stack trace.
- Data access is JPA/Hibernate with bound parameters — no string-concatenated SQL.
- `GlobalExceptionHandler` maps all errors to a stable `{ error: { code, message } }`
  envelope; internal exceptions do not leak stack traces or SQL to clients.

## Secrets & configuration

- No secrets in source. `JWT_SECRET`, `DATABASE_*`, `CORS_ALLOWED_ORIGINS`, and
  `BOOTSTRAP_*` are supplied via environment variables.
- `.env` and `.env.*` are gitignored (`.env.example` documents the shape).
- Flyway migrations are versioned and immutable; schema is `ddl-auto: validate`.

## Operational

- Actuator exposes **only** `/actuator/health` (with readiness/liveness probes);
  no `env`, `beans`, `heapdump`, or `metrics` are exposed publicly.
- Graceful shutdown enabled; per-instance DB pool bounded to respect Postgres limits.
- CI (`.github/workflows/ci.yml`) runs the full test suite on every PR.

## Known items

- Dependency advisories surfaced by scanners are reviewed per Spring Boot release;
  the project tracks the latest Boot 4.x line.
