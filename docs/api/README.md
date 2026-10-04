# API

All endpoints are under `/api/v1`. Every endpoint needs a bearer token unless
marked public.

## Endpoints

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/system/info` | public | API name and version |
| POST | `/auth/register` | public | creates a citizen account and signs it in (201) |
| POST | `/auth/login` | public | returns an access token, sets the refresh cookie |
| POST | `/auth/refresh` | refresh cookie | rotates the refresh token, returns a new access token |
| POST | `/auth/logout` | refresh cookie | revokes the session, clears the cookie (204) |
| GET | `/me` | signed in | current user's profile |
| GET | `/admin/users?q=&role=&page=&size=` | ADMIN | search users by name or email, filter by role |
| PUT | `/admin/users/{id}/roles` | ADMIN | `{"roles": ["CITIZEN", "MODERATOR"]}` |
| PUT | `/admin/users/{id}/status` | ADMIN | `{"enabled": false}` |
| PUT | `/admin/users/{id}/team` | ADMIN | `{"teamId": "..."}` or `null` |
| GET | `/admin/teams` | ADMIN | all teams |
| GET | `/admin/audit?action=&actorId=&targetType=&targetId=&page=&size=` | ADMIN | audit log, newest first |

### Signing in

```http
POST /api/v1/auth/login
Content-Type: application/json

{"email": "citizen@demo.local", "password": "civicpulse-demo"}
```

```http
HTTP/1.1 200
Set-Cookie: civicpulse_refresh=...; Path=/api/v1/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Strict
Cache-Control: no-store

{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {"id": "...", "email": "citizen@demo.local", "displayName": "Demo Citizen", "roles": ["CITIZEN"], "team": null}
}
```

Send the token as `Authorization: Bearer <accessToken>`. On a 401 with
`TOKEN_INVALID`, call `/auth/refresh` and retry with the new token.

### Paged responses

```json
{"content": [], "page": {"number": 0, "size": 20, "totalElements": 0, "totalPages": 0}}
```

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
| 401 | `AUTHENTICATION_REQUIRED` | no access token |
| 401 | `TOKEN_INVALID` | expired, tampered or foreign token; refresh and retry |
| 401 | `INVALID_CREDENTIALS` | wrong email or password, or the account is locked |
| 401 | `INVALID_REFRESH_TOKEN` | missing, expired, revoked or reused refresh token |
| 401 | `REFRESH_TOKEN_ROTATED` | another request just refreshed; retry once |
| 403 | `ACCESS_DENIED` | signed in, but the role does not allow it |
| 404 | `RESOURCE_NOT_FOUND` | unknown path or resource |
| 404 | `USER_NOT_FOUND` | no user with this id |
| 405 | `METHOD_NOT_ALLOWED` | method not supported on this path |
| 409 | `EMAIL_ALREADY_REGISTERED` | registration with an existing email |
| 409 | `CANNOT_REMOVE_OWN_ADMIN_ROLE`, `CANNOT_DISABLE_OWN_ACCOUNT` | an admin would lock themselves out |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | wrong `Content-Type` |
| 422 | `UNKNOWN_TEAM` | team id does not exist |
| 429 | `RATE_LIMITED` | too many attempts; see `Retry-After` |
| 500 | `INTERNAL_ERROR` | unexpected error; details only in the logs |

## Request ids

Clients can send `X-Request-Id` (1-64 characters of `A-Z a-z 0-9 . _ -`).
Otherwise the server generates one. It is returned on every response.
