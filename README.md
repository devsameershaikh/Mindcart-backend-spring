# MindCart Backend — Spring Boot port

A Spring Boot (Java 17) port of the original Express/Prisma MindCart backend.
Same data model, same routes (paths/verbs/response shapes), same business
logic — rebuilt on Spring Data JPA + PostgreSQL, with global error handling,
a hardened Google Sign-In flow, and a Socket.IO-compatible realtime layer so
the existing mobile client doesn't need to change.

## 1. Build

```bash
mvn clean package
```

> **Note:** this project was written and reviewed carefully but has **not
> been compiled in the environment that generated it** (no Maven Central
> access there). Run `mvn clean compile` first and fix any small API-surface
> mismatches version pinning might introduce — the two spots most likely to
> need a look are:
> - `SocketIOConfig` (`server.addAuthTokenListener(...)`) — netty-socketio's
>   auth-token API has evolved across versions; confirm against whatever
>   version resolves.
> - `JwtService` / JJWT builder chain — confirm against `jjwt` 0.12.x docs
>   if you bump the version.

## 2. Configure

Copy `.env.example` to `.env` (or set these directly in your platform's
environment/secrets manager) and fill in:

| Variable | Required | Notes |
|---|---|---|
| `DATABASE_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://host:5432/db`. Use Neon's **pooled** connection string. |
| `DB_USERNAME` / `DB_PASSWORD` | yes | |
| `JWT_SECRET` | yes | ≥32 random bytes. Generate with `openssl rand -base64 48`. App refuses to start without a strong secret. |
| `GOOGLE_CLIENT_IDS` | yes | Comma-separated OAuth client ids, one per platform (iOS/Android/Web) — same as the original `GOOGLE_CLIENT_IDS`. |
| `CORS_ALLOWED_ORIGINS` | recommended | Comma-separated browser origins allowed to call the API. Leave empty to block all browser origins (native/mobile apps are unaffected — CORS is a browser-only mechanism). |
| `PORT` | no | HTTP port, default 4000 (same default as before). |
| `SOCKETIO_PORT` | no | Socket.IO port, default 4001 (separate port; put both behind the same reverse proxy/load balancer in production). |
| `SQL_INIT_MODE` | no | `always` (default) runs `schema.sql` on every boot — safe, it's all `IF NOT EXISTS`. Switch to `never` once you're managing schema changes yourself. |

## 3. Run

```bash
mvn spring-boot:run
# or
java -jar target/mindcart-backend.jar
```

- `GET /health` — unauthenticated liveness check (same as before).
- Everything else requires `Authorization: Bearer <token>` except `POST /auth/google`.
- The mobile client's socket.io connection should point at `SOCKETIO_PORT`
  instead of the HTTP port; the handshake still uses `auth: { token }` with
  the same session JWT.

## 4. What changed vs. the Express version, and why

### Global error handling ("server should not stop")
Node/Express doesn't isolate failures the same way a JVM servlet container
does — an exception thrown in an `async` route handler that isn't wrapped in
try/catch can escape as an unhandled rejection and, depending on
process-level handlers, take the whole process down. In this port:
- Every request runs inside Spring's `DispatcherServlet`, which already
  isolates exceptions per-request; one bad request can't affect another or
  crash the server.
- `GlobalExceptionHandler` is the single place that turns *any* exception —
  expected (`ApiException` subtypes) or not — into the same clean JSON shape
  (`{ error, status, path, timestamp }`), and makes sure nothing internal
  (stack traces, SQL, class names) ever leaks to the client.
- `Thread.setDefaultUncaughtExceptionHandler` in `BackendApplication` catches
  anything that somehow still escapes on a non-request thread (e.g. a socket
  event handler) and logs it instead of silently killing that thread.

### Google Sign-In hardening
- **`email_verified` is now checked.** The original code never checked this
  — an unverified Google account's email could still be used to sign in or
  silently claim invites sent to that address. This port refuses the token
  if the email isn't verified.
- Audience + issuer are validated strictly by Google's own
  `GoogleIdTokenVerifier`, configured with your exact `GOOGLE_CLIENT_IDS`
  list (same multi-platform-id support as before).
- The app **refuses to start** if `GOOGLE_CLIENT_IDS` or `JWT_SECRET` is
  missing/weak, rather than silently running in a misconfigured, insecure
  state.
- `POST /auth/google` is rate-limited per client IP (in-memory token bucket)
  to blunt brute-force/credential-stuffing against the verification
  endpoint.

### Other security hardening
- **CORS**: the original used `cors()` with no options, which reflects and
  allows *any* origin. Every route here carries a bearer token and mutates
  private data, so this port requires an explicit `CORS_ALLOWED_ORIGINS`
  allowlist instead.
- **Security headers**: HSTS, `X-Content-Type-Options`, `X-Frame-Options:
  DENY`, a restrictive CSP, and `Referrer-Policy` are added — the Express
  app had none of this (no `helmet` or equivalent).
- **JWT secret strength check**: HS256 requires ≥256 bits; a short secret is
  brute-forceable and the original code never validated this.
- **Invite creation is rate-limited per user** — `POST /sharing/invites`
  reveals whether an email has an account (404 vs success), so it's
  rate-limited to make user enumeration slower.
- **Socket room join hardened**: the original `list:join` handler trusted
  any `listId` the client sent, letting a connected socket subscribe to live
  updates for a list it had no membership in. This port checks membership
  before joining the room.
- All the original app's own IDOR protections are preserved as-is: 404
  (never 403) when a caller has no access to a list/invite, so probing IDs
  can't distinguish "not yours" from "doesn't exist"; an owner can't be
  demoted or removed from their own list; invite accept/decline verifies the
  caller is the actual intended recipient.

### Data & business logic
Ported line-for-line, including the less obvious bits:
- Offline-first idempotency: client-supplied `id` on list/item creation is
  honored, and a duplicate-key retry is treated as success, not an error
  (see `ListService.createList` / `ItemService.createItem`).
- "Family member" (`allLists` invite) auto-grant to lists created *after*
  the invite was accepted (`ListService.grantToStandingFamilyMembers`).
- Default "Groceries" list seeded on first sign-up only, in the same
  transaction as its starter items.
- Same PATCH semantics: only whitelisted fields present in the request body
  are applied to an item; an empty-string price means "don't touch the
  existing price."
- Pending invites (sent before someone had an account) are auto-attached to
  `recipientId` on first login.

### What is *not* wired up (same as the original)
Sending the actual invite email is still a stub — plug in a transactional
email provider (Resend/SendGrid/etc.) in `SharingService.createInvite`
using the invite's token to build the accept link, same as the original
Node comment noted.

## 5. Architecture

```
entity/        JPA entities (User, ShoppingList, ListMember, Item, Invite, Role, InviteStatus)
repository/    Spring Data JPA repositories
dto/           Request/response shapes (kept field-compatible with the old API)
security/      JwtService, GoogleTokenVerifier, JwtAuthFilter, RateLimiter
config/        SecurityConfig, CorsConfig, SocketIOConfig
service/       Business logic (AuthService, ListService, ItemService, SharingService, PermissionService, RealtimeService)
controller/    REST endpoints (AuthController, ListController, SharingController, HealthController)
socket/        Socket.IO event handlers (room join/leave)
exception/     ApiException hierarchy + GlobalExceptionHandler
```

`schema.sql` (in `src/main/resources`) is the source of truth for the DB
schema, ported from `prisma/schema.prisma` + the original migration; JPA is
set to `ddl-auto: validate` so Hibernate checks against it rather than
generating DDL itself.

## 6. Production notes

- Put a reverse proxy (nginx/ALB/etc.) in front of both `PORT` and
  `SOCKETIO_PORT`, terminating TLS there — same as you'd do for the plain
  `http.Server` in the original app.
- `RateLimiter` is in-memory and per-instance. Fine for a single node; for a
  multi-instance deployment, swap it for Bucket4j's distributed mode backed
  by Redis, or move rate limiting to your API gateway/proxy.
- `SQL_INIT_MODE=always` is convenient for first deploys; once you have a
  real migration workflow (Flyway/Liquibase), switch it to `never` and
  manage schema changes there instead.
