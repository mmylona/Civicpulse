# API

All endpoints are under `/api/v1`. Every endpoint needs a bearer token unless
marked public. Public endpoints also accept a token and then return more
(for example `mine` on a report).

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
| GET | `/categories` | public | active categories |
| POST | `/reports` | CITIZEN | new report (201), optional `Idempotency-Key` header |
| GET | `/reports?status=&category=&district=&q=&page=&size=` | public | newest first; staff can also filter by `teamId` and `assignee=me` |
| GET | `/reports/map?bbox=&zoom=&category=&includeClosed=` | public | clusters below zoom 15, markers from 15, see below |
| GET | `/reports/nearby?lat=&lng=&radius=&category=&limit=` | public | open reports within `radius` metres (max 2000), closest first |
| GET | `/geo/districts` | public | districts with their outline as GeoJSON |
| GET | `/geo/critical-sites` | public | hospitals, schools and stations |
| GET | `/reports/mine` | signed in | the caller's own reports |
| GET | `/reports/{id}` | public | details with `ETag`; staff get an extra `staff` object |
| PATCH | `/reports/{id}` | reporter | only while `REPORTED`, needs `If-Match` |
| GET | `/reports/{id}/actions` | signed in | what the caller can do now |
| GET | `/reports/{id}/timeline` | public | history; staff also see internal notes |
| POST | `/reports/{id}/{action}` | depends on action | lifecycle actions, need `If-Match`, see below |
| GET | `/reports/{id}/comments` | public | staff also see internal comments |
| POST | `/reports/{id}/comments` | signed in | `{"body": "...", "visibility": "PUBLIC"}`, `INTERNAL` for staff only |
| PATCH | `/reports/{id}/comments/{commentId}` | MODERATOR | `{"hidden": true, "reason": "..."}` |
| PUT | `/reports/{id}/confirmation` | CITIZEN | `{"kind": "STILL_PRESENT"}` or `FIXED`, not on your own report |
| DELETE | `/reports/{id}/confirmation` | CITIZEN | withdraw your confirmation |

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

### Reports

```http
POST /api/v1/reports
Authorization: Bearer ...
Idempotency-Key: 6f0c1d5e-7d1e-4a0b-9d38-3f2a1c0b9e11
Content-Type: application/json

{"categoryCode": "POTHOLE", "title": "Deep pothole", "description": "Next to the tram stop",
 "severity": "HIGH", "latitude": 46.948, "longitude": 7.4474, "address": "Bubenbergplatz"}
```

```http
HTTP/1.1 201
Location: /api/v1/reports/0199b1c4-...
ETag: "0"

{"id": "0199b1c4-...", "reference": "CP-10000", "status": "REPORTED", "version": 0, "mine": true, "staff": null, ...}
```

Sending the same `Idempotency-Key` again returns the report created the first
time instead of a second one.

The public view never contains the reporter, the team, the assignee or internal
notes. Those are in `staff`, which is only filled in for moderators, operators
and admins.

### Map

`bbox` is `west,south,east,north` in degrees (what Leaflet's
`getBounds().toBBoxString()` returns). It may not be much larger than a
screen at the given zoom.

```http
GET /api/v1/reports/map?bbox=7.38,46.93,7.50,46.97&zoom=13
```

```json
{
  "mode": "CLUSTERS",
  "clusters": [
    {"latitude": 46.9481, "longitude": 7.4479, "count": 17, "categoryCode": "POTHOLE", "severity": "HIGH", "reportId": null}
  ],
  "markers": [],
  "truncated": false
}
```

From zoom 15 `mode` is `MARKERS` and `markers` holds single reports (id,
reference, title, category, status, severity, position). A cluster with one
report has its `reportId`. At most 500 items are returned; `truncated` says if
there were more. Closed reports are left out unless `includeClosed=true`.
The map and nearby endpoints allow 120 requests per minute per IP.

The ADR with the query details and measurements is
[ADR 005](../adr/005-map-and-nearby.md).

### Lifecycle actions

Each action is `POST /api/v1/reports/{id}/{action}`. The rules are in
[ADR 004](../adr/004-report-lifecycle.md).

| Action | Body |
| --- | --- |
| `triage` | `{"categoryCode": "POTHOLE", "severity": "HIGH"}` |
| `reject` | `{"reason": "NOT_A_CITY_MATTER", "note": "..."}` (`NOT_ENOUGH_INFORMATION`, `ALREADY_FIXED`, `INAPPROPRIATE`, `OTHER`) |
| `mark-duplicate` | `{"duplicateOf": "<report id>"}` |
| `request-information` | `{"question": "..."}` |
| `provide-information` | `{"answer": "..."}` |
| `resume` | `{"note": "..."}` (optional, staff only) |
| `assign` | `{"teamId": "...", "assigneeId": "..."}` (assignee optional) |
| `unassign` | `{"reason": "..."}` (staff only) |
| `start` | none |
| `hand-back` | `{"note": "..."}` (staff only) |
| `resolve` | `{"resolution": "..."}` |
| `reopen` | `{"comment": "..."}` |
| `close` | none |
| `restore` | `{"reason": "...", "severity": "HIGH"}` (severity optional, reason staff only) |

"Staff only" means the text is stored as an internal timeline note.

### Concurrent changes

`GET /reports/{id}` returns the report version as `ETag`. `PATCH` and all
actions need it back in `If-Match`:

```http
POST /api/v1/reports/0199b1c4-.../assign
If-Match: "3"
```

Without `If-Match` the answer is 428, with an old version 412. Reload the
report and try again. The version only changes when the report itself changes,
not on new comments or confirmations.

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
| 404 | `REPORT_NOT_FOUND`, `COMMENT_NOT_FOUND` | no report or comment with this id |
| 405 | `METHOD_NOT_ALLOWED` | method not supported on this path |
| 409 | `EMAIL_ALREADY_REGISTERED` | registration with an existing email |
| 409 | `CANNOT_REMOVE_OWN_ADMIN_ROLE`, `CANNOT_DISABLE_OWN_ACCOUNT` | an admin would lock themselves out |
| 400 | `INVALID_BOUNDING_BOX` | `bbox` is not `west,south,east,north` or too large for the zoom |
| 409 | `INVALID_STATUS_TRANSITION` | the action is not possible in the report's status |
| 409 | `REPORT_NOT_EDITABLE` | the report was already triaged |
| 409 | `REOPEN_WINDOW_CLOSED` | resolved more than 14 days ago |
| 409 | `DUPLICATE_TARGET_INVALID` | the other report is the same report or closed |
| 409 | `REPORT_CLOSED` | no comments or confirmations on a closed report |
| 409 | `OWN_REPORT` | confirming your own report |
| 412 | `VERSION_CONFLICT` | `If-Match` does not match the current version |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | wrong `Content-Type` |
| 422 | `UNKNOWN_TEAM` | team id does not exist |
| 422 | `UNKNOWN_CATEGORY` | category code does not exist |
| 422 | `LOCATION_OUTSIDE_CITY` | the location is not in any district |
| 422 | `ASSIGNEE_NOT_IN_TEAM` | the assignee is not an operator in that team |
| 428 | `PRECONDITION_REQUIRED` | `If-Match` is missing |
| 429 | `RATE_LIMITED` | too many attempts; see `Retry-After` |
| 500 | `INTERNAL_ERROR` | unexpected error; details only in the logs |

## Request ids

Clients can send `X-Request-Id` (1-64 characters of `A-Z a-z 0-9 . _ -`).
Otherwise the server generates one. It is returned on every response.
