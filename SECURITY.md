# Security Policy

CivicPulse is a portfolio project, but it is built and documented as if it
handled real citizens' data. This file describes the security model and its
known limitations. It grows with each implementation phase.

## Reporting a vulnerability

Please do not open a public issue. Use GitHub's private vulnerability reporting
("Security" tab → "Report a vulnerability") and include steps to reproduce.

## Security model (current state: Phase 1)

| Area | Current state | Planned |
| --- | --- | --- |
| Authentication | None yet; only health and system-info endpoints exist | Phase 2: JWT access tokens + rotating refresh tokens |
| Authorization | None yet | Phase 2–3: URL rules, resource policies, query-level visibility |
| Error responses | Uniform JSON error model; no stack traces, exception messages or class names leave the server | — |
| Request tracing | Every response carries `X-Request-Id`; the same id is in logs and error bodies | Phase 13: distributed tracing |
| Actuator | Only `health`, `info` and `prometheus`, on a separate management port (8081) that nginx does not proxy | — |
| Secrets | Read from environment variables; `.env` is git-ignored; `.env.example` holds development-only values | Phase 14: gitleaks in CI |
| Containers | Backend and frontend run as non-root users | Phase 14: image scanning |

## Principles that will not change

- Backend authorization is always enforced; frontend guards only improve navigation.
- Internal operator notes never appear in responses a citizen or anonymous user can obtain.
- Client-provided file metadata (name, type, size) is never trusted.
- No secret is ever committed. Demo credentials are documented as demo-only.

## Known limitations

- Development defaults in `.env.example` are public by design; any deployment
  beyond a laptop must replace every value.
- The client-supplied `X-Request-Id` is accepted only if it is 1–64 characters of
  `[A-Za-z0-9._-]`; otherwise a new id is generated. It is a correlation aid,
  not a security control.
