# Security

## Reporting a vulnerability

Please don't open a public issue. Use GitHub's private vulnerability reporting
(Security tab, "Report a vulnerability").

## Authentication

Details in [ADR 003](docs/adr/003-authentication.md).

- JWT access tokens valid for 15 minutes, kept in memory in the frontend.
- Refresh token in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie for
  `/api/v1/auth`, stored as a SHA-256 hash and rotated on every refresh.
- BCrypt (cost 12). Passwords must be 10 characters to 72 bytes.
- Account locked for 15 minutes after 5 failed logins. Login is limited to 10
  attempts per minute per IP, registration to 10 per hour.

## Roles

| Role | Access |
| --- | --- |
| `CITIZEN` | default for new accounts |
| `MODERATOR` | reviews and moderates reports |
| `CITY_OPERATOR` | works on reports for their team |
| `ADMIN` | users, roles, teams, audit log |

Checked on the backend in `SecurityConfig` (`/api/**` needs a token,
`/api/v1/admin/**` needs `ADMIN`) and with `@PreAuthorize` on services. The
frontend guards only hide pages. An admin can't remove their own admin role or
disable their own account.

## Audit log

Registrations, logins, failed logins, lockouts, role/team/status changes and
refresh token reuse are saved to `audit_entries` in the same transaction as the
change, together with the request id. A trigger rejects `UPDATE` and `DELETE`.

## Other

- One JSON error format, no stack traces or exception messages in responses.
- Actuator (`health`, `info`, `prometheus`) only on port 8081, not proxied by nginx.
- nginx sets CSP, `X-Frame-Options: DENY`, `nosniff` and a referrer policy, and
  overwrites `X-Forwarded-For`.
- Secrets come from environment variables.
- Containers run as non-root.

## Known limitations

- After disabling a user or changing roles, the old access token works for up
  to 15 minutes.
- Rate limits are in memory per instance.
- Registration returns 409 for an existing email, so it reveals which emails
  have accounts. Fixing this needs email verification.
- No password reset yet.
- Everything in `.env.example`, including the JWT secret and demo password, is
  public. Change all of it for a real deployment and don't use the `demo`
  profile there.
