# REST and WebSocket API

The local backend exposes a versioned REST API for commands and queries and a
WebSocket channel for live timer and domain-event notifications. The API is
local-only: it binds to `127.0.0.1`, rejects non-loopback origins and requires a
per-installation bearer token.

Base URL: `http://127.0.0.1:<port>/api/v1`

## 1. Authentication and transport

| Concern | Rule |
|---|---|
| Binding | Loopback only (`127.0.0.1` / `::1`). |
| Token | `Authorization: Bearer <token>`; generated at first backend startup, stored in the local data directory as `api-token`, then reused. The file is readable only by the current user; the Tauri shell reads it locally and supplies the header. |
| Content type | `application/json` for requests and responses. |
| Versioning | `/api/v1`; breaking changes require a new version. |
| CORS | Only the local desktop origin is allowed. |
| Errors | Stable error code + human-readable message. |

## 2. Sessions

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/sessions` | Start a focus session. Request: `{"sessionType":"WORK"}`. | `201 Created` + `SessionResponse` |
| `GET` | `/sessions/active` | Get the active session, if any. | `200 OK` + `SessionResponse` / `204 No Content` |
| `POST` | `/sessions/{id}/pause` | Pause a running session. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/resume` | Resume a paused session. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/stop` | Stop and record the session, including while waiting for the next interval. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/intervals/start` | Start the pending next interval while the session is waiting. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/skip-break` | Skip a pending break and start the next work interval. | `200 OK` + `SessionResponse` |
| `GET` | `/sessions` | Query history by range. | `200 OK` + `Page<SessionSummaryDto>` |
| `GET` | `/sessions/{id}` | Session detail with intervals. | `200 OK` + `SessionDetailDto` |

Query parameters for `GET /sessions`: `from`, `to` (`YYYY-MM-DD`), `page`
(default `0`), `size` (default `20`, max `100`), optional `type` and `status`.
Date boundaries for history and analytics are local calendar dates interpreted
in the operating system's current time zone and converted to UTC instants. If
the OS time zone changes, historical daily groupings use the new zone.

`SessionResponse` contains the session identity, type, state, interval type,
remaining time, planned/actual durations, timestamps and the current interval
sequence. IDs are UUID strings, durations are integer seconds, and timestamps
are ISO-8601 UTC strings; domain value objects are not exposed in the JSON.
For example:

```json
{
  "sessionId": "e954a50a-90fd-4e8b-8770-e5e19f8acfe1",
  "sessionType": "WORK",
  "state": "RUNNING",
  "intervalType": "WORK",
  "intervalState": "RUNNING",
  "intervalSequence": 1,
  "plannedDurationSeconds": 1500,
  "actualDurationSeconds": 0,
  "actualFocusDurationSeconds": 0,
  "remainingSeconds": 1500,
  "createdAt": "2026-11-11T00:00:00Z",
  "startedAt": "2026-11-11T00:00:00Z",
  "endedAt": null
}
```
The backend is authoritative for elapsed time. When the state is
`WAITING_FOR_NEXT_INTERVAL`, `intervalType` identifies the pending interval and
`remainingSeconds` is its full planned duration; no countdown runs until it is
started. In `PAUSED`, it is the persisted remaining time. `GET /sessions/active`
returns the session in `RUNNING`, `PAUSED` or `WAITING_FOR_NEXT_INTERVAL`, and
returns `204` only when none exists. The `intervals/start` operation returns
`422 INVALID_SESSION_STATE` unless the session is waiting and has a pending
interval. `skip-break` returns `422 INVALID_SESSION_STATE` unless a pending
interval is waiting, and `422 INVALID_INTERVAL_TYPE` if that interval is work.

The backend scheduler checks due intervals every five seconds. It records
completion at the planned interval end, so the persisted duration does not
include the scheduler's detection delay; the session may remain `RUNNING` with
zero seconds remaining until the next scheduler check.

The scheduler is an internal backend component and invokes interval completion
directly; interval completion is not a public REST operation. The client renders
a local countdown between backend snapshots, polls `GET /sessions/active` every
30 seconds while running, and reconciles immediately after reconnect or any
state-changing response/event.

## 3. Analytics and streaks

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/analytics/summary?from=...&to=...` | Productivity summary and daily breakdown. | `200 OK` + `AnalyticsDto` |
| `GET` | `/analytics/streak?date=...` | Current and longest streak. | `200 OK` + `StreakDto` |

`AnalyticsDto` is assembled from read models:

```json
{
  "from": "2026-11-01",
  "to": "2026-11-07",
  "totalFocusSeconds": 12600,
  "totalBreakSeconds": 2400,
  "completedSessions": 8,
  "averageSessionSeconds": 1575,
  "focusScore": 82,
  "distractionCount": 5,
  "daily": [
    { "date": "2026-11-01", "focusSeconds": 1500, "sessions": 1, "distractions": 0 }
  ]
}
```

Range rules: `from <= to`, maximum 366 days. An empty range returns zero totals
and a zero-filled daily breakdown.

## 4. Settings

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/settings` | Read timer, theme, notification and retention settings. | `200 OK` + `SettingsDto` |
| `PUT` | `/settings/timer` | Update work/break durations and auto-start flags. | `200 OK` + `SettingsDto` |
| `PUT` | `/settings/application` | Update theme, notifications and retention. | `200 OK` + `SettingsDto` |
| `DELETE` | `/settings/data` | Delete all personal data after confirmation. | `204 No Content` |

Timer validation: work 1–180 minutes, short break 1–60 minutes, long break
1–120 minutes and not shorter than the short break, intervals before long break
1–12. Retention is 7–3650 days or "keep forever". Settings changes apply to new
sessions only; the active session keeps its snapshot.

The delete request requires a confirmation token in the body. It removes
sessions, intervals, distraction logs, daily progress and settings, then
recreates defaults. The active session is stopped first; cleanup never deletes an
active session silently.

## 5. Blocking rules (Phase 2)

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/blocking-rules` | List the active policy's rules. | `200 OK` + `List<BlockingRuleDto>` |
| `POST` | `/blocking-rules` | Create a rule. | `201 Created` + `BlockingRuleDto` |
| `PATCH` | `/blocking-rules/{id}` | Enable, disable or edit a rule. | `200 OK` + `BlockingRuleDto` |
| `DELETE` | `/blocking-rules/{id}` | Delete a rule. | `204 No Content` |

`BlockingRuleDto` contains `id`, `pattern`, `action` (`LOG`, `WARN`, `BLOCK`),
`category`, `enabled` and timestamps. Rule precedence is exact executable path →
process name → window-title pattern; the first matching enabled rule wins.

## 6. Distraction log (Phase 2 read model)

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/distractions?sessionId=...&from=...&to=...&page=0&size=20` | Query the distraction log read model. | `200 OK` + `Page<DistractionDto>` |

`DistractionDto` contains `detectionId`, `sessionId`, `app`, `title`,
`category`, `action`, `blocked`, `failureReason` and `occurredAt`. The log is
append-only; window titles are redacted when the category policy requires it.

## 7. WebSocket

Connect to `ws://127.0.0.1:<port>/ws` with the same bearer token as REST. A
browser client must use the Vite server-side WebSocket proxy to attach this
header; the token must not be exposed to browser JavaScript. The upgrade
request must have an allowed local origin when an `Origin` header is present.
The server sends versioned JSON envelopes containing the stable
outbox event ID, event type, occurrence time and the latest persisted session
snapshot:

```json
{
  "eventId": "8e6d12aa-9d8c-467e-9fd7-ec43b32ee362",
  "version": 1,
  "type": "session.started",
  "occurredAt": "2026-11-07T15:30:00Z",
  "payload": {
    "sessionId": "e954a50a-90fd-4e8b-8770-e5e19f8acfe1",
    "sessionType": "WORK",
    "state": "RUNNING",
    "intervalType": "WORK",
    "intervalState": "RUNNING",
    "intervalSequence": 1,
    "plannedDurationSeconds": 1500,
    "actualDurationSeconds": 0,
    "actualFocusDurationSeconds": 0,
    "remainingSeconds": 1500,
    "createdAt": "2026-11-07T15:30:00Z",
    "startedAt": "2026-11-07T15:30:00Z",
    "endedAt": null
  }
}
```

| Event type | When |
|---|---|
| `session.started` | Session started |
| `session.paused` | Session paused |
| `session.resumed` | Session resumed |
| `session.completed` | Session completed |
| `session.abandoned` | Session abandoned |
| `interval.completed` | Work or break interval completed |
| `interval.started` | A pending interval starts |
| `break.skipped` | A pending break is skipped |

The `payload` uses the `SessionResponse` shape above and represents the latest
persisted state when the event is dispatched; it is not a historical snapshot
captured at the event's occurrence time. Events are dispatched from the durable
outbox only after the transaction commits. Dispatch retries failures with
exponential delay capped at 60 seconds. Delivery is at least once: a crash
after broadcast but before acknowledgement can produce the same `eventId`
again, so clients should ignore duplicate IDs. A broadcast is considered
handled when accepted by the local WebSocket server, even if no clients are
connected. WebSocket delivery is otherwise best-effort, and the timer does not
receive one-second ticks. On connect or reconnect, clients fetch
`GET /sessions/active` to reconcile. The timer also polls that endpoint every
30 seconds while running.

Distraction event types are reserved for a later feature and are not currently
published by FST-015.

When a session is abandoned because it ended before the five-minute minimum,
`reason` is `BELOW_MINIMUM_FOCUS_TIME`.

## 8. Error contract

```json
{
  "code": "INVALID_SESSION_STATE",
  "message": "Session is not running.",
  "details": [],
  "timestamp": "2026-11-07T15:30:00Z"
}
```

| HTTP | Code | Meaning |
|---|---|---|
| `400` | `INVALID_DATE_RANGE` | `from > to` or malformed date. |
| `400` | `RANGE_TOO_LARGE` | Analytics range exceeds 366 days. |
| `401` | `UNAUTHORIZED` | Missing or invalid token. |
| `404` | `SESSION_NOT_FOUND` | Unknown session id. |
| `404` | `RESOURCE_NOT_FOUND` | Unknown rule or distraction entry. |
| `409` | `ACTIVE_SESSION_EXISTS` | Another session is already active. |
| `409` | `DUPLICATE_RULE` | A rule with the same pattern exists. |
| `422` | `INVALID_SESSION_STATE` | Pause/resume/stop/start-next-interval on the wrong state. |
| `422` | `INVALID_INTERVAL_TYPE` | Skip-break requested while the current interval is work. |
| `422` | `VALIDATION_ERROR` | Field-level validation failure. |
| `500` | `INTERNAL_ERROR` | Unexpected server error; no stack trace is exposed. |

## 9. Related documents

- [Use case catalogue](../UseCases/UseCases.md)
- [Sequence diagrams](../SD/SequenceDiagrams.md)
- [Persistence](../data/Persistence.md)
- [Test strategy](../testing/TestStrategy.md)
