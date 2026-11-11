# Persistence

FocusDesktop uses one local relational database. The write model and the read
model share the database but use separate tables and responsibilities. The
diagram source is [`Persistence.puml`](Persistence.puml); the rendered view is
[`Persistence.svg`](Persistence.svg).

## 1. Storage choice

| Concern | MVP choice |
|---|---|
| Engine | H2 in embedded/file mode. |
| Location | User application-data directory, never the repository. |
| Access | Spring Data JPA/Hibernate; explicit migrations. |
| Transactions | One transaction per write use case; domain events are stored in a durable outbox atomically with the write. |
| Read models | Projection tables updated asynchronously from the durable outbox. |

SQLite is a future option; see [ADR-005](../decisions/ADR-005-H2VsSQLite.md).

## 2. Write-side tables

### `focus_session`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `SessionId`. |
| `session_type` | VARCHAR(20) | `WORK`, `SHORT_BREAK`, `LONG_BREAK`. |
| `state` | VARCHAR(32) | `IDLE`, `RUNNING`, `PAUSED`, `WAITING_FOR_NEXT_INTERVAL`, `COMPLETED`, `ABANDONED`. |
| `started_at`, `ended_at` | TIMESTAMP | UTC. |
| `planned_seconds`, `actual_seconds` | BIGINT | Planned and recorded focus duration. |
| `settings_snapshot` | CLOB/JSON | Immutable `SessionSettings` snapshot. |
| `active_slot` | SMALLINT, nullable | `1` for an active session; unique across the table to enforce one active session. |
| `study_material_id` | UUID FK, nullable | Phase 3 only. |
| `created_at`, `updated_at` | TIMESTAMP | Audit columns. |
| `version` | BIGINT | JPA optimistic version; stale concurrent writes are rejected. |

Indexes: `(state)` for active-session recovery, `(started_at)` for history, and
a unique constraint on `active_slot` for the single-active-session invariant.
Recovery queries include `RUNNING`, `PAUSED` and
`WAITING_FOR_NEXT_INTERVAL` sessions.

### `session_interval`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `IntervalId`. |
| `session_id` | UUID FK | Owning session. |
| `sequence_no` | INT | Order within the session. |
| `interval_type` | VARCHAR(20) | Work or break type. |
| `state` | VARCHAR(20) | `PENDING`, `RUNNING`, `COMPLETED`, `SKIPPED`. |
| `planned_seconds`, `actual_seconds` | BIGINT | Durations. |
| `started_at`, `ended_at`, `paused_at` | TIMESTAMP | UTC; `paused_at` is nullable. |
| `remaining_seconds` | BIGINT | Remaining time at pause; pending intervals start with their full planned duration. |

Unique index: `(session_id, sequence_no)`.

`paused_at` and `remaining_seconds` preserve the current interval across
application restarts. A pending interval stores its full planned duration as
remaining time; the first start sets `started_at` and clears `paused_at`.
At pause, elapsed seconds are added to `actual_seconds` and remaining time is
stored. Resume starts a new running segment from that remaining time. Stopping
while paused adds no further elapsed time.

### `blocking_policy` and `blocking_rule`

`blocking_policy` stores the aggregate root (`id`, `name`, `active`, audit
columns). `blocking_rule` stores the entity rows (`id`, `policy_id`, `pattern`,
`action`, `category`, `enabled`, `created_at`). A partial/unique index enforces
**at most one active policy** and `(policy_id, pattern)` uniqueness.

### `app_settings`

One row per installation: timer defaults, theme, notifications and retention.
The active session does not read this row; it uses its snapshot.

## 3. Event delivery and read-model tables

### `domain_event_outbox`

Durable event records are appended in the same database transaction as the
aggregate update. Columns: `event_id` (UUID PK), `event_type`, `aggregate_id`,
`occurred_at` (UTC), `payload` (CLOB/JSON), `delivered_at` (nullable UTC) and
`attempt_count`. A dispatcher retries undelivered rows and marks them delivered
after consumers acknowledge processing. Rows are retained with their source
session/distraction data for deterministic projection rebuilds; retention
cleanup deletes an event when its source data expires. Delete-all removes the
outbox in the same operation so pending events cannot recreate deleted data.
Projection acknowledgement controls durable delivery; WebSocket forwarding is
best-effort and does not delay outbox completion.

FST-013 persists outbox records atomically with session writes. The dispatcher
that processes pending records is tracked separately under FST-015.

### `projection_event_receipt`

Each projection records `(projection_name, event_id)` under a unique
constraint in the same transaction as its read-model update. This makes retries
and explicit replays safe from duplicate increments.

### `daily_progress`

One row per calendar day (`progress_date` unique). It stores completed sessions,
focus/break seconds, distraction count, current streak and longest streak. It is
updated by `DailyProgressProjection` from `SessionCompleted` and
`DistractionDetected`.

### `distraction_log`

Append-only entries: `id`, nullable `session_id`, `occurred_at`,
`app_identifier`, optional `window_title`, `category`, `action`, `blocked`,
`reason`. It is updated by `DistractionLogProjection` from
`DistractionDetected`, `DistractionBlocked` and `DistractionBlockFailed`.

`ProductivitySummary` is **not** a table. It is assembled per analytics query
from `SessionSummary`, `DailyProgress` and `DistractionLog`.

## 4. Phase 3 tables

`study_material` and `detected_content` are created only when Content
Intelligence is enabled. `focus_session.study_material_id` is nullable until
then. These tables stay local; captured content is never uploaded.

Screen captures are stored as local files under the application-data directory,
associated with a session. They are opt-in, follow `retention_days`, and are
removed with their session or by delete-all.

## 5. Transactions and consistency

1. The application service opens a transaction.
2. The aggregate is loaded, validated and saved.
3. Domain events are collected by the aggregate and appended to
   `domain_event_outbox` in the same transaction.
4. The transaction commits.
5. An outbox dispatcher retries pending events and delivers them to projections
   and WebSocket after commit. WebSocket delivery remains best-effort.

Projections are **idempotent** and **eventually consistent**. A duplicate event
must not duplicate a log row or double-count a daily total. If a projection
fails, the write-side data remains correct; the outbox retries it. Rebuilds use
retained sessions and outbox events. Once retention deletes source data and its
events, that data can no longer be reconstructed.

## 6. Retention and deletion

- Cleanup runs on startup and daily while the app is open.
- Data older than `retention_days` is deleted, except the active session.
- "Keep forever" disables time-based deletion.
- "Delete all data" requires explicit confirmation, stops the active session,
  removes sessions, intervals, distraction logs, daily progress, outbox events,
  projection receipts and settings, then recreates default settings.
- The read models can be rebuilt after deletion only from data that still
  exists; deletion is intentionally irreversible.

## 7. Migrations

- Flyway applies versioned migrations from `db/migration` and records applied
  versions in its schema-history table.
- Schema changes use versioned migrations; never rely on Hibernate
  `ddl-auto=update` in production builds.
- Migrations are forward-only and tested against a database created by the
  previous release.
- A backup is taken before a destructive migration; if the migration fails, the
  app starts in read-only diagnostics mode rather than losing data.

All instants are stored in UTC. `progress_date` is derived with the operating
system's current time zone. If that zone changes, rebuild daily projections from
retained UTC session timestamps and outbox events so historical dates use the
current zone consistently.

## 8. Rebuild procedure

```text
1. Pause the projection(s) being rebuilt.
2. Clear only their read-model rows and matching projection_event_receipt rows.
3. Replay all retained outbox events in occurred_at/event_id order to the
   selected projections, including events already marked delivered.
4. Recompute streaks and verify daily totals against retained session data.
5. Resume the projections.
```

The replay runner bypasses normal delivered-event filtering but uses the same
projection code. Receipts make normal retries safe; clearing the selected
projection's receipts allows a full rebuild. The rebuild must be deterministic
and safe to run repeatedly.

## 9. Related documents

- [Domain model](../DM/DomainModel.md)
- [System architecture](../SystemArchitecture/Architecture.md)
- [ADR-005 H2 vs SQLite](../decisions/ADR-005-H2VsSQLite.md)
- [Test strategy](../testing/TestStrategy.md)
