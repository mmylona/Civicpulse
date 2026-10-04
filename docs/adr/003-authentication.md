# ADR 003: JWT access tokens and rotating refresh tokens

Status: accepted
Date: 2026-10-04

## Context

The Angular app and the API are served from the same origin. Citizens should
stay logged in for days, staff roles can change at any time, and it should be
possible to run more than one backend instance later.

## Decision

- Access token: JWT (HS256), 15 minutes, with user id, email, name and roles.
  Validated with Spring Security's OAuth2 resource server.
- The frontend keeps the access token in memory only.
- Refresh token: random 32 bytes in an `HttpOnly; Secure; SameSite=Strict`
  cookie with path `/api/v1/auth`. Only the SHA-256 hash is stored.
- Every refresh rotates the token. Tokens from the same login share a family id.
  If an already rotated token is used again, the whole family is revoked and an
  audit entry is written. Within 10 seconds of rotation this is treated as two
  tabs refreshing at once and the client can retry.
- Changing roles or disabling a user revokes their refresh tokens.
- Passwords: BCrypt cost 12 via `DelegatingPasswordEncoder`, 10 characters to
  72 bytes.
- 5 failed logins lock the account for 15 minutes. Login is limited to 10
  requests per minute per IP. Unknown email, wrong password and locked account
  all return `INVALID_CREDENTIALS`.

## Alternatives

- Server-side sessions: easy to revoke, but need a shared session store with
  more than one instance, and CSRF protection.
- Long-lived JWT only: can't be revoked.
- Tokens in localStorage: readable by any script on the page.
- Keycloak or another identity provider: better for a real deployment, but a
  lot of extra setup for this project. Because the API is a plain resource
  server, switching later mostly touches the identity module.

## Consequences

- A disabled user can keep using the API until the access token expires (max
  15 minutes).
- `CIVICPULSE_JWT_SECRET` (min 32 bytes) must be set; the app won't start
  without it.
- Rate limits are in memory, so they are per instance.
