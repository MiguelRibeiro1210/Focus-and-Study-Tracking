# Functional Requirements

Priority and delivery phase are separate dimensions. **Must**, **Should** and
**Could** indicate priority; the Phase column indicates when a requirement is
planned. A Should requirement may be part of the MVP.
Every requirement is traced to a use case and to the project board in
[Traceability](Traceability.md).

## 1. Session lifecycle

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-SES-01 | The learner can start a focus session with a required project name and the configured work duration. | Must | MVP | UC-01 | FST-005, FST-009, FST-012 |
| FR-SES-02 | The learner can pause a running session; the remaining time is preserved. | Must | MVP | UC-02 | FST-010 |
| FR-SES-03 | The learner can resume a paused session without resetting the interval. | Must | MVP | UC-03 | FST-010 |
| FR-SES-04 | The learner can stop an active session; the actual elapsed focus time is recorded. | Must | MVP | UC-04 | FST-010 |
| FR-SES-05 | A running interval completes automatically when its planned duration elapses; the next interval starts immediately when its matching auto-start setting is enabled, otherwise the session waits for learner action. | Must | MVP | UC-05 | FST-006, FST-038 |
| FR-SES-06 | The learner can skip a pending break or a paused break and start the next work interval immediately. | Must | MVP | UC-06 | FST-006, FST-038 |
| FR-SES-07 | The system prevents more than one session in `RUNNING`, `PAUSED` or `WAITING_FOR_NEXT_INTERVAL` per installation. | Must | MVP | UC-01 | FST-005, FST-009 |
| FR-SES-08 | A running, paused or waiting session is recovered after an application restart, including remaining time. | Must | MVP | UC-01 | FST-005, FST-013 |
| FR-SES-09 | The system exposes the current interval type, state and remaining time at any moment. | Must | MVP | UC-01 | FST-006, FST-015 |
| FR-SES-10 | Each session stores a snapshot of the timer settings used to create it. | Must | MVP | UC-01 | FST-005 |
| FR-SES-11 | A stopped session with less than 5 minutes of actual work time is marked abandoned; 5 minutes or more is completed. Paused and break time do not count. | Should | MVP | UC-04 | FST-005, FST-011 |
| FR-SES-12 | When notifications are enabled and OS permission is granted, the desktop app sends one native system notification for each completed work, short-break or long-break interval. | Should | MVP | UC-05 | FST-045 |

### Acceptance criteria — session lifecycle

- A start request requires a project name. The backend trims surrounding
  whitespace, accepts 1–120 characters after trimming, and rejects missing,
  blank or longer names with `400 VALIDATION_ERROR`.
- The normalized project name is immutable for the lifetime of the session and
  is returned in active-session responses and live session snapshots.
- Starting when another session is active or waiting returns `409 ACTIVE_SESSION_EXISTS`
  and does not create a second record.
- Pause/resume on the wrong state returns `422 INVALID_SESSION_STATE`.
- Stop on a completed/abandoned session returns `422`.
- After a restart, the timer screen shows the recovered session and the correct
  remaining time within one second.
- The sum of completed work-interval durations equals the session's recorded
  focus duration (± 1 second).
- Stopping with 299 seconds of actual work marks the session `ABANDONED`; 300
  seconds marks it `COMPLETED`. Break and paused time do not count toward this
  threshold.
- With auto-start disabled, the completed interval is persisted, the session
  enters `WAITING_FOR_NEXT_INTERVAL`, and the next interval starts only after
  `POST /sessions/{id}/intervals/start`.
- Skip-break is allowed for a pending break while waiting or for a break paused
  by the learner; skipping a paused break retains its elapsed break duration,
  discards its remaining time, and starts work immediately.
- The backend sends timer snapshots on state/interval changes. The client may
  count down locally between snapshots and reconciles periodically and after
  reconnecting.

## 2. History

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-HIS-01 | Every completed or abandoned session is persisted with its intervals. | Must | MVP | UC-04 | FST-013 |
| FR-HIS-02 | The learner can query completed and abandoned history by optional local date range. | Must | MVP | UC-07 | FST-012 |
| FR-HIS-03 | History results are paginated and sorted newest first by default. | Must | MVP | UC-07 | FST-012 |
| FR-HIS-04 | The learner can open a session and see its intervals, duration and available distractions. | Should | Phase 2 | UC-07 | FST-043 |
| FR-HIS-05 | The learner can filter history by terminal status. | Could | MVP | UC-07 | FST-012, FST-018 |

### Acceptance criteria — history

- An invalid range (`from > to`) returns `400 INVALID_DATE_RANGE`.
- A range with no data returns an empty page, not an error.
- Pagination metadata (`total`, `page`, `size`) is always present.
- A query without date bounds returns all available history; one bound may be
  supplied without the other. Supplied dates are inclusive in the operating
  system's local timezone and select sessions by their `startedAt` date.
- History contains only `COMPLETED` and `ABANDONED` sessions by default; an
  explicit status filter may select either one. Results are newest first with
  a stable session-ID tie-breaker.
- Each item includes session ID, project name, session type, status, start and
  end timestamps, and actual focus duration in seconds.
- History queries use a summary projection and do not load interval collections.
- The History screen defaults to today and the previous 6 local calendar
  dates, and offers a 30-calendar-day range and all time. FST-019 also provides
  an inclusive custom local-date range that filters both the analytics summary
  and session list. The UI limits its selectable range to 366 days because the
  analytics endpoint has that limit; the history endpoint itself has no fixed
  maximum. Its current CSV export contains filtered session history only;
  complete export support, including analytics, remains a later requirement.
  Session detail, streaks and distraction counts are outside the history-list
  slice.

## 3. Streaks

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-STR-01 | The system calculates a daily streak from days with at least one completed work interval of 5 minutes or more. | Must | MVP | UC-08 | FST-011 |
| FR-STR-02 | The learner can see the current streak and the longest streak. | Must | MVP | UC-08 | FST-018 |
| FR-STR-03 | The current day is treated as open and does not break a streak until the day ends. | Must | MVP | UC-08 | FST-011 |
| FR-STR-04 | Abandoned sessions and skipped breaks do not count toward a streak. | Must | MVP | UC-08 | FST-011 |

### Acceptance criteria — streaks

- A day with only a 4-minute work interval does not count.
- A day with a 5-minute completed work interval counts.
- A gap of one full day resets the current streak but preserves the longest.
- Recomputing the streak from persisted sessions produces the same result as
  the incremental read model.

## 4. Analytics

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-ANA-01 | The learner can retrieve total focus time, break time and completed sessions for a date range. Only completed sessions contribute. | Must | MVP | UC-09 | FST-019 |
| FR-ANA-02 | The system calculates a focus score between 0 and 100 from completed work time and its planned work time. | Must | MVP | UC-09 | FST-019 |
| FR-ANA-03 | The analytics response includes a daily breakdown of focus time, break time and completed sessions, plus the best day in the selected range. | Should | MVP | UC-09 | FST-019 |
| FR-ANA-04 | The learner can see distraction counts and categories when Phase 2 tracking data exists. | Should | Phase 2 | UC-09, UC-15 | FST-028 |
| FR-ANA-05 | Explicit analytics date ranges are limited to 366 inclusive local calendar days; an omitted range requests all-time totals. | Must | MVP | UC-09 | FST-019 |

### Acceptance criteria — analytics

- Focus score is `round(100 × actual work seconds / planned work seconds)`
  across completed sessions, clamped to `[0, 100]`; a zero denominator yields
  `0`. It has no distraction penalty until distraction metrics are implemented.
- An empty range returns zero totals and a valid DTO.
- The daily breakdown has one entry per day in the range, including zero days.
- Totals and the score are computed from completed sessions only. Actual work
  and break time come from their recorded intervals; planned work time is the
  sum of planned durations for started work intervals in those sessions.
- Daily buckets use each session's `startedAt` local date, consistent with the
  session-history date filter. Average session focus time is total focus time
  divided by the completed-session count; an empty range yields `0`. The best
  day is computed across the full selected range; ties select the most recent
  date.
- FST-019 preserves the existing History screen's visual design. Its backend
  returns the focus score, but displaying that score awaits a later UI story
  and the learner's approval of its placement.
- `from` and `to` are either both supplied or both omitted. Supplied bounds
  are inclusive in the operating system's local timezone and may span at most
  366 calendar days. When both are omitted, totals cover all completed
  sessions and the daily breakdown covers the latest 7 local calendar days.

## 5. Settings

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-SET-01 | The learner can configure work, short-break and long-break durations. | Must | MVP | UC-10 | FST-020 |
| FR-SET-02 | The learner can configure the number of work intervals before a long break. | Must | MVP | UC-10 | FST-020 |
| FR-SET-03 | The learner can enable or disable auto-start for breaks and work intervals. | Must | MVP | UC-10 | FST-020 |
| FR-SET-04 | The learner can configure theme, notifications and data retention. | Should | MVP | UC-11 | FST-020, FST-045 |
| FR-SET-05 | Invalid settings are rejected with field-level validation errors. | Must | MVP | UC-10 | FST-020 |
| FR-SET-06 | Settings changes apply to new sessions; active sessions keep their snapshot. | Must | MVP | UC-10 | FST-005 |
| FR-SET-07 | The learner can create, edit, select as default, and delete timer profiles, including the built-in profiles. | Must | MVP | UC-10 | FST-020 |
| FR-SET-08 | The learner can override work, short-break and long-break durations for one session from Overview without changing a saved profile. | Must | MVP | UC-01, UC-10 | FST-017, FST-020 |
| FR-SET-09 | The learner can set a daily focus goal from 1 to 24 whole hours. | Should | MVP | UC-09, UC-11 | FST-020 |
| FR-SET-10 | Overview goal progress includes the active session's current work interval after it reaches five minutes. | Should | MVP | UC-09 | FST-020 |

### Acceptance criteria — settings

- Work duration must be between 1 and 180 minutes.
- Short break must be between 1 and 60 minutes.
- Long break must be between 1 and 120 minutes and not shorter than the short
  break.
- `intervalsBeforeLongBreak` must be between 1 and 12.
- Each timer profile has a trimmed name of 1–60 characters, unique without
  regard to letter case. A profile contains the three durations, intervals before a long
  break, and independent work/break auto-start flags.
- Built-in starter profiles can be edited and deleted. At least one profile
  must remain; deleting the current default requires selecting another default
  first. Creating a profile starts with a copy of the current default profile.
- Overview starts with the selected default profile. Editing one or more
  duration fields changes only those values for that session; unchanged
  durations, interval count and auto-start flags come from the default profile.
  The override is not saved as a profile. Duration bounds are the same as
  FR-SET-01..03 and long break must remain at least as long as short break.
- Daily goal is an integer number of hours from 1 to 24. The default is 3 hours.
- Daily goal progress uses completed-session focus time, completed work
  intervals from the active session, and the current work interval's actual
  duration only after that interval reaches 300 seconds. It updates live while
  that interval runs and stops increasing while paused or on a break. The
  progress bar caps at 100%; the displayed focus total may continue above the
  goal.
- Starter profiles are Classic (25/5/15 minutes), Deep work (50/10/20 minutes)
  and Quick session (15/3/10 minutes). Classic is the initial default; all
  start with four work intervals before a long break and both auto-start flags
  disabled. The initial theme is light, notifications are enabled and data
  retention is keep forever.
- Export all data offers a JSON file or a ZIP archive of CSV files and includes
  settings, profiles, sessions and analytics. Clear session history deletes
  only completed and abandoned sessions, preserving settings and any active
  session. Delete all data is a separate confirmed action that stops an active
  session, deletes all personal data and recreates default settings and starter
  profiles. The data-folder action opens the local application-data directory.
- Retention must be between 7 and 3650 days, or "keep forever".
- Native system notifications cover each completed work, short-break and
  long-break interval, but not a separate `session.completed` event. The
  desktop app sends them even while its window is focused; the operating
  system's notification and quiet-mode settings may suppress their display.
  Notifications require the app and its WebSocket client to be running.
- Enabling notifications requests OS permission before saving the preference
  as enabled. If permission is denied, the preference remains off and the UI
  explains that delivery is blocked. Each later activation calls the OS
  permission request again; whether the OS presents another prompt is under
  OS control. Because notifications are enabled by default, the desktop app
  performs this permission check/request on first startup as well. A denial
  turns the saved preference off and is explained in Settings. Turning
  notifications off suppresses delivery immediately.
- Theme changes apply immediately; supported choices are light, dark and
  system.
- A rejected update leaves the previous settings unchanged.

## 6. Distraction control (Phase 2)

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-DIS-01 | The learner can create, edit, enable/disable and delete blocking rules. | Should | Phase 2 | UC-12 | FST-028 |
| FR-DIS-02 | A rule can match an executable path, a process name or a window-title pattern. | Should | Phase 2 | UC-12 | FST-027, FST-028 |
| FR-DIS-03 | The system detects the active window/application while a session is running. | Should | Phase 2 | UC-13 | FST-027 |
| FR-DIS-04 | The system evaluates the active window against the active blocking policy. | Should | Phase 2 | UC-13 | FST-028 |
| FR-DIS-05 | A matched rule can log, warn or block the application. | Should | Phase 2 | UC-14 | FST-028 |
| FR-DIS-06 | Every detection is projected into a distraction log read-model entry with app, title, category and action. | Should | Phase 2 | UC-13, UC-15 | FST-008 |
| FR-DIS-07 | The learner can review distraction logs by session and date range. | Should | Phase 2 | UC-15 | FST-028 |
| FR-DIS-08 | Monitoring is suspended when the session is paused, on a break, or stopped. | Should | Phase 2 | UC-13 | FST-027 |
| FR-DIS-09 | A failed block is reported to the learner and recorded with a reason. | Should | Phase 2 | UC-14 | FST-028 |

### Acceptance criteria — distraction control

- With no active policy, detection returns "ignored" and writes no log.
- The first matching enabled rule wins; rule precedence is path → process →
  title.
- Blocking a process that has already exited completes without an error.
- Monitoring stops within one tick after the session leaves `RUNNING`.
- A log entry always references a valid session or is explicitly session-less
  for pre-session detections.

## 7. Privacy and data control

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-PRV-01 | All productivity data is stored in a local database. | Must | MVP | all | FST-004, FST-013 |
| FR-PRV-02 | The core runtime makes no outbound network calls. | Must | MVP | all | FST-026 |
| FR-PRV-03 | The local API binds to loopback and requires a per-installation token. | Must | MVP | all | FST-014 |
| FR-PRV-04 | The learner can configure data retention and trigger cleanup. | Should | MVP | UC-11 | FST-020 |
| FR-PRV-05 | The learner can delete all personal data after an explicit confirmation. | Should | MVP | UC-11 | FST-020 |
| FR-PRV-06 | The learner can export settings, profiles, sessions and analytics as a JSON file or CSV archive. | Should | MVP | UC-11 | FST-020, FST-036 |
| FR-PRV-07 | The learner can clear completed and abandoned session history without deleting settings or an active session. | Should | MVP | UC-11 | FST-020 |
| FR-PRV-08 | The learner can open the local application-data directory from Settings. | Could | MVP | UC-11 | FST-020 |

### Acceptance criteria — privacy

- An integration test blocks all non-loopback sockets and the full MVP test
  suite still passes.
- The API rejects requests without the token with `401`.
- The API rejects non-loopback origins.
- Deleting all data removes sessions, intervals, distractions, progress and
  settings, then recreates default settings.
- Clearing session history removes only completed and abandoned sessions and
  their intervals/outbox records; it preserves settings and active sessions.
- An export contains settings, timer profiles, session and interval history,
  and analytics. JSON is a single portable file; CSV is a ZIP archive with
  separate tables for settings, profiles, sessions, intervals and analytics.
- Retention cleanup never deletes the active session.
- Retention cleanup runs only when the learner explicitly requests it. It
  removes completed or abandoned sessions whose end time is at least the
  configured number of days old, plus their intervals and outbox records.
  "Keep forever" removes nothing. Startup and scheduled automatic cleanup are
  not performed.
- Clearing all session history is a separate action and requires explicit
  confirmation; it preserves settings and active sessions.

## 8. Future content intelligence (Phase 3)

| ID | Requirement | Priority | Phase | UC | Board |
|---|---|---|---|---|---|
| FR-CAP-01 | The learner can opt in to periodic local screen capture during a session. | Could | Phase 3 | UC-16 | FST-030 |
| FR-REC-01 | The system can recognize study material from captured frames. | Could | Phase 3 | UC-17 | FST-029 |
| FR-REC-02 | The system can categorize a session automatically from recognized content. | Could | Phase 3 | UC-18 | FST-029 |
| FR-REC-03 | Captured frames and recognized content stay local unless the learner exports them. | Could | Phase 3 | UC-16..18 | FST-026, FST-030 |

These requirements are documented for roadmap completeness. They are not part
of the MVP or Phase 2 acceptance criteria.

Screen captures are opt-in and disabled by default. They follow the global
`retention_days` policy and are deleted with their session or by the delete-all
data operation.

## 9. Requirement status summary

| Area | Must | Should | Could |
|---|---:|---:|---:|
| Session lifecycle | 10 | 1 | 0 |
| History | 3 | 1 | 1 |
| Streaks | 4 | 0 | 0 |
| Analytics | 3 | 2 | 0 |
| Settings | 4 | 2 | 0 |
| Distraction control | 0 | 9 | 0 |
| Privacy | 3 | 2 | 1 |
| Content intelligence | 0 | 0 | 4 |
| **Total** | **27** | **17** | **6** |

## 10. Related documents

- [Use case catalogue](../UseCases/UseCases.md)
- [Non-functional requirements](NonFunctionalRequirements.md)
- [Traceability](Traceability.md)
- [REST and WebSocket API](../api/RESTAndWebSocket.md)
