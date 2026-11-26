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
| UC-10 | Configure timer profiles and settings | Learner | Must | MVP | [SD](../SD/SD_ConfigureTimerSettings.puml) |
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
3. The learner enters a project name and presses **Start**; session type is
   `WORK`.
4. The UI sends `POST /api/v1/sessions` with `sessionType` and `projectName`.
5. The system trims and validates the project name (1–120 characters), then
   checks for an active session.
6. The system loads the current timer settings.
7. `FocusSessionFactory` creates a `FocusSession` in `IDLE` with a settings
   snapshot; the aggregate starts its first `WORK` interval.
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
- **4b. Missing, blank or overlength project name.** The system returns
  `400 VALIDATION_ERROR`; the UI keeps the entered value available for editing.
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
   from work intervals only; break time is excluded.
7. The system marks the session `COMPLETED`, or `ABANDONED` when the focus time
   is below five minutes. Exactly five minutes is sufficient for `COMPLETED`.
8. The system persists the session and appends `SessionCompleted` or
   `SessionAbandoned` to the outbox in the same transaction.
9. After commit, the daily-progress projection updates the read model
   idempotently.
10. The system returns a summary and broadcasts `session.completed` or
    `session.abandoned`, matching the persisted terminal state.

### Extensions

- **5a. Session not found.** `404 SESSION_NOT_FOUND`.
- **5b. Session already finished.** `422 INVALID_SESSION_STATE`.
- **7a. Focus time is below five minutes.** The session is `ABANDONED`; it is
  stored for diagnostics but excluded from streaks and completed-session totals;
  the event reason is `BELOW_MINIMUM_FOCUS_TIME`.
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

1. The backend scheduler checks for due intervals every five seconds and invokes
   `CompleteDueIntervalsUseCase`; there is no REST endpoint for completion.
2. The system loads the session.
3. The system verifies the session is `RUNNING`.
4. The aggregate completes the current interval at its planned end time and
   records its actual duration, even if the scheduler detects it a few seconds
   later.
5. After work, the scheduling service chooses a short break, or a long break
   after N completed work intervals. Short breaks do not count toward N; after
   the long break, the work-interval count starts a new cycle. After a break,
   it chooses the next work interval.
6. If the corresponding auto-start setting (`autoStartBreaks` or
   `autoStartWork`) is enabled, the new interval starts immediately; otherwise
   it remains `PENDING` and the session enters `WAITING_FOR_NEXT_INTERVAL`.
7. The system persists the session and appends `IntervalCompleted` and, when the
   next interval auto-starts, `IntervalStarted` to the outbox in the same
   transaction.
8. After commit, the dispatcher broadcasts `interval.completed` with the next
   interval type, session state and remaining time.
9. The desktop UI handles each unique `interval.completed` event once. Before
   applying its session snapshot, it reads the interval type currently shown
   and asks the native notification service to display a work-complete or
   break-complete message when notifications are enabled and OS permission is
   granted. It does not send a second notification for `session.completed`.

### Starting a pending interval

1. The learner selects *Start break* or *Start work*.
2. The UI sends `POST /api/v1/sessions/{id}/intervals/start`.
3. The system loads the session and verifies it is
   `WAITING_FOR_NEXT_INTERVAL` with a pending interval.
4. `StartNextIntervalUseCase` invokes the aggregate to start that interval and
   changes the session to `RUNNING`.
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
- **5a. The configured number of work intervals is reached.** A `LONG_BREAK`
  is created instead of a `SHORT_BREAK`.
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

FR-SES-05, FR-SES-09, FR-SES-12.
[Sequence diagram](../SD/SD_CompleteInterval.puml),
[start pending interval](../SD/SD_StartNextInterval.puml),
[domain model](../DM/DomainModel.md).

## 9. UC-06 — Skip break

**Actor:** Learner
**Goal:** End a break early and return to work.
**Priority / phase:** Must / MVP
**Preconditions:** A session exists and either is
`WAITING_FOR_NEXT_INTERVAL` with a pending break, or is `PAUSED` with the
current interval paused during a break.
**Trigger:** The learner presses **Skip break**.

### Main success scenario

1. The learner presses **Skip break**.
2. The UI sends `POST /api/v1/sessions/{id}/skip-break`.
3. `SkipBreakUseCase` loads the session.
4. The system verifies the session is waiting with a pending break or paused
   during a break; a work interval cannot be skipped.
5. The aggregate marks the break `SKIPPED`, preserves any break time already
   elapsed before the pause, discards its remaining time, creates a new work
   interval and starts it.
6. The system persists the session and appends `BreakSkipped` and
   `IntervalStarted` to the outbox in the same transaction.
7. After commit, the dispatcher can broadcast the interval change.
8. The UI switches to the work timer.

### Extensions

- **3a. Session not found.** `404 SESSION_NOT_FOUND`.
- **4a. Session is neither waiting with a pending break nor paused during a
  break.**
  `422 INVALID_SESSION_STATE`.
- **4b. The current interval is work.** `422 INVALID_INTERVAL_TYPE`.

### Postconditions

- The break is `SKIPPED`.
- A new work interval is `RUNNING`.
- The skipped interval remains `SKIPPED`; any elapsed break time before pausing
  is retained, while the unused duration is discarded.
- A new work interval is running and session state is `RUNNING`.

### Business rules

BR-03, BR-05, BR-08.

### Related requirements and diagrams

FR-SES-06.
[Sequence diagram](../SD/SD_SkipBreak.puml).

## 10. UC-07 — View session history

**Actor:** Learner
**Goal:** Review past focus sessions and their recorded focus durations.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **History**.

### Main success scenario

1. The learner opens **History**; the initial range is today and the previous
   6 local calendar dates (7 days total).
2. The learner may choose 30 days or all time and may filter by terminal
   status. The learner may also choose an inclusive custom local-date range of
   up to 366 days. Session type filtering is omitted because focus sessions
   currently always have type `WORK`.
3. The UI requests `GET /api/v1/sessions` with the selected filters and page.
   All time omits `from` and `to`.
4. The system validates dates, filters and pagination.
5. The system queries a summary projection and returns a page with `total`,
   `page` and `size`.
6. The UI renders project name, date/time, actual focus duration and status,
   newest first.

### Optional CSV export

1. The learner selects **Export CSV**.
2. The UI retrieves every history page using the current date and status
   filters, then serializes project, start time, focus duration and status.
3. In the desktop app, Tauri opens a native save dialog with
   `focus-history.csv` as the suggested filename and writes the CSV to the
   selected path. Cancelling the dialog writes no file. Browser preview keeps
   the browser download behavior.

### Extensions

- **4a. Invalid range (`from > to`).** `400 INVALID_DATE_RANGE`.
- **4b. Invalid pagination or filter.** `400 VALIDATION_ERROR`.
- **5a. No sessions match.** The system returns an empty page, not an error.

### Postconditions

- Session and application data are not modified; CSV export only writes the
  learner-selected file.
- The UI shows a paginated, filterable history. Date bounds are inclusive local
  calendar dates; omitting both returns all history. The UI limits its
  selectable range to 366 days because the analytics endpoint has that limit;
  the history endpoint itself has no fixed maximum.
- The 30-day choice likewise includes today and the previous 29 local calendar
  dates. The API converts the inclusive local range to a UTC start instant and
  an exclusive instant at midnight after the end date. A session is assigned
  to the date on which it started, even if it ends on a later date.

### Business rules

BR-03, BR-06.

### Related requirements and diagrams

FR-HIS-02, FR-HIS-03, FR-HIS-05. Session details, streaks and distractions are
deferred to their own stories. The current CSV export contains the filtered
session list only; full export including analytics remains a later requirement.
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
**Goal:** Understand completed focus time, break time, session volume and focus
score for a date range.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens the **Overview** or **History** screen. On
History, the learner can select a range.

### Main success scenario

1. On Overview, the UI requests `GET /api/v1/analytics/summary` for yesterday
   and today, then uses the daily buckets for today's focus time, completed
   sessions and the comparison with yesterday. On History, the learner selects
   a preset (7 days, 30 days or All time) or an inclusive custom local-date
   range. The History default remains the last 7 local calendar days.
2. The History UI requests `GET /api/v1/analytics/summary` using the selected
   date bounds. The same custom bounds filter the session list; the status
   filter applies to the list and CSV export only.
3. The system validates that explicit `from` and `to` bounds are supplied
   together, are inclusive local dates, and span no more than 366 calendar
   days. With both bounds omitted, the request means All time.
4. The system queries completed sessions and their work/break intervals for
   totals and daily data. For All time, totals use all completed sessions and
   the daily breakdown covers the latest 7 local calendar days.
5. The analytics assembler computes totals, average session focus duration,
   best day and focus score.
6. The system returns the summary and zero-filled daily breakdown. Daily
   buckets use each session's `startedAt` local date.
7. On Overview, the UI also loads the saved daily goal and active-session
   snapshot. It counts completed work intervals from that active session and
   adds the current work interval's actual duration only after that interval
   reaches 300 seconds. The live amount updates while that interval runs and
   freezes while paused or on a break.
   History continues to show completed-session analytics only. The focus score
   remains off the UI in this story.

### Extensions

- **3a. Invalid range or only one date bound supplied.** `400 INVALID_DATE_RANGE`.
- **3b. Range too large.** `400 RANGE_TOO_LARGE`.
- **4a. No data.** The system returns zero totals and a zero-filled daily
  breakdown.
- **5a. Planned work time is zero.** The focus score is `0`; the response is
  still valid.

### Postconditions

- No data is modified.
- The summary is reproducible from persisted completed sessions and interval
  data. Distraction metrics are deferred until Phase 2 tracking data exists.

### Business rules

- Focus score = `round(100 × actual work seconds / planned work seconds)` over
  completed sessions, clamped to `[0, 100]`; a zero denominator yields `0`.
  Distraction penalties are deferred until their source data exists.
- The Overview daily goal is a persisted integer from 1 to 24 hours. Its
  progress combines completed-session focus time, completed work intervals
  from the active session, and the current work interval's actual duration
  only once that interval reaches 300 seconds. The live contribution updates
  while the interval runs and pauses during breaks or when the session is
  paused. Analytics totals remain based on completed sessions only.
  Distraction counts are not inferred from session analytics.
- Overview displays daily-goal progress, but does not add a focus-score or
  distraction widget in this story.
- Totals use completed sessions only.
- The daily breakdown includes zero days. For All time it contains only the
  latest 7 local calendar days; explicit ranges contain every date in range.
- Focus and break totals use recorded work and break interval durations from
  completed sessions. Planned work time is the sum of planned durations of
  their started work intervals; unstarted pending work intervals are excluded.
- Average session focus duration is total focus seconds divided by completed
  session count; an empty range yields `0`. Best day is computed over the full
  selected range, including All time. When multiple days tie, the most recent
  date is selected.
- `ProductivitySummary` is a read model, not a domain aggregate.

### Related requirements and diagrams

FR-ANA-01..05.
[Sequence diagram](../SD/SD_ViewAnalytics.puml),
[SSD](../SSD/SSD_Analytics.puml),
[domain model](../DM/DomainModel.md).

## 13. UC-10 — Configure timer profiles and settings

**Actor:** Learner
**Goal:** Manage saved timer profiles and select the profile used for new
sessions.
**Priority / phase:** Must / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **Settings → Timer profiles**.

### Main success scenario

1. The UI requests `GET /api/v1/settings` and displays the saved profiles and
   selected default.
2. The learner creates a profile, edits any profile (including a starter
   profile), or selects a profile as the default.
3. The UI sends the corresponding profile create, update or default-selection
   request.
4. The system validates the profile name and all timer preferences, updates
   the `AppSettings` aggregate, and persists it.
5. The system returns the updated settings and profile list.
6. The learner may delete any profile after selecting a replacement when the
   profile being removed is the current default.

### Extensions

- **4a. Invalid field or duplicate profile name.** `422 VALIDATION_ERROR` with
  field-level details.
- **4b. Long break shorter than short break.** `422 VALIDATION_ERROR`.
- **4c. The learner tries to remove the last profile or the default without
  first selecting a replacement.** The request is rejected and no data changes.
- **4d. Persistence fails.** The previous settings remain in effect.
- **5a. A session is active.** The change is accepted for future sessions; the
  active session keeps its snapshot.

### Postconditions

- At least one profile always exists, and exactly one profile is the default.
- Profile names are trimmed and unique without regard to letter case.
- New profiles begin as a copy of the current default profile.
- New sessions use the current default profile's values.
- Active sessions are unchanged.

### Business rules

- Work: 1..180 minutes.
- Short break: 1..60 minutes.
- Long break: 1..120 minutes and ≥ short break.
- Intervals before long break: 1..12.
- Auto-start flags are independent booleans.

### Related requirements and diagrams

FR-SET-01..03, FR-SET-05..06, FR-SET-07.
[Sequence diagram](../SD/SD_ConfigureTimerSettings.puml),
[SSD](../SSD/SSD_Settings.puml).

### One-session timer override from Overview

Overview displays the selected default profile's durations. The learner may
change work, short-break and long-break durations before starting a session.
Each changed duration overrides only that session; unchanged values and the
interval count and independent auto-start settings come from the default
profile. The override is validated using the timer duration rules and is not
saved to the profile. The session stores the resulting immutable settings
snapshot.

## 14. UC-11 — Configure application settings

**Actor:** Learner
**Goal:** Change application preferences and manage local data.
**Priority / phase:** Should / MVP
**Preconditions:** The backend is running.
**Trigger:** The learner opens **Settings**.

### Main success scenario

1. The UI requests `GET /api/v1/settings` and displays the daily focus goal,
   theme, notification and retention preferences.
2. The learner changes one or more preferences; the system validates and persists
   them. The system returns updated settings and the UI applies the theme
   immediately.
3. The learner may open the local data directory or export all settings,
   profiles, sessions and analytics as a JSON file or a ZIP of CSV files.
4. The learner may clear session history; after explicit confirmation the
   system removes completed and abandoned sessions while preserving settings
   and any active session.
5. The learner may request deletion of all personal data; the system requires
   explicit confirmation, stops an active session, deletes all personal data,
   then recreates default settings and starter profiles.

### Notification permission behavior

1. On startup, the UI loads the saved notification preference. Notifications
   are enabled by default. If the preference is enabled, the desktop UI checks
   native OS permission and requests it when needed.
2. If permission is granted, the saved preference remains enabled. If denied,
   the UI saves `notificationsEnabled=false` and shows an explanation in
   Settings.
3. When the learner later enables notifications, the UI checks permission and
   calls the OS permission request again if needed. It saves `true` only after
   permission is granted; if denied, it keeps the setting off. Whether another
   system prompt appears after an earlier denial is controlled by the OS.
4. Turning notifications off saves `false` and suppresses interval notifications
   immediately.

### Extensions

- **2a. Invalid goal or retention.** `422 VALIDATION_ERROR`.
- **2b. Unsupported theme.** `422 VALIDATION_ERROR`.
- **2c. A completed interval event arrives.** If notifications are enabled,
  display one native system notification based on the interval type shown just
  before applying the event snapshot. This applies while the window is focused
  or not focused; OS notification settings may suppress it.
- **4a. The learner cancels history deletion.** No data is changed.
- **5a. The learner cancels deletion of all data.** No data is changed.
- **5b. The learner requests deletion of all data.** The system requires an
  explicit confirmation, then deletes sessions, intervals, distractions,
  progress, profiles, outbox events and settings, recreates defaults, and
  returns `204 No Content`.
- **6a. Retention cleanup is triggered.** The system deletes data older than the
  configured retention, never the active session.

### Postconditions

- Settings are persisted and applied.
- History clearing is irreversible but does not stop the active session or
  delete preferences.
- Delete-all is irreversible and leaves the clean default state.

### Business rules

- Retention: 7..3650 days or "keep forever".
- Deletion requires a confirmation token in the request body.
- Cleanup runs on startup and daily while the app is open.

### Related requirements and diagrams

FR-SET-04, FR-SET-09, FR-PRV-04..08.
[Sequence diagram](../SD/SD_ConfigureApplicationSettings.puml),
[SSD](../SSD/SSD_Settings.puml).

## 15. UC-12 — Manage blocking rules (Phase 2)

**Actor:** Learner
**Goal:** Choose applications and websites to log, warn about or block.
**Priority / phase:** Should / Phase 2
**Preconditions:** The local backend is running.
**Trigger:** The learner opens **Distractions**.

### Main success scenario

1. The UI requests the active policy's rules through the planned blocking API;
   a migration-seeded empty policy returns an empty JSON array.
2. The learner adds a display name, typed application/website target, action
   (`LOG`, `WARN`, `BLOCK`), category and enabled state.
3. The use case locks the singleton policy before loading its current rules;
   the policy validates/canonicalizes the target and rejects duplicates.
4. The policy owns the new rule; the use case persists and commits it atomically,
   preserving concurrent changes and assigning server timestamps.
5. The system returns the rule. The learner can edit, enable/disable or delete it.
6. The next eligible monitoring cycle sees committed changes.

### Extensions and business rules

- Invalid target: `422 VALIDATION_ERROR`; duplicate canonical target:
  `409 DUPLICATE_RULE`. Failed updates preserve saved rules.
- Flyway seeds one empty policy; request-time lazy insertion is not used.
  Deleting the final rule is allowed and keeps the policy row.
- PATCH preserves omitted fields, rejects null/unknown fields and replaces a
  provided target atomically. Unknown rule IDs return `404 RESOURCE_NOT_FOUND`.
- Export includes saved rules; confirmed delete-all clears them; history-only
  cleanup preserves them. Rule writes do not imply successful enforcement.
- One active policy in the initial release. Rule configuration is independent
  from whether a supported detector/blocker is available on this installation.
- Applications use executable path or process name for a declared OS. Websites
  use domain plus subdomains. Title patterns are deferred.
- App paths precede process names; websites select the most specific matching
  domain. Normalization/duplicate defaults are recorded in the
  [approved FST-008 design](../decisions/ADR-006-distraction-model.md).

### Ownership and diagrams

FST-008 defines the domain; FST-048 implements storage/API and PATCH transport
support; FST-049 connects the UI. FR-DIS-01, FR-DIS-02.
[Management contract](../decisions/ADR-007-BlockingRuleManagement.md).
[Sequence](../SD/SD_ManageBlockingRules.puml),
[SSD](../SSD/SSD_BlockingRules.puml).

## 16. UC-13 — Detect distracting application or website (Phase 2)

**Actors:** Operating system / supported local website integration
**Goal:** Identify an observed target and evaluate the active blocking policy.
**Preconditions:** Session `RUNNING`, interval `WORK`, supported adapter.
**Trigger:** Application monitoring tick (planned default: every 2 seconds)
or a website observation from the selected FST-050 integration.

### Main success scenario

1. Check session state and work interval before collecting an observation.
2. Application adapter supplies executable path/process name and transient
   instance context; website adapter supplies the actual host and transient
   page/request context. A browser title is not a website identity.
3. Load the active policy and evaluate only enabled matching rules.
4. If a rule matches, assign a detection ID and commit `DistractionDetected`
   with a snapshot of the configured/observed target and selected decision.
5. After commit, the dispatcher delivers to idempotent log/progress projections
   and WebSocket clients. It does not execute OS/browser block commands.
6. `LOG` requires no enforcement. `WARN` requests learner feedback.
   `BLOCK` invokes UC-14, which rechecks current eligibility.

### Extensions and business rules

- Missing policy, no match, paused/waiting/terminal session or break: do not
  create a session detection or request enforcement.
- Unsupported integration or permission denial: report capability diagnostics;
  the timer continues. Do not invent a detected website from a window title.
- Debounce repeated log detections for 30 seconds per session, rule and observed
  application instance/website host; do not suppress protection of new instances
  or website requests merely because logging is debounced.
- Website host matching includes subdomains at a dot boundary. Do not collect
  URL credentials, query strings or page contents for this matching operation.
- Application and website policy evaluations are independent. A rule to block
  a browser app applies to that app; a site rule does not block the whole browser.

### Ownership and diagrams

FST-027 detects applications; FST-050 supplies website integration; FST-008
provides pure domain evaluation; FST-037 persists the log projection.
FR-DIS-03, FR-DIS-04, FR-DIS-06, FR-DIS-08.
[Application sequence](../SD/SD_DetectDistraction.puml),
[website sequence](../SD/SD_DetectAndBlockWebsite.puml).

## 17. UC-14 — Enforce a distraction rule (Phase 2)

**Actors:** Operating system / supported local website integration
**Goal:** Apply a `BLOCK` decision to the detected app instance or matched site.
**Preconditions:** A committed detection selected `BLOCK`; adapter is available.

### Main success scenario

1. Recheck session `RUNNING`, interval `WORK`, current rule and matching target.
   Revalidate the observed app instance or website context before acting.
2. Invoke the application blocker or website blocker with the target-specific
   transient context. Website blocking must not terminate the whole browser.
3. The adapter confirms success or returns an explicit failure/limitation.
4. Commit `DistractionBlocked` or `DistractionBlockFailed` with the same
   detection ID and a timestamp; failure includes a reason.
5. The dispatcher updates projections and publishes WebSocket feedback after
   commit. The use case never writes the log projection directly.
6. When focus eligibility ends or a rule is disabled/deleted, release temporary
   restrictions introduced by the adapter; do not reverse unrelated OS state.

### Extensions and business rules

- Stale decision: cancel enforcement; do not claim the target was blocked.
- Already-exited app: no enforcement remains necessary; handle safely and do
  not block a replacement process that reused an OS identifier.
- Permission denial/unavailable integration: report a failure, warn the learner
  and keep the timer working. Never silently escalate privileges.
- OS/browser side effects and database commits are not one atomic operation.
  Do not replay an event notification as another block command. If recording an
  outcome fails after an adapter action, surface diagnostics and retain the
  adapter cleanup obligation; do not fabricate a durable success.
- Concrete app operations, website mechanism, platform permissions and cleanup
  guarantees are reviewed in FST-028/FST-050, not selected by FST-008.

### Ownership and diagrams

FST-028 enforces application rules; FST-050 enforces website rules.
FR-DIS-05, FR-DIS-09.
[Application sequence](../SD/SD_BlockDistraction.puml),
[website sequence](../SD/SD_DetectAndBlockWebsite.puml),
[cleanup sequence](../SD/SD_ReleaseDistractionProtection.puml).

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
   configured/observed target, category, action, enforcement outcome and timestamp.
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
- This version records application identifiers or website hosts, not window
  titles, URL credentials/query strings or page contents.

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
