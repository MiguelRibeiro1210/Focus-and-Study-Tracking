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

In the desktop app, the Rust Tauri process reads the token and attaches it to
REST and WebSocket requests. The token is never sent to the WebView. Browser
development continues to use Vite's local proxy, which adds the same header on
the server side. The desktop shell also uses a bearer-authenticated private
`POST /internal/shutdown` lifecycle endpoint when closing the sidecar; it is
not a learner-facing session API operation.

## 2. Sessions

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/sessions` | Start a focus session. Optional `timerOverride` supplies one-session work/short/long duration overrides. | `201 Created` + `SessionResponse` |
| `GET` | `/sessions/active` | Get the active session, if any. | `200 OK` + `SessionResponse` / `204 No Content` |
| `POST` | `/sessions/{id}/pause` | Pause a running session. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/resume` | Resume a paused session. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/stop` | Stop and record the session, including while waiting for the next interval. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/intervals/start` | Start the pending next interval while the session is waiting. | `200 OK` + `SessionResponse` |
| `POST` | `/sessions/{id}/skip-break` | Skip a pending or paused break and start the next work interval. | `200 OK` + `SessionResponse` |
| `GET` | `/sessions` | Query completed and abandoned history summaries. | `200 OK` + `SessionHistoryPage` |
| `GET` | `/sessions/{id}` | Planned session detail endpoint (outside the history-list slice). | `200 OK` + `SessionDetailDto` |

Query parameters for `GET /sessions`: optional inclusive `from`, `to`
(`YYYY-MM-DD`), `page` (default `0`), `size` (default `20`, max `100`), and
optional terminal `status`. Omitting both date bounds returns all
available history; either bound may be supplied alone. If both are present,
`from` must not be after `to`. Dates are interpreted in the operating system's
current timezone and converted to UTC instants. Date filtering uses
`startedAt`, so a session crossing midnight belongs to its start date. No
maximum history range is imposed. The default status set includes `COMPLETED`
and `ABANDONED`; results are newest first by `startedAt`, with session ID as a
stable tie-breaker.
For equal start timestamps, session IDs are sorted ascending.
`page` must be non-negative and `size` must be between 1 and 100; malformed
dates, unsupported status values or invalid pagination return
`400 VALIDATION_ERROR`, except `from > to`, which returns
`400 INVALID_DATE_RANGE`.

`SessionHistoryPage` is `{ "items": [...], "total": 0, "page": 0,
"size": 20 }`. Each item contains `sessionId`, `projectName`, `sessionType`,
`status`, `startedAt`, `endedAt` and `actualFocusDurationSeconds`. The query
selects these fields from `focus_session` and does not load interval
collections or require a separate materialized history table. The UI
defaults to today and the previous 6 local calendar dates; 30 calendar days
and all time are also available. Inclusive local date bounds are converted to
a UTC start instant and an exclusive UTC instant at midnight after `to`.
There is no session-type filter because `FocusSession` currently only supports
`WORK`; `sessionType` remains in each summary for clarity and future expansion.

Example history response:

```json
{
  "items": [
    {
      "sessionId": "e954a50a-90fd-4e8b-8770-e5e19f8acfe1",
      "projectName": "Exam preparation",
      "sessionType": "WORK",
      "status": "COMPLETED",
      "startedAt": "2026-11-11T00:00:00Z",
      "endedAt": "2026-11-11T00:50:00Z",
      "actualFocusDurationSeconds": 2700
    }
  ],
  "total": 1,
  "page": 0,
  "size": 20
}
```

`POST /sessions` requires `projectName`. The backend trims it, accepts 1–120
characters after trimming, rejects missing/blank/overlength values with
`400 VALIDATION_ERROR`, and stores the normalized name immutably with the
session. `SessionResponse` and live session snapshots include `projectName`.

The optional `timerOverride` object may contain any subset of `workSeconds`,
`shortBreakSeconds` and `longBreakSeconds`. Omitted values come from the
currently selected default profile. `intervalsBeforeLongBreak` and the
independent auto-start flags always come from that profile. The resulting
settings are validated and snapshotted in the session; the override is not
persisted as a profile.

`SessionResponse` contains the session identity, project name, type, state,
interval type, remaining time, planned/actual durations, timestamps and the
current interval sequence. IDs are UUID strings, durations are integer seconds, and timestamps
are ISO-8601 UTC strings; domain value objects are not exposed in the JSON.
For example:

```json
{
  "sessionId": "e954a50a-90fd-4e8b-8770-e5e19f8acfe1",
  "projectName": "Exam preparation",
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
break is waiting or the session is paused during a break; it returns
`422 INVALID_INTERVAL_TYPE` if the current interval is work. Skipping a paused
break retains the elapsed duration recorded before the pause and discards the
remaining break duration.

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

`AnalyticsDto` is assembled by the backend from completed sessions and their
persisted work and break intervals. The current MVP does not return
distraction metrics. `from` and `to` must either both be supplied or both be
omitted. Supplied local-date bounds are inclusive and may span at most 366
calendar days. Omitting both requests all-time totals; its `daily` array covers
the latest 7 local calendar days for the existing weekly chart. `from` and `to`
are `null` in an all-time response.

```json
{
  "from": "2026-11-01",
  "to": "2026-11-07",
  "totalFocusSeconds": 12600,
  "totalBreakSeconds": 2400,
  "completedSessions": 8,
  "averageSessionSeconds": 1575,
  "focusScore": 82,
  "bestDay": { "date": "2026-11-05", "focusSeconds": 3600, "completedSessions": 2 },
  "daily": [
    { "date": "2026-11-01", "focusSeconds": 1500, "breakSeconds": 300, "completedSessions": 1 }
  ]
}
```

The focus score is `round(100 × actual work seconds / planned work seconds)`
across completed sessions, clamped to `[0, 100]`; a zero denominator yields
`0`. Its planned-work denominator sums planned durations for started work
intervals in completed sessions; unstarted pending work intervals are excluded.
No distraction penalty is applied until distraction tracking is implemented.
`averageSessionSeconds` is total focus seconds divided by completed sessions.
`bestDay` is computed across the full selected range and is `null` when it has
no completed sessions; when days tie, the most recent date wins. Daily buckets
use each session's `startedAt` local date.
An empty range returns zero totals and a zero-filled daily breakdown. For
explicit ranges, `daily` includes every local date from `from` through `to`,
even when its values are zero.

## 4. Settings

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/settings` | Read timer profiles, selected default, daily goal, theme, notifications and retention. | `200 OK` + `SettingsDto` |
| `POST` | `/settings/profiles` | Create a profile by copying the current default; request includes its name. | `201 Created` + updated `SettingsDto` |
| `PUT` | `/settings/profiles/{profileId}` | Edit a profile name, durations, interval count and auto-start flags. | `200 OK` + updated `SettingsDto` |
| `PUT` | `/settings/profiles/default` | Select the profile used by new sessions. Request includes `profileId`. | `200 OK` + `SettingsDto` |
| `DELETE` | `/settings/profiles/{profileId}` | Delete a non-default profile; any starter profile may be deleted. | `204 No Content` |
| `PUT` | `/settings/application` | Update theme, notifications, retention and daily goal. | `200 OK` + `SettingsDto` |
| `GET` | `/settings/export?format=json\|csv-zip` | Export all settings, profiles, sessions and analytics. | `200 OK` + JSON or ZIP of CSV files |
| `DELETE` | `/sessions/history` | Delete completed and abandoned sessions, preserving settings and any active session. | `204 No Content` |
| `DELETE` | `/sessions/history/expired` | On explicit request, delete terminal sessions older than the configured retention period. | `200 OK` + `{ "deletedSessions": 3 }` |
| `DELETE` | `/settings/data` | Delete all personal data after confirmation. | `204 No Content` |

Timer validation: work 1–180 minutes, short break 1–60 minutes, long break
1–120 minutes and not shorter than the short break, intervals before long break
1–12. Auto-start work and breaks are independent. Profile names are trimmed,
non-empty and unique without regard to case. At least one profile must remain;
the default cannot be deleted until another profile has been selected. New
profiles copy the current default's timer preferences. Retention is 7–3650 days
or "keep forever"; daily goal is an integer from 1–24 hours. Settings changes
apply to new sessions only; the active session keeps its snapshot. Profile
names contain 1–60 characters after trimming.

The CSV ZIP contains separate files for settings, profiles, sessions,
intervals, and analytics. The JSON export contains the equivalent data in one
file. The data-folder action is a Tauri local command and does not expose a
filesystem path through the HTTP API.

The delete request requires a confirmation token in the body. It removes
sessions, intervals, distraction logs, daily progress, profiles, outbox events
and settings, then recreates default settings and starter profiles. The active
session is stopped first. History deletion is a separate confirmed operation
and leaves the active session and all settings in place. The Settings UI asks
for explicit confirmation before clearing history. Retention cleanup is a
separate, user-triggered action: it deletes completed or abandoned sessions
whose `endedAt` is at or before `now - retentionDays`, together with their
intervals and outbox events. It preserves active sessions and all settings. A
`null` retention period means keep forever and makes cleanup a no-op. Cleanup
does not run automatically at startup or on a schedule.

## 5. Blocking rules (Phase 2)

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/blocking-rules` | List the active policy's rules. | `200 OK` + `List<BlockingRuleDto>` |
| `POST` | `/blocking-rules` | Create a rule. | `201 Created` + `BlockingRuleDto` |
| `PATCH` | `/blocking-rules/{id}` | Enable, disable or edit a rule. | `200 OK` + `BlockingRuleDto` |
| `DELETE` | `/blocking-rules/{id}` | Delete a rule. | `204 No Content` |

These are planned FST-048 endpoints, not implemented APIs. The FST-008
contract draft replaces the ambiguous `pattern` with a typed `target` and
adds `displayName`. `BlockingRuleDto` contains `id`, `displayName`, `target`,
`action` (`LOG`, `WARN`, `BLOCK`), `category`, `enabled` and timestamps.

Application target: `{ "type": "APPLICATION", "platform": "WINDOWS",
"matchKind": "PROCESS_NAME", "value": "example.exe" }`; the other application
match kind is `EXECUTABLE_PATH`. Platform distinguishes Windows, macOS and
Linux identifier comparison. Website target:
`{ "type": "WEBSITE", "domain": "youtube.com" }`; subdomains are always included.
Window-title rules and URL/path-pattern rules are deferred.

Create/edit payloads use the same target variants. FST-048 will finalize HTTP
validation and concurrency details after domain approval. See the
[approved FST-008 design](../decisions/ADR-006-distraction-model.md) for
normalization, duplicates and precedence. Saving/enabling a rule is separate
from actual adapter capability or currently active protection.

## 6. Distraction log (Phase 2 read model)

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/distractions?sessionId=...&from=...&to=...&page=0&size=20` | Query the distraction log read model. | `200 OK` + `Page<DistractionDto>` |

The planned `DistractionDto` contains `detectionId`, `sessionId`, `ruleId`,
configured/observed target snapshots, `category`, `action`, `blocked`,
`failureReason` and `occurredAt`. Correlated outcome events distinguish pending,
successful and failed enforcement; `blocked` is true only after confirmation.
The read model derives from append-only events. Website hosts suffice for
matching/logging; full URLs, credentials and page contents are not required.
FST-037 owns persistence and queries, with final wire details reviewed there.

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
    "projectName": "Exam preparation",
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
| `break.skipped` | A pending or paused break is skipped |

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
When `interval.completed` and the following `interval.started` share an
occurrence timestamp, the outbox preserves their transition order and sends
completion first. Each `interval.completed` envelope also includes
`completedIntervalType` (`WORK`, `SHORT_BREAK`, or `LONG_BREAK`) from the domain
event. This identifies the interval that ended even when the session snapshot
already describes the next interval.

The desktop UI reacts only to unique `interval.completed` event IDs, chooses
the message from `completedIntervalType`, and ignores `session.completed` for
notification purposes. Because event delivery is best-effort, a missed event
discovered only through a later REST reconciliation does not produce a delayed
notification.

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
| `400` | `INVALID_DATE_RANGE` | Malformed date, `from > to`, or only one analytics bound supplied. |
| `400` | `RANGE_TOO_LARGE` | Explicit analytics range exceeds 366 inclusive local calendar days. |
| `401` | `UNAUTHORIZED` | Missing or invalid token. |
| `404` | `SESSION_NOT_FOUND` | Unknown session id. |
| `404` | `RESOURCE_NOT_FOUND` | Unknown rule or distraction entry. |
| `409` | `ACTIVE_SESSION_EXISTS` | Another session is already active. |
| `409` | `DUPLICATE_RULE` | A rule with the same canonical typed target exists. |
| `422` | `INVALID_SESSION_STATE` | Pause/resume/stop/start-next-interval on the wrong state. |
| `422` | `INVALID_INTERVAL_TYPE` | Skip-break requested while the current interval is work. |
| `422` | `VALIDATION_ERROR` | Field-level validation failure. |
| `500` | `INTERNAL_ERROR` | Unexpected server error; no stack trace is exposed. |

## 9. Related documents

- [Use case catalogue](../UseCases/UseCases.md)
- [Sequence diagrams](../SD/SequenceDiagrams.md)
- [Persistence](../data/Persistence.md)
- [Test strategy](../testing/TestStrategy.md)
