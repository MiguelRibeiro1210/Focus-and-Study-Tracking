# Domain Model

The domain model describes the business vocabulary of FocusDesktop. It is a
conceptual model: it shows business concepts, their stereotypes and their
relationships, not implementation details such as repositories, frameworks or
database columns.

The source of truth is [`DM.puml`](DM.puml); the rendered view is
[`DM.svg`](DM.svg). This conceptual model records business concepts and their
relationships. Runtime responsibilities and interfaces are described in the
architecture, use case, API, persistence and sequence documentation.

> **Convention:** the conceptual model uses the PlantUML style defined in
> [`../DocExamples/DM_Example.puml`](../DocExamples/DM_Example.puml): class name
> plus stereotype, business concepts only. Multiplicities are deliberately
> provisional and will be reviewed separately from the structural model.

## 1. Bounded contexts

| Context | Type | Aggregate root | Phase | Responsibility |
|---|---|---|---|---|
| **Session Tracking** | Write model | `FocusSession` | MVP | Owns the focus-session lifecycle, work/break intervals and the timer snapshot. |
| **Distraction Control** | Write model | `BlockingPolicy` | Phase 2 | Owns blocking rules, active-window evaluation and block decisions. |
| **Progress & Analytics** | Read model | — | MVP / Phase 2 | Projections for daily progress, productivity summaries and the distraction log. |
| **Preferences** | Write model | `AppSettings` | MVP | Owns timer defaults, theme, notifications and retention policy. |
| **Content Intelligence** | Write model | `StudyMaterial` | Phase 3 | Owns study material and locally recognized content; behind a feature flag. |

`Progress & Analytics` intentionally has **no aggregate root**. Its concepts are
read models built from domain events and persisted sessions. This keeps the
write model small and makes analytics rebuildable.

## 2. Context map

```text
Preferences ──provides defaults for──▶ Session Tracking
Session Tracking ──studies───────────▶ Content Intelligence   (Phase 3)
Distraction Control ──emits──────────▶ Progress & Analytics    (events)
Session Tracking ──────emits──────────▶ Progress & Analytics    (events)
```

Cross-context references are by identifier or by a small value object, never by
a direct object graph. The conceptual relationships are:

- `AppSettings --> FocusSession : provides defaults for`
- `FocusSession --> StudyMaterial : studies`
- `DistractionControl` and `SessionTracking` publish events consumed by
  `ProgressAnalytics`.

## 3. Aggregates

### 3.1 Session Tracking — `FocusSession`

**Aggregate root:** `FocusSession`
**Entities:** `SessionInterval`
**Value objects:** `SessionId`, `IntervalId`, `SessionSettings`, `Duration`,
`TimeStamp`, `SessionType`, `SessionState`, `IntervalState`

`FocusSession` is the consistency boundary for the session lifecycle. It owns
the interval collection and the settings snapshot used when the session was
created.

**Invariants**

- At most one session is `RUNNING`, `PAUSED` or
  `WAITING_FOR_NEXT_INTERVAL` per installation.
- A session can only move through the transitions defined by the state machine
  (`IDLE → RUNNING ⇄ PAUSED`, `RUNNING → WAITING_FOR_NEXT_INTERVAL → RUNNING`,
  then `COMPLETED` or `ABANDONED`).
- Paused time never counts as focus time.
- A completed or abandoned session is immutable.
- Stopping with less than five minutes of actual work time marks the session
  `ABANDONED`; five minutes or more marks it `COMPLETED`. Break time is excluded.
- The actual focus duration is the sum of completed work intervals, within one
  second.
- The settings snapshot is never rewritten by later preference changes.

`FocusSessionFactory` creates and reconstitutes the aggregate from immutable
snapshots; it validates interval ordering and consistency between session and
interval states. `SessionIntervalFactory` creates and reconstitutes interval
entities. State transitions remain on `FocusSession` and `SessionInterval`.
`SessionIntervalScheduleService` selects the next interval type without
mutating the aggregate. `intervalsBeforeLongBreak` counts completed work
intervals only: for a value of four, the first three completed work intervals
are followed by short breaks and the fourth by a long break. The cycle then
repeats.

**Events:** `SessionStarted`, `SessionPaused`, `SessionResumed`,
`SessionCompleted`, `SessionAbandoned`, `IntervalCompleted`, `IntervalStarted`.

**Repository:** `FocusSessionRepository` (write side only).

### 3.2 Distraction Control — `BlockingPolicy`

**Aggregate root:** `BlockingPolicy`
**Entities:** `BlockingRule`
**Value objects:** `BlockingPolicyId`, `BlockingRuleId`, `ActiveWindowInfo`,
`AppIdentifier`, `WindowTitle`, `BlockingDecision`, `BlockingAction`,
`DistractionCategory`

`BlockingPolicy` owns the rule set and evaluates an `ActiveWindowInfo` to
produce a `BlockingDecision`. The decision is a value object; it is not a
persisted entity.

**Invariants**

- A policy has at most one rule per pattern.
- Only one policy is active at a time in the MVP.
- Disabled rules are ignored during evaluation.
- Rule precedence is exact executable path → process name → window-title
  pattern; the first matching enabled rule wins.
- Evaluation is read-only and never mutates the policy.

**Domain service:** `DistractionPolicyService` evaluates a policy against a
window and checks whether monitoring is allowed for the current session state.

**Events:** `DistractionDetected`, `DistractionBlocked`,
`DistractionBlockFailed`.

**Repository:** `BlockingPolicyRepository` (write side only).

`DistractionLog` is **not** part of this aggregate. It is an append-only read
model in `Progress & Analytics`, fed by the events above.

### 3.3 Preferences — `AppSettings`

**Aggregate root:** `AppSettings`
**Value objects:** `SettingsId`, `TimerPreferences`, `NotificationPreference`,
`DataRetentionPolicy`, `Theme`

`AppSettings` owns the user's defaults. `TimerPreferences` is the write-side
preference value; when a new session starts, the application layer maps it to
the `SessionSettings` snapshot owned by `FocusSession`.

**Invariants**

- Work duration: 1–180 minutes.
- Short break: 1–60 minutes.
- Long break: 1–120 minutes and not shorter than the short break.
- Intervals before a long break: 1–12.
- Retention: 7–3650 days or "keep forever".
- A rejected update leaves the previous settings unchanged.

**Repository:** `AppSettingsRepository`.

### 3.4 Content Intelligence — `StudyMaterial` (Phase 3)

**Aggregate root:** `StudyMaterial`
**Entities:** `DetectedContent`
**Value objects:** `StudyMaterialId`, `DetectedContentId`, `ContentCategory`

`StudyMaterial` groups locally recognized study content. `FocusSession` may
reference a `StudyMaterial` when the learner links a session to study material,
but the material has its own consistency boundary.

**Phase 3 only:** the context is behind a feature flag and is not part of the
MVP or Phase 2 acceptance criteria.

## 4. Progress & Analytics read models

These concepts are **read models**, not aggregates. They are updated by
idempotent projections and can be rebuilt from retained persisted sessions and
durable outbox events.

| Read model | Identity | Composed of | Built from |
|---|---|---|---|
| `ProductivitySummary` | `ProductivitySummaryId` | `DailyProgress`, `DateRange`, `FocusScore` | Session summaries, daily progress, distraction counts |
| `DailyProgress` | `DailyProgressId` | `DistractionLog`, `HourStreak` | `SessionCompleted`, `DistractionDetected` |
| `DistractionLog` | `DistractionLogId` | `ActiveWindowInfo`, `BlockingDecision` | `DistractionDetected`, `DistractionBlocked`, `DistractionBlockFailed` |

`ProductivitySummary` is assembled per query for analytics. `DailyProgress` and
`DistractionLog` are persisted projections and support history, streaks and
distraction review.

**Read-model rules**

- Projections are idempotent: replaying an event must not duplicate rows or
  double-count values.
- `DistractionLog` is append-only.
- `DailyProgress` is one row per calendar day.
- A day counts toward `HourStreak` when it has at least one completed work
  interval of 5 minutes or more.
- Deleting read-model data never deletes the write-side session history; the
  projections can be rebuilt from retained source data and outbox events.
- Each projection stores an event receipt with its update so retries and replay
  do not double-count. Events expire with their source data under retention.

## 5. Domain events

| Event | Published by | Consumed by | Purpose |
|---|---|---|---|
| `SessionStarted` | `FocusSession` | WebSocket, diagnostics | Timer screen update |
| `SessionPaused` / `SessionResumed` | `FocusSession` | WebSocket | Timer screen update |
| `SessionCompleted` | `FocusSession` | `DailyProgressProjection`, WebSocket | Analytics and history |
| `SessionAbandoned` | `FocusSession` | History, diagnostics | Exclude from streaks |
| `IntervalCompleted` | `FocusSession` | WebSocket | Work/break transition with state and remaining-time snapshot |
| `IntervalStarted` | `FocusSession` | WebSocket | Manually started pending interval with remaining-time snapshot |
| `DistractionDetected` | `DistractionControl` | `DistractionLogProjection`, `DailyProgressProjection`, WebSocket | Log entry and distraction count |
| `DistractionBlocked` | `DistractionControl` | `DistractionLogProjection`, WebSocket | Mark the log entry as blocked |
| `DistractionBlockFailed` | `DistractionControl` | `DistractionLogProjection`, WebSocket | Record the failure reason |

Events are written to the transactional outbox in the same commit as the
aggregate update. A dispatcher processes them after commit; consumers must
tolerate duplicate delivery. WebSocket delivery is best-effort, and the client
reconciles state using REST snapshots.

## 6. Relationship rules

1. **One aggregate root per aggregate.** `BlockingRule` and `SessionInterval`
   are entities inside their roots; `DetectedContent` is inside
   `StudyMaterial`.
2. **Cross-aggregate links are root-to-root.** The model contains
   `AppSettings → FocusSession` and `FocusSession → StudyMaterial`; it does not
   link a value object from one aggregate directly to another aggregate's root.
3. **Read models depend on write models, never the reverse.** `DailyProgress`
   aggregates `FocusSession` data through events; `ProductivitySummary` compiles
   `DailyProgress`.
4. **Value objects are immutable.** A change produces a new value object and,
   where relevant, a new aggregate version.
5. **`DistractionLog` is a read model.** It records `ActiveWindowInfo` and
   `BlockingDecision`, but it is not the aggregate that produces the decision.

## 7. Related documents

- [Domain model diagram](DM.svg)
- [Use case catalogue](../UseCases/UseCases.md)
- [System architecture](../SystemArchitecture/Architecture.md)
- [Sequence diagrams](../SD/SequenceDiagrams.md)
- [Persistence model](../data/Persistence.md)
- [Glossary](../glossary.md)
