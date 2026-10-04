# API

All endpoints are under `/api/v1`.

## Error format

Every error response has this shape (`Content-Type: application/json`):

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
| `timestamp` | when the error happened (UTC) |
| `status` | HTTP status |
| `code` | stable error code for clients and translations |
| `message` | short English description, never internal details |
| `path` | request path |
| `traceId` | the request's `X-Request-Id` |
| `errors` | field errors (`field`, `code`, `message`) for validation failures |

A plain record is used instead of Spring's `ProblemDetail` to keep exactly these
fields without duplicates like `detail`/`message`.

## Error codes

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | invalid body or parameters, see `errors` |
| 400 | `MALFORMED_REQUEST` | unreadable JSON or wrong parameter type |
| 404 | `RESOURCE_NOT_FOUND` | unknown path or resource |
| 405 | `METHOD_NOT_ALLOWED` | method not supported on this path |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | wrong `Content-Type` |
| 500 | `INTERNAL_ERROR` | unexpected error; details only in the logs |

## Request ids

Clients can send `X-Request-Id` (1-64 characters of `A-Z a-z 0-9 . _ -`).
Otherwise the server generates one. It is returned on every response.
