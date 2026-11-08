# Sequence Diagrams

Each sequence diagram shows one use case from the UI or scheduler through the
controller, application service, domain and adapters. Messages are numbered with
PlantUML `autonumber`; each diagram shows the successful scenario. Preconditions
are assumed to hold, so error and rejection responses are documented in the API
contract rather than repeated in these flows.

## 1. Diagram index

| # | Use case | Source | Render | Main participants |
|---|---|---|---|---|
| 1 | UC-01 Start focus session | [`SD_StartSession.puml`](SD_StartSession.puml) | [`SD_StartSession.svg`](SD_StartSession.svg) | UI, `SessionController`, `StartSessionUseCase`, `FocusSessionRepository`, `AppSettingsRepository`, event publisher |
| 2 | UC-02 Pause session | [`SD_PauseSession.puml`](SD_PauseSession.puml) | [`SD_PauseSession.svg`](SD_PauseSession.svg) | UI, `SessionController`, `PauseSessionUseCase`, `FocusSession` |
| 3 | UC-03 Resume session | [`SD_ResumeSession.puml`](SD_ResumeSession.puml) | [`SD_ResumeSession.svg`](SD_ResumeSession.svg) | UI, `SessionController`, `ResumeSessionUseCase`, `FocusSession` |
| 4 | UC-04 Stop session | [`SD_StopSession.puml`](SD_StopSession.puml) | [`SD_StopSession.svg`](SD_StopSession.svg) | UI, `SessionController`, `StopSessionUseCase`, `DailyProgressProjection` |
| 5 | UC-05 Complete interval | [`SD_CompleteInterval.puml`](SD_CompleteInterval.puml), [`SD_StartNextInterval.puml`](SD_StartNextInterval.puml) | [`SD_CompleteInterval.svg`](SD_CompleteInterval.svg), [`SD_StartNextInterval.svg`](SD_StartNextInterval.svg) | Scheduler or learner, interval use cases, `FocusSession` |
| 6 | UC-06 Skip break | [`SD_SkipBreak.puml`](SD_SkipBreak.puml) | [`SD_SkipBreak.svg`](SD_SkipBreak.svg) | UI, `SessionController`, `SkipBreakUseCase`, `FocusSession` |
| 7 | UC-07 View session history | [`SD_QuerySessionHistory.puml`](SD_QuerySessionHistory.puml) | [`SD_QuerySessionHistory.svg`](SD_QuerySessionHistory.svg) | UI, `HistoryController`, `QuerySessionHistoryUseCase`, `SessionQueryRepository` |
| 8 | UC-08 View daily streak | [`SD_CalculateDailyStreak.puml`](SD_CalculateDailyStreak.puml) | [`SD_CalculateDailyStreak.svg`](SD_CalculateDailyStreak.svg) | UI, `AnalyticsController`, `CalculateDailyStreakUseCase`, `DailyProgressReadRepository` |
| 9 | UC-09 View productivity analytics | [`SD_ViewAnalytics.puml`](SD_ViewAnalytics.puml) | [`SD_ViewAnalytics.svg`](SD_ViewAnalytics.svg) | UI, `AnalyticsController`, analytics use case, read-model repositories, assembler |
| 10 | UC-10 Configure timer settings | [`SD_ConfigureTimerSettings.puml`](SD_ConfigureTimerSettings.puml) | [`SD_ConfigureTimerSettings.svg`](SD_ConfigureTimerSettings.svg) | UI, `SettingsController`, `UpdateTimerSettingsUseCase`, `AppSettings`, `TimerPreferences` |
| 11 | UC-11 Configure application settings | [`SD_ConfigureApplicationSettings.puml`](SD_ConfigureApplicationSettings.puml) | [`SD_ConfigureApplicationSettings.svg`](SD_ConfigureApplicationSettings.svg) | UI, `SettingsController`, `UpdateApplicationSettingsUseCase`, `AppSettings` |
| 12 | UC-12 Manage blocking rules | [`SD_ManageBlockingRules.puml`](SD_ManageBlockingRules.puml) | [`SD_ManageBlockingRules.svg`](SD_ManageBlockingRules.svg) | UI, `BlockingRuleController`, `ManageBlockingRulesUseCase`, `BlockingPolicy` |
| 13 | UC-13 Detect distracting application | [`SD_DetectDistraction.puml`](SD_DetectDistraction.puml) | [`SD_DetectDistraction.svg`](SD_DetectDistraction.svg) | Active-window monitor, `BlockingPolicy`, policy service, event publisher, projections, WebSocket |
| 14 | UC-14 Block distracting application | [`SD_BlockDistraction.puml`](SD_BlockDistraction.puml) | [`SD_BlockDistraction.svg`](SD_BlockDistraction.svg) | `BlockDistractionUseCase`, platform blocker, event publisher, projection |
| 15 | UC-15 Review distraction log | [`SD_ReviewDistractionLog.puml`](SD_ReviewDistractionLog.puml) | [`SD_ReviewDistractionLog.svg`](SD_ReviewDistractionLog.svg) | UI, `DistractionController`, query use case, `DistractionLogReadRepository` |

## 2. Conventions

- **Transaction boundary:** the application service owns the transaction and
  writes domain events to a transactional outbox in the same commit. An
  idempotent dispatcher processes them after commit.
- **Read models:** analytics and distraction-log queries go through read-model
  ports. They never load aggregates or run domain rules.
- **Projections:** `SessionCompleted`, `DistractionDetected`,
  `DistractionBlocked` and `DistractionBlockFailed` are consumed by idempotent
  projections. A projection failure is logged and retried or replayed; it does
  not roll back the write-side transaction.
- **WebSocket:** live updates are best-effort snapshots emitted after committed
  state changes. The client reconciles by reading the authoritative snapshot
  after reconnect and polls the active session every 30 seconds while running.
- **Errors:** error codes (`ACTIVE_SESSION_EXISTS`, `INVALID_SESSION_STATE`,
  `INVALID_DATE_RANGE`, `VALIDATION_ERROR`, etc.) and HTTP status codes are
  defined in [`../api/RESTAndWebSocket.md`](../api/RESTAndWebSocket.md); they are
  outside the happy-path scope of these diagrams.
- **Privacy:** no sequence sends productivity data outside the workstation.

## 3. Read-model flows

### Session completion

`SD_StopSession` records `SessionCompleted` or `SessionAbandoned` in the outbox
in the same transaction as the aggregate save. The dispatcher invokes
`DailyProgressProjection` after commit. Replaying an event is idempotent.

### Distraction detection

`SD_DetectDistraction` shows the write side publishing `DistractionDetected`.
Two projections react:

- `DistractionLogProjection` upserts the append-only log entry.
- `DailyProgressProjection` increments the day's distraction count.

The log entry records the `ActiveWindowInfo` and `BlockingDecision`. The write
side never writes the log repository directly.

### Block outcome

`SD_BlockDistraction` shows the successful `DistractionBlocked` outcome.
`DistractionLogProjection` marks the entry as blocked. Failure handling is
defined by the API and blocking contracts, outside this happy-path diagram.

### Analytics and streaks

`SD_ViewAnalytics` reads `SessionSummary`, `DailyProgress` and
`DistractionLog` projections and assembles `ProductivitySummary`.
`SD_CalculateDailyStreak` reads `DailyProgress` and computes `HourStreak`.
Both flows are read-only and can run while a session is active.

## 4. Related documents

- [Use case catalogue](../UseCases/UseCases.md)
- [System sequence diagrams](../SSD/SystemSequenceDiagrams.md)
- [Domain model](../DM/DomainModel.md)
- [REST and WebSocket API](../api/RESTAndWebSocket.md)
