# Authentication & Session Model

This document describes how the backend authenticates users and protects endpoints.
It covers the intended security model; consult the referenced classes for implementation
detail.

## Overview

There are three complementary layers:

1. **Prod perimeter (`InternalSecretFilter`)** -- in the `prod` profile, requests must carry
   a valid `X-Internal-Secret` header. The Next.js BFF proxy holds this shared secret and
   attaches it to every call it forwards; browsers never call the API directly.
2. **User sessions (`SessionService` + `SessionAuthenticationFilter`)** -- authenticated
   users carry an opaque, DB-backed session in an HttpOnly cookie.
3. **Spring Security (`SecurityConfig`)** -- declares which routes are public vs.
   authenticated and wires the session filter into the chain.

## Prod Perimeter: `InternalSecretFilter`

- Active in the `prod` profile only (`@Profile("prod")`), ordered ahead of the security chain.
- Every request (except the actuator health probes) must present `X-Internal-Secret`
  matching the configured secret. Comparison is constant-time (`MessageDigest.isEqual`) to
  avoid leaking the secret via timing.
- Supports zero-downtime rotation: both a current (`internal.api.secret`) and a next
  (`internal.api.secret.next`) value are accepted while a rotation is in flight.
- This is the perimeter for the `/api/read`, `/api/admin`, and `/api/public` families in
  production. Because those calls arrive from the BFF bearing the shared secret rather than a
  session principal, they are intentionally **not** switched to Spring `authenticated()` --
  authorization for them is the BFF + secret perimeter.

## Sessions: `SessionService`

- A 256-bit CSPRNG token is generated per login and returned **raw** only in the
  `ezac_session` cookie. Only its **SHA-256 hash** is persisted in `user_session`, so a
  database leak never yields a usable cookie.
- Cookie attributes: `HttpOnly`, `Secure` (forced off only in the dev profile so it works over
  plain `http://localhost`), `SameSite=Strict`, `Path=/`, with `Max-Age` matching the TTL.
- **Two bounds on lifetime:**
  - *Idle / sliding*: a session expires after `app.auth.session.ttl-days` (60) of inactivity.
    On use, if the last-seen timestamp is older than the refresh threshold
    (`refresh-threshold-hours`, 24), the window slides forward.
  - *Absolute*: the slide can never push expiry past `createdAt + max-lifetime-days` (90), so
    even a continuously-used session eventually lapses (per OWASP guidance).
- Sessions can be revoked instantly (logout marks the row revoked and clears the cookie).
- `SameSite=Strict` cookies plus the BFF write-method Origin allow-list provide CSRF defense,
  so the stateless API carries no separate CSRF token.

## Session resolution: `SessionAuthenticationFilter`

- Runs once per request, reads the `ezac_session` cookie, and asks `SessionService.resolve`
  to map the raw token to a principal.
- On success it populates the Spring `SecurityContext` with an `AuthPrincipal`
  (`ROLE_USER`). On any miss (unknown / revoked / expired) the request simply continues
  unauthenticated and is handled by the security matrix.

## Route matrix: `SecurityConfig`

- Stateless session policy; form login, HTTP Basic, and Spring's logout are all disabled
  (the API owns its own login/logout).
- Public (permit) routes: `POST /api/auth/login`, `/api/auth/webauthn/login/**`,
  `GET /api/auth/invite/*`, `POST /api/auth/invite/*/accept`.
- Authenticated routes: `/api/auth/me`, `/api/auth/logout`,
  `/api/auth/webauthn/register/**`.
- Unauthenticated requests to an authenticated route receive `401`.

## Password login: `AuthController`

- `POST /api/auth/login` is the break-glass path. Passwords are verified with a delegating
  BCrypt encoder against the hash stored in `users`.
- **User-enumeration hardening**: the unknown-email branch performs a dummy BCrypt comparison
  and discards the result, so the "no such user" and "wrong password" branches take the same
  time -- no timing oracle.
- **Rate limiting**: an IP+email limiter (`AuthLoginLimiter`,
  `app.auth.login.max-attempts` / `window-minutes`) returns `429` when tripped and resets on
  a successful login.
- A successful login mints a session (`mfaSatisfied = false` for password logins).

## Passkeys (WebAuthn): `WebAuthnController` / `WebAuthnService`

Passwordless login and step-up using W3C WebAuthn (via webauthn4j). Four ceremony endpoints:

- `POST /api/auth/webauthn/register/start` and `/register/finish` -- **require an
  authenticated principal**; mint attestation options and persist the new credential to
  `webauthn_credential`.
- `POST /api/auth/webauthn/login/start` and `/login/finish` -- public; mint assertion options
  and verify the signed assertion. A short-lived (5 min) HttpOnly `ezac_webauthn_attempt`
  cookie carries the per-attempt id between the two calls.
- Login ceremonies share the same IP+email rate-limiter as password login.
- Relying-party config (`WEBAUTHN_RP_ID`, `WEBAUTHN_RP_NAME`, `WEBAUTHN_ALLOWED_ORIGINS`)
  binds credentials to the expected origin. A user-verified passkey login records
  `mfaSatisfied = true` on the session.

## Invites: `InviteController` / `UserInviteService`

- `GET /api/auth/invite/{token}` -- public, read-only preview (does not consume the token);
  returns the invitee email and any pre-filled name, or `404` for an unknown/expired/used token.
- `POST /api/auth/invite/{token}/accept` -- public; atomically redeems the token, sets the
  BCrypt password and display name, activates the account (`UserStatus.ACTIVE`), and
  auto-logs the user in by minting a session. Returns `410 Gone` if the token was already
  used or expired.
- Invite tokens live in `user_invite`; admins issue them via the admin user endpoints.

## Related tables

`users`, `user_session`, `webauthn_credential`, `user_invite`, `user_collection`
(gallery membership + role), `gallery_access`. See [database.md](database.md).
