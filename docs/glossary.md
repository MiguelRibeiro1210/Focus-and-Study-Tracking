# Glossary

| Term | Meaning |
|---|---|
| **Active session** | A session in `RUNNING`, `PAUSED` or `WAITING_FOR_NEXT_INTERVAL`; only one may exist per installation. |
| **Aggregate** | A domain consistency boundary with one aggregate root. |
| **Aggregate root** | The only object through which an aggregate's state may be changed; for a focus session, this is `FocusSession`. |
| **Application service / use case** | Coordinates a user goal, calling domain objects and ports and defining the transaction boundary. |
| **Auto-start** | A timer preference that starts the next work or break interval immediately after the current interval completes. |
| **Bounded context** | A boundary within which a domain model and its terms have a specific meaning. |
| **Command** | A request to change system state, such as starting or pausing a session. |
| **Domain event** | A durable record of a domain change, appended to the outbox in the same transaction as that change. |
| **Distraction** | An observation of the foreground application while distraction monitoring is active. |
| **Distraction log** | An append-only read model of detected applications and the resulting policy and blocking outcomes. |
| **Distraction policy** | The active set of rules that determines whether an observed application is logged, warned about or blocked. |
| **Focus session** | A learner's work period containing an ordered sequence of work and break intervals. |
| **Interval** | A timed work period, short break or long break within a focus session. |
| **Idempotency** | The property that processing the same command or event more than once has the same effect as processing it once. |
| **Local-first** | The application stores and processes product data on the user's device and needs no cloud service to operate. |
| **Long break** | A rest interval selected after the configured number of completed work intervals. |
| **Outbox** | A table of domain events committed atomically with writes and dispatched after commit with retry. |
| **Port / adapter** | A port describes an application-facing capability; an adapter implements it for a concrete technology such as the operating system or database. |
| **Projection / read model** | A query-oriented representation updated from source records and domain events. |
| **Read/write model separation** | Keeping query-oriented projections separate from domain objects that enforce and change business state. |
| **Remaining time** | The interval time still due according to backend timestamps; the UI may animate a local countdown between authoritative snapshots. |
| **Session settings snapshot** | The timer settings copied into a session at creation so later preference changes do not alter that active session. |
| **Short break** | A rest interval that follows a work interval when the long-break threshold has not been reached. |
| **Snapshot (timer)** | An authoritative server response containing session state, interval type, remaining time and relevant timestamps. |
| **Streak** | Consecutive local calendar days containing at least one completed work interval of five minutes or more. |
| **Transactional outbox** | The persistence pattern that stores state changes and their events in one transaction, then publishes events after commit. |
| **WebSocket event** | A best-effort live notification sent after a committed state change; clients reconcile with an authoritative snapshot after reconnect. |
| **Work interval** | A timed focus period; only completed work intervals meeting the documented minimum count toward streaks. |
| **Waiting for next interval** | Session state after an interval ends with auto-start disabled; a pending interval exists but no countdown runs. |
