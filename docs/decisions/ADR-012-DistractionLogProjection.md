# ADR-012 — Durable distraction log projection

**Status:** FST-037 design within the authorized sequential blocking delivery.
**Decision date:** 2026-11-26.

## Event ledger and derived log

Consume the version-1 application and website distraction snapshots already
committed by FST-028/FST-050. Add migration V8 with an append-only projection
event ledger keyed by event UUID, a materialized log keyed by detection UUID,
and a singleton projection lock. Store session IDs, host/application identity,
configured target, rule/category/action snapshots and occurrence times. Never
store native process handles, titles, full URLs, credentials or traffic payloads.

The ledger doubles as the deduplication receipt. Insert the receipt, rebuild the
affected detection view and mark the source outbox event delivered in one
transaction. A dedicated worker consumes only distraction event types; existing
session WebSocket dispatch remains independent. Bound batches and use retry
backoff with safe diagnostics so a malformed event cannot starve session delivery
or the rest of the distraction batch. Do not repeat native enforcement during
projection, retries or replay.

An outcome arriving first is retained in the ledger without fabricating a
detection. When detection arrives, correlate immutable facts and validate outcome
chronology using the domain contracts. A repeated event produces no duplicate.
Incompatible outcomes never turn a log into a successful block. When multiple
valid outcome events exist, select the earliest by timestamp and event UUID so
the resulting view is deterministic across delivery orders. Keep the underlying
events for review; rule changes and deletion cannot rewrite recorded snapshots.

The view distinguishes pending, confirmed blocked and failed outcomes. LOG and
WARN entries display as logged/warned rather than implying a block is pending.
Only a matching confirmed block outcome yields `blocked=true`.

## Queries and learner UI

Implement authenticated `GET /api/v1/distractions` with optional `sessionId`,
local-date `from` and `to` filters, zero-based `page` and `size` (1–100).
Combine filters, reject inverted ranges and invalid identifiers, and convert
dates to half-open UTC bounds using the installation's current timezone, as in
session history. Order detection time descending with detection UUID as a stable
tie breaker. Return `items`, `total`, `page` and `size`, with configured and
observed snapshots, action, outcome, failure reason and timestamps.

Add a distraction-history section to the existing Distractions page with date
and session filters, pagination, loading/empty/error states and explicit outcome
labels. This is a log review, not the deferred FST-043 session-detail feature.
Read failures must not invent an empty successful history or lose filter inputs.
Keep setup/rule management independent of history failures.

## Export, deletion and consistency

Include logs in JSON and CSV ZIP export. Session history deletion and retention
remove associated ledger and log rows; delete-all removes them all. Use session
foreign keys with cascading deletion and coordinate maintenance/projection with
the projection lock. Distraction outbox writes must lock and confirm the owning
session so a delayed in-flight observation cannot recreate deleted history.
Maintenance acquires the same session locks before removing source events and
sessions. Saved rules and preferences survive history cleanup, while delete-all
continues to reset both protection preferences.

## Verification

Test both target types, pending/success/failure, duplicate delivery, outcome-first
delivery, immutable snapshots, mismatched facts and chronology, deterministic
ordering, filtered pagination, transaction rollback, source delivery isolation,
export and session/delete-all cleanup. Test frontend failure/empty/pagination
behavior and truthful outcome labels. Run backend verification before frontend
integration, then relevant frontend/build/privacy checks and inspect the diff.
The projection does not close FST-050's outstanding native/browser acceptance
gaps or claim websites were blocked without a confirmed outcome.
