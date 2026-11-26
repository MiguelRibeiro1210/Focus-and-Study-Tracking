# ADR-007 — Persisted blocking rules and local management API

**Status:** Documentation approved for commit by the user on 2026-11-26.
Production implementation follows this documentation commit.

## Delivery workflow and boundaries

The user authorized completing the blocking stories sequentially on 2026-11-26.
For each story: prepare/update documentation, request permission for its
documentation commit, implement backend tests/behavior and required frontend
integration, verify the result, then request permission for the implementation
commit. Do not commit without permission or begin the next story early.

Order: FST-048 saved rules, FST-049 rule-management UI, FST-027 application
detection, FST-028 application enforcement, FST-050 website enforcement, then
FST-037 persisted distraction outcomes. FST-043 session details remains separate.

FST-048 builds on the approved [domain](ADR-006-distraction-model.md). It makes
rule configuration durable and accessible through the local API. It does not
claim that saving/enabling a rule has activated protection. FST-049 owns the
Distractions screen; FST-027/FST-028/FST-050 own runtime integration.

## Persist one policy and its typed rules

- Add Flyway migration V5; do not change migrations already released. Create
  `blocking_policy` and `blocking_rule`, then seed one empty policy during the
  migration. GET/POST never race to lazily insert the same installation row.
- Policy identity is UUID `00000000-0000-0000-0000-000000000001`. A non-null
  singleton slot, unique and constrained to `1`, allows at most one policy.
  Keep the policy row when deleting the last rule or clearing personal data.
  Multiple policies and switching profiles remain out of scope.
- Rule rows retain UUID, owning policy ID, display name, target type, application
  platform/match kind when applicable, entered target value, canonical target
  key, category, action, enabled flag and UTC created/updated timestamps.
- Application target keys encode type, platform, match kind and the domain's
  canonical identifier. Website keys encode type and normalized domain.
  Encode components unambiguously, for example a JSON array, and enforce
  `UNIQUE(policy_id, target_key)`. Website null application fields must not
  weaken uniqueness. Domain equality and database keys must agree.
- Target validation, case, path and IDN normalization remain domain behavior.
  Expose the application's existing canonical identifier for adapter use rather
  than reimplement normalization in SQL or the controller. Store both the entered
  application value and canonical key; website domains are stored normalized.
- Use variable-length text columns for names, target values and keys; this story
  does not introduce arbitrary extra limits absent from the approved domain.
  Add FK cascade from policy to rules and target-shape/action/category checks.
  Seed no blocking rules. The currently supported category is `OTHER`.

## Transaction and concurrency behavior

The application use case owns each transaction. Before any rule mutation, lock
the singleton policy row for update, then load its current rules. Hold the lock
through validation, persistence and commit. Locking after loading the rules is
insufficient: stale aggregate snapshots could otherwise erase concurrent edits.

All writers, including delete-all, use the same lock order. Increment/update
policy state and timestamps when saving changes. Listing is read-only and
returns committed configuration without invoking runtime adapters.

An invalid edit or duplicate produces no partial update. Serialize concurrent
valid mutations so both survive; a concurrent duplicate produces a conflict,
not an internal-server error. Retain the database unique constraint as a final
guard and translate only recognized rule-target conflicts to `DUPLICATE_RULE`.
Do not mislabel unrelated database failures as duplicates.

Persistence reconstitutes `BlockingPolicy` and immutable `BlockingRule` values
through their validated domain constructors. Preserve rule IDs and creation
timestamps during edits, including a target-type change. Save only the intended
configuration, not runtime capability state or detection events. Future
monitoring reads committed policy state on its next cycle; introduce no stale
application cache.

Keep transaction-lock access in an application port implemented by the JPA
adapter; do not put Spring lock types in the pure domain. A read projection
provides API DTOs with persisted audit metadata, avoiding persistence timestamps
in the existing domain rule. Flush mutations before reading their response DTOs.
Unchanged rules keep their audit timestamps; only new/changed rows receive new
creation/update values from the backend clock.

## Local API contract

All paths are under `/api/v1`; existing bearer-token and local-origin checks
apply. No endpoint exposes the installation token.

| Method | Path | Behavior |
|---|---|---|
| GET | `/blocking-rules` | `200` JSON array, initially `[]`; order by creation time then rule UUID. |
| POST | `/blocking-rules` | Validate/create, commit, then `201` with the stored rule and `Location`. |
| PATCH | `/blocking-rules/{id}` | Apply provided editable fields atomically; `200` with the stored rule. |
| DELETE | `/blocking-rules/{id}` | Remove the rule and commit; `204` with no body. |

Create requires `displayName`, `target`, `category`, `action` and `enabled`.
The caller must choose explicit values; the API does not silently enable a
rule or choose a stronger action when fields are absent.

```json
{
  "displayName": "Video sites",
  "target": { "type": "WEBSITE", "domain": "youtube.com" },
  "category": "OTHER",
  "action": "BLOCK",
  "enabled": true
}
```

Application target shape:

```json
{
  "type": "APPLICATION",
  "platform": "WINDOWS",
  "matchKind": "PROCESS_NAME",
  "value": "example.exe"
}
```

Platforms are `WINDOWS`, `MACOS`, `LINUX`; application match kinds are
`EXECUTABLE_PATH`, `PROCESS_NAME`. Website rules include subdomains under the
approved domain rules. Preserve API type separation: website targets cannot
carry app fields, and app targets cannot carry a domain field. Unknown fields,
unsupported enum values and incorrectly typed values are rejected rather than
silently coerced. Responses add read-only `id`, `createdAt` and `updatedAt`.

PATCH preserves omitted fields; provided values cannot be null. A provided
`target` is one complete replacement, not a merge of old/new variant fields.
For example `{ "enabled": false }` changes only that flag. Reject empty PATCH
objects and attempts to edit server-owned IDs/timestamps. Nonexistent rule IDs
return `404 RESOURCE_NOT_FOUND`, including repeated deletion.

Errors use the existing `{code, message, details, timestamp}` envelope:

- `400 VALIDATION_ERROR`: malformed JSON, malformed UUID or a body that is not
  a JSON object.
- `422 VALIDATION_ERROR`: missing/invalid editable fields, invalid target,
  unsupported values, null/unknown fields or empty PATCH. Provide useful field
  names in `details` without leaking stack traces.
- `409 DUPLICATE_RULE`: another rule in the policy has the same canonical target.
- Existing `401`/`403` security responses remain in effect.

Use rule-specific validation exceptions/handlers so existing endpoints retain
their current error status behavior. Do not globally change session/settings
validation from `400` to `422` for this feature.

## Desktop transport, export and deletion

- Permit PATCH in the API CORS method list and Tauri's native HTTP method
  mapping. Keep path restrictions, local-origin checks and bearer handling.
  This transport compatibility work is part of FST-048; page UI work is FST-049.
- Extend JSON data export with an additive `blockingRules` array. Add
  `blocking-rules.csv` to CSV ZIP export, containing ID, display name, typed
  target fields, category, action, enabled state and timestamps. Reuse existing
  CSV quoting; never export API tokens or internal duplicate/lock keys.
- Confirmed delete-all clears configured rules in the same transaction as the
  existing personal-data reset. Keep the singleton policy empty. Wrong/missing
  confirmation makes no changes. History-only cleanup preserves the rules.
- Do not emit `DistractionBlocked` or claim protection success on a CRUD request.
  Editing rules does not require an active session, browser integration or
  runtime OS permissions.

## Verification required before the implementation commit

- Migration on an empty and existing database; exactly one seeded policy and
  no seeded rules; database-level canonical-target uniqueness and FK checks.
- Application/website CRUD round trips with stable IDs/timestamps, reload from
  a fresh persistence context and reconstitution of matching semantics.
- Domain-normalized duplicates on add/edit, target-kind changes, enable/disable,
  empty-policy behavior and rollback without lost prior values.
- Concurrent first requests, distinct creates, duplicate creates and edits:
  no duplicate policy, lost rules, unexpected success or HTTP 500.
- HTTP shapes and status/error details; missing token/nonlocal origin; PATCH
  preflight and native HTTP method handling.
- JSON/CSV export includes configured rules; confirmed delete-all clears them;
  history-only deletion and invalid confirmation preserve them.
- Full backend verification, relevant frontend transport tests, frontend
  build/lint and Rust checks for the native method mapping. Record actual
  results; do not claim OS/browser enforcement was exercised by this story.

## Confirmed requirements for the later runtime stories

The user requests Windows, macOS and Linux, with Chrome, Edge, Firefox and Safari
where available on those systems. Application protection must temporarily
suspend matched instances and restore only restrictions introduced by
FocusDesktop. Website protection must not require an extension. The user
accepts explicit OS permission prompts and system/browser network-setting
changes with automatic restoration.

FST-050 must select and document a concrete mechanism consistent with those
constraints and the existing local-only privacy requirements; this ADR does
not approve an external forwarding proxy or weakening that privacy guarantee.
Platform/browser verification remains explicit: Linux can be checked directly
in the current workspace, while other systems need their native CI/device runs.
