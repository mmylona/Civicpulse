# API conventions

The REST API is versioned under `/api/v1`. OpenAPI documentation is generated
from the code once the first business endpoints exist (Phase 2).

## Error model

Every error response, for every status code, has this shape
(`Content-Type: application/json`):

```json
{
  "timestamp": "2026-10-03T14:32:07.512Z",
  "status": 404,
  "code": "REPORT_NOT_FOUND",
  "message": "Report was not found",
  "path": "/api/v1/reports/123",
  "traceId": "8f1c2a4e-63b0-4c8e-9a51-0f4b7f6f2d10",
  "errors": []
}
```

| Field | Meaning |
| --- | --- |
| `timestamp` | When the error was produced (UTC, ISO-8601) |
| `status` | HTTP status, repeated for clients that only see the body |
| `code` | Stable, machine-readable error code; clients and translations key on this |
| `message` | Human-readable English summary; never contains exception messages, SQL or class names |
| `path` | Request path |
| `traceId` | The request's `X-Request-Id`; quote it when reporting a problem |
| `errors` | Field-level validation errors: `{ "field", "code", "message" }`; empty otherwise |

The shape is a plain record rather than RFC 9457 `ProblemDetail` so it matches
the documented contract exactly, without duplicated fields (`detail` vs
`message`, `instance` vs `path`).

## Error codes

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | body or parameters fail validation; see `errors[]` |
| 400 | `MALFORMED_REQUEST` | unreadable JSON, wrong parameter type |
| 404 | `RESOURCE_NOT_FOUND` | no endpoint or resource at this path |
| 405 | `METHOD_NOT_ALLOWED` | endpoint exists, method does not |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | wrong `Content-Type` |
| 500 | `INTERNAL_ERROR` | anything unexpected; details only in server logs under `traceId` |

Business codes (`REPORT_NOT_FOUND`, `INVALID_STATUS_TRANSITION`, …) are added
with the modules that raise them.

## Request ids

Clients may send `X-Request-Id` (1–64 characters of `A–Z a–z 0–9 . _ -`).
Otherwise the server generates a UUID. The id is returned in the
`X-Request-Id` response header on every response.
