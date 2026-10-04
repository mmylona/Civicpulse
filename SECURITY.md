# Security

## Reporting a vulnerability

Please don't open a public issue. Use GitHub's private vulnerability reporting
(Security tab, "Report a vulnerability").

## Current state

| Area | Status |
| --- | --- |
| Authentication | Not implemented yet. Only the health and system-info endpoints exist. |
| Authorization | Not implemented yet. |
| Error responses | One JSON format for all errors; no stack traces, exception messages or class names in responses. |
| Request tracing | `X-Request-Id` on every response, also logged and included in error bodies. |
| Actuator | Only `health`, `info` and `prometheus`, on port 8081, which nginx does not proxy. |
| Secrets | Environment variables only; `.env` is git-ignored. |
| Containers | Backend and frontend run as non-root users. |

## Rules

- Authorization is always enforced in the backend; frontend route guards are
  only for navigation.
- Internal operator notes are never returned to citizens or anonymous users.
- File names, types and sizes sent by clients are not trusted.
- Demo credentials are for local use only.

## Known limitations

- The values in `.env.example` are public development defaults. Replace all of
  them for any deployment.
- A client-supplied `X-Request-Id` is accepted only if it is 1-64 characters of
  `A-Z a-z 0-9 . _ -`. It is for correlating logs, not a security control.
