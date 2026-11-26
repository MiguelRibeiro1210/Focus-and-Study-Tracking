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

These remain planned until the FST-048 implementation commit. The
[management contract](../decisions/ADR-007-BlockingRuleManagement.md) defines
persistence, mutation locking, exact request shapes and verification.

`BlockingRuleDto` contains `id`, `displayName`, `target`, `category`, `action`,
`enabled`, `createdAt` and `updatedAt`. GET returns a JSON array (initially `[]`)
ordered by creation time then UUID. POST requires all five editable fields:
`displayName`, `target`, `category`, `action`, `enabled`. The supported category
is currently `OTHER`; actions are `LOG`, `WARN`, `BLOCK`.

Application target: `{ "type": "APPLICATION", "platform": "WINDOWS",
"matchKind": "PROCESS_NAME", "value": "example.exe" }`. Platforms are
`WINDOWS`, `MACOS`, `LINUX`; match kinds are `EXECUTABLE_PATH`, `PROCESS_NAME`.
Website target: `{ "type": "WEBSITE", "domain": "youtube.com" }`, always
including subdomains. Application values retain the entered spelling; website
domains are normalized. Read-only IDs/timestamps are assigned by the server.

PATCH preserves omitted fields. A supplied `target` is one complete typed
replacement; `{ "enabled": false }` changes only the enabled flag. Reject null
values, unknown/read-only fields, mismatched target-variant fields, unsupported
values and empty PATCH objects. Existing domain normalization, duplicate checks
and precedence remain authoritative.

Rule endpoints return `400 VALIDATION_ERROR` for malformed JSON, malformed UUIDs
or non-object bodies; `422 VALIDATION_ERROR` with field names in `details` for
invalid/missing editable fields; `409 DUPLICATE_RULE` for canonical duplicates;
and `404 RESOURCE_NOT_FOUND` for unknown rule IDs, including repeated DELETE.
Existing token/origin responses remain unchanged. These rule-specific handlers
must not change validation statuses for existing session/settings endpoints.

POST returns `201` plus the persisted rule and `Location`; PATCH returns `200`
plus the persisted rule; DELETE returns empty `204`. Acquire the singleton policy
lock before loading a mutable aggregate and hold it through commit. GET does
not lazily create a policy: Flyway seeds one empty policy. Failed changes roll
back; concurrent valid changes must not lose one another.

PATCH must be supported by both CORS and Tauri's native HTTP bridge. Data export
adds `blockingRules` in JSON and `blocking-rules.csv` in the ZIP; confirmed
delete-all clears the rules while preserving an empty policy. History-only
cleanup preserves configuration. Saving/enabling a rule does not claim runtime
protection or emit a block-success event. The Distractions UI is FST-049.

### Foreground detection status (FST-027)

Authenticated `GET /api/v1/blocking-status` returns `monitorEnabled`,
`applicationDetection` (`DISABLED`, `IDLE`, `OBSERVING`, `UNAVAILABLE`), a safe
`applicationReason`, `applicationEnforcement`, `websiteEnforcement` and UTC
`checkedAt`. This endpoint reads diagnostics without querying the OS and returns
no executable, title or process identifier. FST-028 adds
`applicationProtectionEnabled`, `ownedRestrictions`, `applicationProtectionState`
(`DISABLED`, `READY`, `ACTIVE`, `RESTORING`, `UNAVAILABLE`) and an optional
correlated `notice` (`id`, `kind`, `message`, `occurredAt`). Application enforcement
is true only while confirmed owned restrictions exist; website enforcement is
retained as false for compatibility; use the separate website-protection status
for browser routing. `READY` means configured to evaluate, not successfully blocked.
Desktop launch enables the monitoring runtime; standalone launches require the explicit
`--focusdesktop.blocking.monitor-enabled=true` option. See
[ADR-009](../decisions/ADR-009-ApplicationMonitoring.md) for platform requirements.

Authenticated `PATCH /api/v1/blocking-protection` accepts
`{"applicationsEnabled":true,"consent":"SUSPEND_MATCHED_APPS"}` to opt in, or
`{"applicationsEnabled":false}` to disable and trigger restoration. It returns
`200` with `applicationsEnabled` and nullable UTC `applicationConsentAt`. Missing
consent when enabling, wrong field types and unknown fields return `400`.
The setting defaults to false, is exported with personal data and is reset by
delete-all. Rules alone never activate protection. See
[ADR-010](../decisions/ADR-010-ApplicationSuspension.md).

### Website protection (FST-050)

Authenticated `GET /api/v1/website-protection` returns `enabled`, `state`
(`DISABLED`, `STARTING`, `READY`, `VERIFIED`, `RESTORING`, `UNAVAILABLE`),
`routingVerified`, nullable `verificationUrl`, safe `reason` and optional local
`notice`, `runtimeEnabled`, `routingMethod` (`SYSTEM_PROXY` or `BRAVE`),
`braveAvailable` and `braveLaunchPending`. `VERIFIED` proves a browser requested the local challenge through the
proxy; it does not prove every browser honors system settings or that a site
was blocked. Verify each browser separately.

Authenticated `PATCH /api/v1/website-protection` accepts
`{"enabled":true,"consent":"FORWARD_SYSTEM_PROXY_TRAFFIC"}` or `{"enabled":false}`.
PATCH optionally includes `routingMethod`; omission preserves the saved method.
Application consent cannot enable website forwarding. Missing consent, wrong
types and unknown fields return `400`. Setup/permission failures return the
actual unavailable/restoring status rather than a successful-protection claim.
Standalone backend mode does not apply native settings. Export includes
`websiteProtectionPreferences` and `website-protection-preferences.csv`;
delete-all resets website consent. See [ADR-011](../decisions/ADR-011-WebsiteProtection.md).

Authenticated `POST /api/v1/website-protection/brave` accepts exactly
`{"confirmation":"OPEN_EXISTING_BRAVE_PROFILE"}` and returns `202` with the
current status. Consent, enabled runtime, native Linux Brave and ready `BRAVE`
routing are required; invalid bodies or unavailable setup return `400`.
The learner has 60 seconds to close all of their Brave windows, including a
browser-hosted development UI. The backend then launches the installed native
Brave with fixed PAC/verification arguments and the existing default profile.
No arbitrary executable, command, URL or profile path can be supplied. Pending
launches expire or cancel on disabling/changing routing; nothing is force-closed.
Runtime status reports timeout or startup failure without claiming verification.
The Brave-only PAC has tested DIRECT fallback for proxy shutdown/crash; intentional
HTTP/HTTPS denials remain blocked. Other browsers are outside this routing method.

The proxy has its own ephemeral loopback port and serves a random PAC path and
local browser verification challenge. It does not expose the authenticated API,
accept origin-form API requests or store traffic payloads. Its outbound sockets
exist only for requests from browsers and other apps using temporary system
proxy settings under the approved FST-026 exception.

## 6. Distraction log (Phase 2 read model)

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/distractions?sessionId=...&from=...&to=...&page=0&size=20` | Query the distraction log read model. | `200 OK` + `Page<DistractionDto>` |

FST-037 returns `{items,total,page,size}`. Each `DistractionLogDto` contains
`detectionId`, `sessionId`, `ruleId`, configured `target`, `observationType`,
nullable observed `application` or `host`, `category`, `action`, detection
`occurredAt`, `outcome` (`PENDING`, `BLOCKED`, `FAILED`), nullable `outcomeAt`,
`blocked` and nullable `failureReason`. Only a correlated confirmed success
yields `blocked=true`; a detection alone cannot claim enforcement. The UI labels
LOG/WARN as logged/warning rather than implying a pending block.

Session and date filters combine. Dates use the installation's local timezone
and include both boundary days; either bound may be omitted. Reject inverted
ranges, invalid UUIDs, negative pages and sizes outside 1–100 with `400`.
Order by detection time descending, then detection UUID ascending. Defaults are
page 0 and size 20. Missing sessions yield an empty filtered result; the query
never performs enforcement. Rules can be removed without changing snapshots.

The append-only ledger deduplicates event UUIDs and retains outcome-first
delivery without inventing a detection. Invalid fact/chronology correlations
cannot produce confirmed blocks. Projection and source delivery commit together;
the worker handles only distraction event types. JSON export adds `distractions`,
and the ZIP adds `distractions.csv`. History deletion, retention and delete-all
remove associated logs and ledger rows. Website hosts suffice for matching and
review; full URLs, credentials, native handles and page contents are excluded.

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

Distraction event types are projected independently by FST-037 and are not
published on the session WebSocket. The distraction-history UI queries REST.

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

Website forwarding consent covers browsers and other apps that use temporary
system proxy settings. Browser-only consent tokens are rejected. Migration V9
disables old website preferences and clears their consent timestamp; saved rules
and application protection are retained. No autonomous outbound requests, HTTPS
decryption or telemetry are authorized.
