# Use Case Catalogue and Specifications

This document describes the behaviour of FocusDesktop from the actor's point of
view. It complements the [use case diagram](UseCases.svg) and the
[sequence diagrams](../SD/SequenceDiagrams.md).

## 1. Actors

| Actor | Type | Description |
|---|---|---|
| **Learner** | Primary | Student, developer or professional using the application. Starts, pauses, resumes and stops sessions, reviews history and configures the app. |
| **Operating System** | Secondary | Supplies the active window/application and enforces blocking requests (Phase 2). Also provides screen-capture capability (Phase 3). |
| **Timer / Scheduler** | Secondary | Internal scheduler that triggers interval completion while a session is running. It is modelled as an actor because it initiates UC-05 without a learner action. |
| **Local Database** | Supporting | Persists aggregates, projections and settings. Not an actor in the use case sense. |
| **Desktop UI** | System boundary | Renders the experience and calls the local API. It is part of the system, not an external actor. |

## 2. Use case catalogue

| ID | Name | Primary actor | Priority | Phase | Diagram |
|---|---|---|---|---|---|
| UC-01 | Start focus session | Learner | Must | MVP | [SD](../SD/SD_StartSession.puml) |
| UC-02 | Pause session | Learner | Must | MVP | [SD](../SD/SD_PauseSession.puml) |
| UC-03 | Resume session | Learner | Must | MVP | [SD](../SD/SD_ResumeSession.puml) |
| UC-04 | Stop session | Learner | Must | MVP | [SD](../SD/SD_StopSession.puml) |
| UC-05 | Complete interval | Timer / Scheduler and Learner | Must | MVP | [SD](../SD/SD_CompleteInterval.puml), [start pending](../SD/SD_StartNextInterval.puml) |
| UC-06 | Skip break | Learner | Must | MVP | [SD](../SD/SD_SkipBreak.puml) |
| UC-07 | View session history | Learner | Must | MVP | [SD](../SD/SD_QuerySessionHistory.puml) |
| UC-08 | View daily streak | Learner | Must | MVP | [SD](../SD/SD_CalculateDailyStreak.puml) |
| UC-09 | View productivity analytics | Learner | Must | MVP | [SD](../SD/SD_ViewAnalytics.puml) |
| UC-10 | Configure timer settings | Learner | Must | MVP | [SD](../SD/SD_ConfigureTimerSettings.puml) |
| UC-11 | Configure application settings | Learner | Should | MVP | [SD](../SD/SD_ConfigureApplicationSettings.puml) |
| UC-12 | Manage blocking rules | Learner | Should | Phase 2 | [SD](../SD/SD_ManageBlockingRules.puml) |
| UC-13 | Detect distracting application | Operating System | Should | Phase 2 | [SD](../SD/SD_DetectDistraction.puml) |
| UC-14 | Block distracting application | Operating System | Should | Phase 2 | [SD](../SD/SD_BlockDistraction.puml) |
| UC-15 | Review distraction log | Learner | Should | Phase 2 | [SD](../SD/SD_ReviewDistractionLog.puml) |
| UC-16 | Capture session screenshot | Learner / OS | Could | Phase 3 | — |
| UC-17 | Recognize study content | Operating System | Could | Phase 3 | — |
| UC-18 | Categorize session automatically | Operating System | Could | Phase 3 | — |

## 3. Cross-cutting business rules

| Rule | Description |
|---|---|
| BR-01 | At most one session may be `RUNNING`, `PAUSED` or `WAITING_FOR_NEXT_INTERVAL` per installation. |
| BR-02 | A session snapshots its timer settings at creation time; later settings changes do not rewrite history. |
| BR-03 | Timestamps are stored in UTC. Streak and daily aggregate dates use the operating system's current time zone; changing that zone changes the mapping of instants to local dates, including historical views. |
| BR-04 | Paused time does not count as focus time. |
| BR-05 | Break time is recorded but never counts as focus time or toward a streak. |
| BR-06 | A completed session is immutable. Corrections require a new session or an explicit administrative migration. |
| BR-07 | Domain changes and their outbox events commit atomically. Outbox consumers process after commit and tolerate redelivery. |
| BR-08 | The local API never trusts the UI for elapsed time; the backend computes it from timestamps. |
| BR-09 | Errors use a stable code plus a human-readable message. |
| BR-10 | No use case may send personal data outside the workstation. |
| BR-11 | Progress and analytics are read models; projections are idempotent and can be rebuilt from retained sessions and retained outbox events. |
| BR-12 | The distraction log is a read model. The write side records detections and block outcomes as events; it never updates the log directly. |

## 4. UC-01 — Start focus session

**Actor:** Learner
**Goal:** Begin a new focus session with the configured work duration.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running, the local database is available and
there is no active or waiting session.
**Trigger:** The learner presses **Start** on the timer screen.

### Main success scenario

1. The UI loads the timer screen and requests the active session.
2. The system reports that no session is active.
3. The learner chooses the session type (`WORK`) and presses **Start**.
4. The UI sends `POST /api/v1/sessions` with the session type.
5. The system validates the request and checks for an active session.
6. The system loads the current timer settings.
7. The system creates a `FocusSession` in `IDLE`, snapshots the settings and
   starts the first `WORK` interval.
8. The system persists the session and appends `SessionStarted` to the outbox
   in the same transaction.
9. The system returns the session DTO; after commit the dispatcher broadcasts
   `session.started`.
10. The UI displays the work interval and starts a local countdown from the
    backend snapshot; it periodically reconciles with backend snapshots.

### Extensions

- **2a. An active session exists.** The system returns
  `409 ACTIVE_SESSION_EXISTS`. The UI offers *Resume* or *Stop* instead of
  creating a second session.
- **4a. Invalid session type.** The system returns `400 VALIDATION_ERROR`.
- **6a. Settings are missing or invalid.** The system falls back to defaults and
  logs a warning; the session still starts with valid defaults.
- **8a. Persistence fails.** The system returns `500 PERSISTENCE_ERROR` and no
  session is considered started.

### Postconditions

- Exactly one `RUNNING` session exists.
- The first work interval is `RUNNING`.
- A `SessionStarted` outbox record commits atomically with the session; it is
  dispatched to projections and WebSocket after commit.
- The UI shows the correct remaining time.

### Business rules

BR-01, BR-02, BR-03, BR-08, BR-09.

### Related requirements and diagrams

FR-SES-01, FR-SES-07, FR-SES-08, FR-SES-09, FR-SES-10.
[Sequence diagram](../SD/SD_StartSession.puml),
[SSD](../SSD/SSD_StartSession.puml),
[domain model](../DM/DomainModel.md).

## 5. UC-02 — Pause session

**Actor:** Learner
**Goal:** Temporarily stop the clock without ending the session.
**Priority / phase:** Must / MVP
**Preconditions:** A session exists and is `RUNNING`.
**Trigger:** The learner presses **Pause**.

### Main success scenario

1. The learner presses **Pause**.
2. The UI sends `POST /api/v1/sessions/{id}/pause`.
3. The system loads the session.
4. The system verifies the session is `RUNNING`.
5. The aggregate pauses the active interval and records the pause timestamp.
6. The system persists the session and appends `SessionPaused` to the outbox in
   the same transaction.
7. The system returns the updated DTO; after commit the dispatcher broadcasts
   `session.paused`.
8. The UI freezes the countdown.

### Extensions

- **3a. Session not found.** `404 SESSION_NOT_FOUND`.
- **4a. Session is not `RUNNING`.** `422 INVALID_SESSION_STATE`.
- **6a. Persistence fails.** The pause is rolled back; the UI keeps running.

### Postconditions

- Session state is `PAUSED`.
- Remaining time is preserved.
- Paused time is excluded from focus duration.

### Business rules

BR-01, BR-03, BR-04, BR-08.

### Related requirements and diagrams

FR-SES-02.
[Sequence diagram](../SD/SD_PauseSession.puml),
[SSD](../SSD/SSD_PauseResumeSession.puml).

## 6. UC-03 — Resume session

**Actor:** Learner
**Goal:** Continue a paused session from the same point.
**Priority / phase:** Must / MVP
**Preconditions:** A session exists and is `PAUSED`.
**Trigger:** The learner presses **Resume**.

### Main success scenario

1. The learner presses **Resume**.
2. The UI sends `POST /api/v1/sessions/{id}/resume`.
3. The system loads the session.
4. The system verifies the session is `PAUSED`.
5. The aggregate restarts the same interval using its persisted remaining time;
   paused duration is not counted.
6. The system persists the session and appends `SessionResumed` to the outbox in
   the same transaction.
7. The system returns the updated DTO; after commit the dispatcher broadcasts
   `session.resumed`.
8. The UI restarts the countdown.

### Extensions

- **3a. Session not found.** `404 SESSION_NOT_FOUND`.
- **4a. Session is not `PAUSED`.** `422 INVALID_SESSION_STATE`.
- **5a. The interval was already completed.** The system rejects the resume with
  `422 INVALID_SESSION_STATE` and suggests stopping the session.

### Postconditions

- Session state is `RUNNING`.
- The same interval continues; no new interval is created.
- Remaining time matches the value before the pause.

### Business rules

BR-01, BR-03, BR-04, BR-08.

### Related requirements and diagrams

FR-SES-03.
[Sequence diagram](../SD/SD_ResumeSession.puml),
[SSD](../SSD/SSD_PauseResumeSession.puml).

## 7. UC-04 — Stop session

**Actor:** Learner
**Goal:** End the current session and record the actual focus time.
**Priority / phase:** Must / MVP
**Preconditions:** A session exists and is `RUNNING`, `PAUSED` or
`WAITING_FOR_NEXT_INTERVAL`.
**Trigger:** The learner presses **Stop** and confirms.

### Main success scenario

1. The learner presses **Stop**.
2. The UI asks for confirmation.
3. The learner confirms.
4. The UI sends `POST /api/v1/sessions/{id}/stop`.
5. The system loads the session.
6. If an interval is running, the system closes it at the stop time. If paused,
   it closes it using the elapsed time recorded at pause. If the session is
   waiting, there is no active interval to close. Focus duration is calculated
   from completed work intervals only.
7. The system marks the session `COMPLETED`, or `ABANDONED` when the focus time
   is below the configured minimum.
8. The system persists the session and appends `SessionCompleted` or
   `SessionAbandoned` to the outbox in the same transaction.
9. After commit, the daily-progress projection updates the read model
   idempotently.
10. The system returns a summary and broadcasts `session.completed` or
    `session.abandoned`, matching the persisted terminal state.

### Extensions

- **5a. Session not found.** `404 SESSION_NOT_FOUND`.
- **5b. Session already finished.** `422 INVALID_SESSION_STATE`.
- **7a. Focus time is below the minimum.** The session is `ABANDONED`; it is
  stored for diagnostics but excluded from streaks and completed-session totals.
- **8a. Outbox delivery fails after commit.** The terminal session remains
  persisted; the event remains pending and is retried by the dispatcher.

### Postconditions

- No session is active.
- The session and its intervals are persisted.
- The daily progress is updated.
- The UI returns to the idle timer screen.

### Business rules

BR-03, BR-05, BR-06, BR-07, BR-08.

### Related requirements and diagrams

FR-SES-04, FR-SES-11, FR-HIS-01.
[Sequence diagram](../SD/SD_StopSession.puml),
[SSD](../SSD/SSD_StopSession.puml),
[state machine](../StateMachines/FocusSessionState.svg).

## 8. UC-05 — Complete interval

**Actor:** Timer / Scheduler (completion); Learner (start pending interval)
**Goal:** Move from a finished work or break interval to the next interval.
**Priority / phase:** Must / MVP
**Preconditions:** For automatic completion, the session is `RUNNING` and its
current interval is due. For manual start, the session is
`WAITING_FOR_NEXT_INTERVAL` and has a pending interval.
**Trigger:** The scheduler detects a due interval, or the learner chooses to
start the pending interval.

### Main success scenario

1. The backend scheduler invokes `completeInterval(sessionId, now)` internally;
   there is no REST endpoint for this operation.
2. The system loads the session.
3. The system verifies the session is `RUNNING`.
4. The aggregate completes the current interval and records its actual duration.
5. After work, the aggregate chooses a short break or a long break after N
   completed work intervals. After a break, it creates the next work interval.
6. If the corresponding auto-start setting (`autoStartBreaks` or
   `autoStartWork`) is enabled, the new interval starts immediately; otherwise
   it remains `PENDING` and the session enters `WAITING_FOR_NEXT_INTERVAL`.
7. The system persists the session and appends `IntervalCompleted` to the outbox
   in the same transaction.
8. After commit, the dispatcher broadcasts `interval.completed` with the next
   interval type, session state and remaining time.

### Starting a pending interval

1. The learner selects *Start break* or *Start work*.
2. The UI sends `POST /api/v1/sessions/{id}/intervals/start`.
3. The system loads the session and verifies it is
   `WAITING_FOR_NEXT_INTERVAL` with a pending interval.
4. The aggregate starts that interval and changes the session to `RUNNING`.
5. The session and `IntervalStarted` outbox record commit atomically.
6. The response and post-commit `interval.started` notification contain the
   authoritative timer snapshot.

### Extensions

- **2a. Session not found.** The scheduler logs and ignores the tick; a manual
  start returns `404 SESSION_NOT_FOUND`.
- **3a. Session is paused, waiting or finished when a scheduler tick arrives.**
  The tick is ignored; no interval is created.
- **3b. Manual start is requested outside `WAITING_FOR_NEXT_INTERVAL`.** The
  API returns `422 INVALID_SESSION_STATE`.
- **5a. The work limit is reached.** A `LONG_BREAK` is created instead of a
  `SHORT_BREAK`.
- **6a. Auto-start is disabled.** The session enters
  `WAITING_FOR_NEXT_INTERVAL`; the UI shows a *Start break* or *Start work*
  action. The backend does not run a countdown until the learner starts it.

### Postconditions

- The previous interval is `COMPLETED`.
- The appropriate next interval exists (`RUNNING` or `PENDING`).
- The session is `RUNNING` when the next interval starts automatically and
  `WAITING_FOR_NEXT_INTERVAL` otherwise.

### Business rules

BR-03, BR-05, BR-08.

### Related requirements and diagrams

FR-SES-05, FR-SES-09.
[Sequence diagram](../SD/SD_CompleteInterval.puml),
[start pending interval](../SD/SD_StartNextInterval.puml),
[domain model](../DM/DomainModel.md).

## 9. UC-06 — Skip break

**Actor:** Learner
**Goal:** End a break early and return to work.
**Priority / phase:** Must / MVP
**Preconditions:** A session exists, is `RUNNING`, and the current interval is
a break.
**Trigger:** The learner presses **Skip break**.

### Main success scenario

1. The learner presses **Skip break**.
2. The UI sends `POST /api/v1/sessions/{id}/skip-break`.
3. The system loads the session.
4. The system verifies the session is `RUNNING` and the current interval is
   `SHORT_BREAK` or `LONG_BREAK`.
5. The aggregate marks the break `SKIPPED`, creates a new work interval and
   starts it.
6. The system persists the session and appends `IntervalCompleted` to the
   outbox in the same transaction.
7. After commit, the dispatcher broadcasts `interval.completed` with
   `nextType: WORK`.
8. The UI switches to the work timer.

### Extensions

- **3a. Session not found.** `404 SESSION_NOT_FOUND`.
- **4a. Current interval is work.** `422 INVALID_INTERVAL_TYPE`.
- **4b. Session is paused.** The system returns `422 INVALID_SESSION_STATE`;
  the learner must resume or stop.

### Postconditions

- The break is `SKIPPED`.
- A new work interval is `RUNNING`.
- Skipped break time is not counted as focus or break time.

### Business rules

BR-03, BR-05, BR-08.

### Related requirements and diagrams

FR-SES-06.
[Sequence diagram](../SD/SD_SkipBreak.puml).

## 10. UC-07 — View session history

**Actor:** Learner
**Goal:** Review past sessions and their details.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **History**.

### Main success scenario

1. The learner opens **History**.
2. The UI requests `GET /api/v1/sessions` with a default range (last 30 days)
   and pagination.
3. The system validates the range.
4. The system queries the read model for session summaries.
5. The system returns a page of summaries with `total`, `page` and `size`.
6. The UI renders the list, newest first.
7. The learner selects a session.
8. The UI requests the session detail and shows intervals and distractions.

### Extensions

- **3a. Invalid range (`from > to`).** `400 INVALID_DATE_RANGE`.
- **3b. Range larger than 366 days.** `400 RANGE_TOO_LARGE`.
- **4a. No sessions in range.** The system returns an empty page, not an error.
- **8a. Session detail not found.** `404 SESSION_NOT_FOUND`.

### Postconditions

- No data is modified.
- The UI shows a paginated, filterable history.

### Business rules

BR-03, BR-06.

### Related requirements and diagrams

FR-HIS-02, FR-HIS-03, FR-HIS-04, FR-HIS-05.
[Sequence diagram](../SD/SD_QuerySessionHistory.puml),
[SSD](../SSD/SSD_HistoryAndStreak.puml).

## 11. UC-08 — View daily streak

**Actor:** Learner
**Goal:** See the current and longest streak.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens the dashboard or the history screen.

### Main success scenario

1. The UI requests `GET /api/v1/analytics/streak?date=<today>`.
2. The system loads daily progress for the last 365 days.
3. The streak calculator sorts the days and counts consecutive qualifying days.
4. The system returns `current` and `longest`.
5. The UI displays the streak and the next milestone.

### Extensions

- **2a. No progress exists.** The system returns `current = 0`, `longest = 0`.
- **2b. The current day has no session yet.** The day is treated as open and does
  not break the streak.
- **2c. The progress read model is stale.** The system can rebuild it from
  persisted sessions on startup or on demand.

### Postconditions

- No data is modified.
- The streak values are consistent with the qualifying-day rule.

### Business rules

- A qualifying day has at least one completed `WORK` interval of ≥ 5 minutes.
- A break-only day does not qualify.
- Abandoned sessions do not qualify.
- A full missed day resets the current streak but not the longest.

### Related requirements and diagrams

FR-STR-01..04.
[Sequence diagram](../SD/SD_CalculateDailyStreak.puml),
[SSD](../SSD/SSD_HistoryAndStreak.puml),
[domain model](../DM/DomainModel.md).

## 12. UC-09 — View productivity analytics

**Actor:** Learner
**Goal:** Understand focus time, session volume, focus score and distractions
over a date range.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **Analytics** and selects a range.

### Main success scenario

1. The learner selects a range (default: last 7 days).
2. The UI requests `GET /api/v1/analytics/summary`.
3. The system validates the range (≤ 366 days).
4. The system loads session summaries, daily progress and distraction counts from the read models.
5. The analytics assembler computes totals, average session and focus score.
6. The system returns the `ProductivitySummary` read model and the daily breakdown.
7. The UI renders charts and key figures.

### Extensions

- **3a. Invalid range.** `400 INVALID_DATE_RANGE`.
- **3b. Range too large.** `400 RANGE_TOO_LARGE`.
- **4a. No data.** The system returns zero totals and a zero-filled daily
  breakdown.
- **5a. Planned time is zero.** The focus score is `0`; the response is still
  valid.

### Postconditions

- No data is modified.
- The summary is reproducible from persisted sessions and the `DailyProgress` / `DistractionLog` read models.

### Business rules

- Focus score = completed focus time / planned focus time, penalised by
  distraction density; clamped to `[0, 100]`.
- Totals use completed sessions only.
- The daily breakdown includes zero days.
- Distraction counts are included when Phase 2 read-model data exists.
- `ProductivitySummary` is a read model, not a domain aggregate.

### Related requirements and diagrams

FR-ANA-01..05.
[Sequence diagram](../SD/SD_ViewAnalytics.puml),
[SSD](../SSD/SSD_Analytics.puml),
[domain model](../DM/DomainModel.md).

## 13. UC-10 — Configure timer settings

**Actor:** Learner
**Goal:** Change work and break durations and auto-start behaviour.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **Settings → Timer** and saves.

### Main success scenario

1. The learner edits work duration, short break, long break, intervals before a
   long break, and auto-start options.
2. The UI sends `PUT /api/v1/settings/timer`.
3. The system validates every field.
4. The system loads `AppSettings` and updates the timer settings.
5. The system persists the settings.
6. The system returns the updated DTO.
7. The UI confirms the change and shows the new defaults.

### Extensions

- **3a. Invalid field.** `422 VALIDATION_ERROR` with field-level details.
- **3b. Long break shorter than short break.** `422 VALIDATION_ERROR`.
- **5a. Persistence fails.** The previous settings remain in effect.
- **6a. A session is active.** The change is accepted for future sessions; the
  active session keeps its snapshot.

### Postconditions

- The new settings are persisted.
- New sessions use the new values.
- Active sessions are unchanged.

### Business rules

- Work: 1..180 minutes.
- Short break: 1..60 minutes.
- Long break: 1..120 minutes and ≥ short break.
- Intervals before long break: 1..12.
- Auto-start flags are independent booleans.

### Related requirements and diagrams

FR-SET-01, FR-SET-02, FR-SET-03, FR-SET-05, FR-SET-06.
[Sequence diagram](../SD/SD_ConfigureTimerSettings.puml),
[SSD](../SSD/SSD_Settings.puml).

## 14. UC-11 — Configure application settings

**Actor:** Learner
**Goal:** Change theme, notifications, retention and data controls.
**Priority / phase:** Should / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **Settings → Application**.

### Main success scenario

1. The UI requests `GET /api/v1/settings`.
2. The system returns timer, theme, notification and retention settings.
3. The learner changes theme, notifications or retention.
4. The UI sends `PUT /api/v1/settings/application`.
5. The system validates and persists the changes.
6. The system returns the updated settings.
7. The UI applies the theme immediately.

### Extensions

- **5a. Invalid retention.** `422 VALIDATION_ERROR`.
- **5b. Unsupported theme.** `422 VALIDATION_ERROR`.
- **5c. The learner requests deletion of all data.** The system requires an
  explicit confirmation, then deletes sessions, intervals, distractions,
  progress and settings, recreates defaults, and returns `204 No Content`.
- **5d. Retention cleanup is triggered.** The system deletes data older than the
  configured retention, never the active session.

### Postconditions

- Settings are persisted and applied.
- Deletion is irreversible and leaves a clean default state.

### Business rules

- Retention: 7..3650 days or "keep forever".
- Deletion requires a confirmation token in the request body.
- Cleanup runs on startup and daily while the app is open.

### Related requirements and diagrams

FR-SET-04, FR-PRV-04, FR-PRV-05.
[Sequence diagram](../SD/SD_ConfigureApplicationSettings.puml),
[SSD](../SSD/SSD_Settings.puml).

## 15. UC-12 — Manage blocking rules (Phase 2)

**Actor:** Learner
**Goal:** Define which applications or windows should be logged, warned or
blocked during focus sessions.
**Priority / phase:** Should / Phase 2
**Preconditions:** The backend is running and the active blocking policy exists.
**Trigger:** The learner opens **Settings → Focus protection**.

### Main success scenario

1. The UI requests the active policy and its rules.
2. The learner adds a rule: pattern, action (`LOG`, `WARN`, `BLOCK`) and category.
3. The system validates the pattern and checks for duplicates.
4. The system adds the rule to the active `BlockingPolicy`.
5. The system persists the policy.
6. The system returns the created rule.
7. The learner can disable, enable or delete rules.

### Extensions

- **3a. Empty or invalid pattern.** `422 VALIDATION_ERROR`.
- **3b. Duplicate pattern.** `409 DUPLICATE_RULE`.
- **5a. The policy is not found.** The system creates a default policy.
- **7a. Deleting the last rule.** The policy remains active with zero rules and
  detects nothing.

### Postconditions

- The active policy contains the new rule set.
- The next monitoring tick uses the new rules.

### Business rules

- Rule precedence: exact executable path → process name → window-title pattern.
- First matching enabled rule wins.
- Only one policy is active in the MVP.
- Patterns are case-insensitive for process names and titles.

### Related requirements and diagrams

FR-DIS-01, FR-DIS-02.
[Sequence diagram](../SD/SD_ManageBlockingRules.puml),
[SSD](../SSD/SSD_BlockingRules.puml).

## 16. UC-13 — Detect distracting application (Phase 2)

**Actor:** Operating System
**Goal:** Detect when the active window matches a blocking rule.
**Priority / phase:** Should / Phase 2
**Preconditions:** A session is `RUNNING` and an active policy exists.
**Trigger:** The monitoring scheduler tick (default every 2 seconds).

### Main success scenario

1. The scheduler asks the active-window port for the current window.
2. The system loads the active blocking policy.
3. The policy service evaluates the window against enabled rules.
4. If no rule matches, the system ignores the window.
5. If a rule matches, the system assigns a `detectionId` and publishes
   `DistractionDetected`.
6. The `DistractionLogProjection` and `DailyProgressProjection` update the
   analytics read models.
7. The system broadcasts `distraction.detected`.
8. If the action is `BLOCK`, the system invokes UC-14.

### Extensions

- **1a. The OS denies access.** The system logs a capability warning and
  disables monitoring for the session; the timer keeps working.
- **2a. No active policy.** The system ignores the window and writes no log.
- **3a. The session is paused or on a break.** Monitoring is suspended.
- **5a. The same window is still active on the next tick.** The system applies a
  debounce window (default 30 seconds) to avoid log spam.
- **8a. Blocking fails.** UC-14 publishes `DistractionBlockFailed`; the
  projection records the failure and the learner is notified.

### Postconditions

- A `DistractionLog` read-model entry exists for each distinct detection.
- The learner is warned or the app is blocked according to the rule.
- No data leaves the workstation.

### Business rules

- Monitoring only while the session is `RUNNING`.
- Debounce identical `(session, app, title)` detections for 30 seconds.
- A log entry is append-only and is a read model, not a domain aggregate.
- The policy evaluation is read-only.
- The write side emits events; it never updates the log projection directly.

### Related requirements and diagrams

FR-DIS-03, FR-DIS-04, FR-DIS-06, FR-DIS-08.
[Sequence diagram](../SD/SD_DetectDistraction.puml),
[domain model](../DM/DomainModel.md),
[persistence model](../data/Persistence.md).

## 17. UC-14 — Block distracting application (Phase 2)

**Actor:** Operating System
**Goal:** Enforce a `BLOCK` decision.
**Priority / phase:** Should / Phase 2
**Preconditions:** A detection with action `BLOCK` exists in the current
monitoring flow.
**Trigger:** UC-13 invokes the block use case.

### Main success scenario

1. The system calls the platform blocker adapter with the app identifier.
2. The adapter requests the OS to suspend, hide or close the application.
3. The OS confirms the operation.
4. The system publishes `DistractionBlocked`; the log projection marks the
   read-model entry as blocked.
5. The system broadcasts `distraction.blocked`.
6. The UI shows a short confirmation.

### Extensions

- **2a. The application already exited.** The adapter returns success.
- **3a. The OS denies the operation.** The system publishes
  `DistractionBlockFailed`; the projection marks the entry as failed with a
  reason and the use case returns `blocked: false`.
- **3b. The adapter is unavailable.** The system falls back to a warning and
  records the capability limitation through the same failure event.

### Postconditions

- The `DistractionLog` read model reflects the outcome.
- The learner is informed of success or failure.
- A failed block never crashes the backend.

### Business rules

- Blocking is best-effort and platform-dependent.
- The app never escalates privileges silently.
- A failed block is never silent.
- The write side publishes the outcome; it never updates the log projection
  directly.

### Related requirements and diagrams

FR-DIS-05, FR-DIS-09.
[Sequence diagram](../SD/SD_BlockDistraction.puml),
[domain model](../DM/DomainModel.md).

## 18. UC-15 — Review distraction log (Phase 2)

**Actor:** Learner
**Goal:** Understand which applications interrupted focus and what the system did.
**Priority / phase:** Should / Phase 2
**Preconditions:** Distraction log read-model entries exist.
**Trigger:** The learner opens a session detail or **Analytics → Distractions**.

### Main success scenario

1. The learner opens a session or selects a date range.
2. The UI requests `GET /api/v1/distractions`.
3. The system validates the filter.
4. The system queries the `DistractionLog` read model and returns entries with
   app, title, category, action, blocked status and timestamp.
5. The UI groups entries by category and shows counts.
6. The learner can filter by category or action.

### Extensions

- **3a. Invalid session or range.** `400` or `404`.
- **4a. No logs.** The system returns an empty list.
- **5a. A log references a deleted session.** The entry remains visible with a
  "session deleted" marker.

### Postconditions

- No domain data is modified; only the read model is queried.
- The learner can correlate distractions with focus sessions.

### Business rules

- `DistractionLog` is an append-only read model.
- Logs are retained according to the retention policy.
- Window titles are stored only when the rule category requires it; otherwise
  they are redacted.

### Related requirements and diagrams

FR-DIS-06, FR-DIS-07, FR-ANA-04.
[Sequence diagram](../SD/SD_ReviewDistractionLog.puml),
[domain model](../DM/DomainModel.md).

## 19. UC-16 — Capture session screenshot (Phase 3)

**Actor:** Learner / Operating System
**Goal:** Optionally capture periodic local screenshots during a session for
later review.
**Priority / phase:** Could / Phase 3
**Preconditions:** The learner explicitly enables capture; OS permission is
granted.
**Trigger:** Scheduler tick during a running session.

### Main scenario (outline)

1. The learner enables screen capture and chooses an interval.
2. The system captures a frame locally.
3. The frame is stored in the local data directory with a session reference.
4. The UI indicates that capture is active.
5. The learner can disable capture at any time.

### Extensions

- Permission denied: capture is disabled and the learner is informed.
- Disk full: capture stops and a warning is shown.
- Session paused/stopped: capture stops immediately.

### Business rules

- Opt-in only, off by default.
- Frames never leave the device unless the learner exports them.
- Capture is suspended on breaks.
- Frames follow the global `retention_days` setting and are deleted with their
  session or by delete-all.
- A visible indicator is always shown while capture is active.

### Related requirements

FR-CAP-01, FR-REC-03.

## 20. UC-17 — Recognize study content (Phase 3)

**Actor:** Operating System
**Goal:** Identify the study material visible in captured frames.
**Priority / phase:** Could / Phase 3
**Preconditions:** Captures exist and recognition is enabled.
**Trigger:** A capture is stored or the learner requests analysis.

### Main scenario (outline)

1. The system loads a captured frame.
2. The recognition service extracts text/visual features locally.
3. The service classifies the content into a `ContentCategory`.
4. The system stores a `DetectedContent` with a confidence score.
5. The UI shows the suggested category for the session.

### Extensions

- Low confidence: the system leaves the category as `OTHER` and asks the learner.
- Unsupported format: the frame is skipped and logged.

### Business rules

- Local processing only in the MVP of this feature.
- Confidence and provenance are stored with the result.
- The learner can correct or delete the classification.

### Related requirements

FR-REC-01, FR-REC-03.

## 21. UC-18 — Categorize session automatically (Phase 3)

**Actor:** Operating System
**Goal:** Assign a category to a session from recognized content and app usage.
**Priority / phase:** Could / Phase 3
**Preconditions:** Recognized content or distraction data exists.
**Trigger:** Session completion or manual refresh.

### Main scenario (outline)

1. The system loads recognized content for the session.
2. The categorization service selects the dominant category.
3. The system stores the category on the session summary.
4. The UI shows the category and allows correction.

### Extensions

- No recognized content: the session remains `UNCATEGORIZED`.
- Conflicting categories: the highest-confidence category wins; ties fall back to
  `OTHER`.

### Business rules

- Automatic categories are suggestions, not facts.
- The learner can override any category.
- Categories never affect streak or focus-score calculations.

### Related requirements

FR-REC-02, FR-REC-03.

## 22. Open questions

| Question | Current position |
|---|---|
| Should the timer continue while the computer sleeps? | Compute from timestamps; on resume, show the elapsed state and let the learner decide to continue or stop. |
| Should breaks count toward the daily streak? | No; only completed work intervals of ≥ 5 minutes qualify. |
| Should the MVP support multiple blocking profiles? | No; one active policy. Named profiles are a Phase 2+ extension. |
| Should the UI be able to start a session offline? | Yes; the backend is local, so "offline" is the normal mode. |
| Should analytics be materialised? | Daily progress is materialised; range analytics is computed on demand. |
| Should sessions be editable? | No; completed sessions are immutable. Corrections require a new session or a migration. |

## 23. Related documents

- [Use case diagram](UseCases.svg)
- [Sequence diagrams](../SD/SequenceDiagrams.md)
- [System sequence diagrams](../SSD/SystemSequenceDiagrams.md)
- [Functional requirements](../requirements/FunctionalRequirements.md)
- [Domain model](../DM/DomainModel.md)
- [Glossary](../glossary.md)
