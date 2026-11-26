# Test Strategy

The test strategy protects the timer, privacy and read-model guarantees. Tests
are layered: fast domain tests for invariants, application tests with fakes,
integration tests for adapters, and a small E2E suite for desktop flows.

### Interval transaction regression (2026-11-26)

CI exposed a race in IntervalCompletionTransactionTest: increasing a scheduled
task's fixed delay does not delay its first invocation. Give that integration
fixture an explicit initial delay so only the test invokes interval completion.
Session aggregate reads must fetch the parent and intervals in one database
statement, avoiding inconsistent reconstruction if another transaction commits
between parent and lazy-child reads. Verify both ID and active-session loading
use one statement and retain rollback/outbox assertions. Keep domain validation
strict; do not hide inconsistent persisted state by weakening its invariants.

## 1. Test levels

| Level | Scope | Tooling | Runs in CI |
|---|---|---|---|
| Domain unit | Aggregates, entities, value objects, domain services, state transitions | JUnit 5, AssertJ | Yes |
| Application | Use-case orchestration, transactions, event publication, error mapping | JUnit 5, fakes/Mockito | Yes |
| Integration | JPA repositories, projections, REST controllers, WebSocket, security filter | Spring Boot Test, MockMvc, H2 | Yes |
| Architecture | Dependency rule, package boundaries, no infrastructure in domain | ArchUnit | Yes |
| Contract | REST/WebSocket payloads and error codes | Spring REST Docs / JSON assertions | Yes |
| E2E | Start → pause → resume → stop, history, analytics, settings, reconnect | Playwright + Tauri driver | Nightly / release |
| Performance | Latency, memory, seeded history/analytics | JMH / benchmark harness | Nightly |
| Privacy | Runtime stays on loopback; no telemetry dependencies | Source/dependency audit and Linux packaged network guard | Yes |

## 2. Domain tests

- Every aggregate invariant has a positive and a negative test.
- `FocusSession` state transitions are table-driven; invalid transitions return
  `INVALID_SESSION_STATE`.
- Session factories reject inconsistent snapshots and preserve valid interval
  order and state on reconstitution.
- Paused time is excluded from focus duration; break time never counts as focus.
- Stopping at 299 seconds of work abandons a session; stopping at 300 or more completes it.
- Long-break scheduling counts work intervals only, excludes short breaks, and repeats after a long break.
- The settings snapshot is immutable after creation.
- `BlockingPolicy` rule precedence and first-match-wins are tested.
- `BlockingDecision` is read-only; no policy mutation during evaluation.
- `AppSettings` validation boundaries (1/180, 1/60, 1/120, 1/12, 7/3650) are
  tested.
- `HourStreak` rules: 5-minute threshold, today open, gap resets current but not
  longest.

## 3. Read-model and projection tests

| Scenario | Assertion |
|---|---|
| `SessionCompleted` delivered once | `DailyProgress` increments exactly once. |
| Same event delivered twice | No duplicate row and no double count. |
| `DistractionDetected` + `DistractionBlocked` out of order | Log converges to the correct final state. |
| `DistractionBlockFailed` | Entry is marked failed with a reason. |
| Rebuild from persisted sessions | Daily totals and streaks match the incremental projection. |
| Projection failure | Write-side transaction is not rolled back; retry/replay succeeds. |

`ProductivitySummary` is assembled per query; tests assert the assembler is
deterministic and does not mutate `DailyProgress` or `DistractionLog`.

## 4. Integration tests

- JPA adapter tests use an isolated H2 in-memory database and apply the same
  Flyway migrations as the file-backed application database.
- Repository round-trips preserve value objects and interval order.
- Active-session recovery finds exactly one `RUNNING`, `PAUSED` or
  `WAITING_FOR_NEXT_INTERVAL` session after restart and preserves the interval's
  remaining time.
- REST tests cover each endpoint, validation, 401/403/404/409/422/500 mapping.
- WebSocket tests cover connect, event delivery, disconnect and reconnect.
- Desktop notification tests cover one native notification per unique
  `interval.completed` event, distinct work/break messages using the previously
  displayed interval, no notification for `session.completed`, and suppression
  when the preference is off or OS permission is unavailable.
- Settings tests cover the enabled-by-default startup permission check, keeping
  the preference on after a grant, turning it off after denial, and requesting
  permission again after a later user activation.
- Packaged desktop smoke checks exercise notification permission and delivery
  for work and break completion on supported operating systems; browser-only
  tests mock the native notification adapter.
- Retention cleanup deletes old data but never the active session.
- "Delete all data" leaves defaults and no user rows.
- Flyway migrations apply to a copy of the previous schema.

## 5. Privacy and security tests

- `npm run privacy:audit` checks runtime source endpoints and direct/resolved
  frontend and Rust dependencies for non-local endpoints and telemetry or
  crash-reporting SDKs.
- The packaged Linux smoke flow runs with a socket guard that rejects and logs
  non-loopback TCP connects and UDP sends. The smoke flow must complete without
  any logged attempt. Build-time dependency/runtime downloads are excluded;
  this audit covers the installed app's runtime.
- The app rejects requests without the bearer token (`401`) and non-loopback
  origins (`403`).
- Dependency and licence scans fail on high/critical findings or copyleft in
  distributed artefacts.
- Secret scanning fails on committed tokens.
- Schema review confirms no keystrokes, clipboard, browsing history or file
  contents are stored.

## 6. Performance tests

| Scenario | Target |
|---|---|
| Start session p95 | < 200 ms |
| History query for 10k sessions p95 | < 300 ms |
| Analytics for 366 days p95 | < 500 ms |
| Timer drift over 2 hours | ≤ 1 s |
| Backend idle RSS | < 250 MB |
| UI idle RSS | < 150 MB |
| DB size for 5 years | < 50 MB |

Use a fake clock for timer tests; never sleep for real time in unit tests.

## 7. Coverage and quality gates

| Gate | Threshold |
|---|---|
| Domain line coverage | ≥ 85% |
| Overall backend line coverage | ≥ 75% |
| ArchUnit violations | 0 |
| High/critical dependency vulnerabilities | 0 |
| Privacy network test | Pass |
| Contract tests | Pass |
| Build + tests time | < 5 minutes |

Coverage is a floor, not a goal: a change that adds untested branches can still
fail review even if the percentage is above the threshold.

## 8. CI pipeline

```text
1. Format/static analysis
2. Compile + ArchUnit
3. Domain and application unit tests
4. Integration tests (H2, MockMvc, WebSocket)
5. Privacy/network sandbox test
6. Dependency, licence and secret scans
7. Package the desktop bundle
8. Nightly: E2E matrix (Windows, macOS, Linux) + performance
```

The PR checklist requires that changed behaviour updates the matching use case,
relevant design documentation and traceability row.

## 9. Test data

- Use fixed dates and a fake clock; avoid "today" in assertions.
- Use builders for aggregates and value objects; do not share mutable fixtures.
- Seed history/analytics data with deterministic generators.
- Never use real personal data in tests or screenshots.

## Distraction domain verification (FST-008)

Documentation, backend tests and then domain implementation were approved in
separate steps on 2026-11-26; the final implementation review was also approved.
The design source is [ADR-006](../decisions/ADR-006-distraction-model.md).

| Test class | Meaningful behavior |
|---|---|
| `WebsiteTargetTest` | Domain/subdomain boundaries, case, DNS-dot and internationalized-name normalization; invalid configuration and malformed observations. |
| `ApplicationTargetTest` | Host-independent Windows drive/network and POSIX path normalization, platform-specific case rules, exact process names, missing paths, invalid identifiers and root traversal. |
| `BlockingPolicyTest` | Enabled-rule matching, path/process and website specificity precedence, canonical duplicates, atomic edits, empty policies, immutable rule membership and separate app/site observations. |
| `DistractionPolicyServiceTest` | Running work only; every other session/interval combination is ineligible; absent/empty/disabled policies and reevaluation after pause, break, stop, disable or deletion. |
| `DistractionEventSnapshotTest` | Snapshots survive rule edits/deletion; outcomes preserve detection/session correlation, chronology and explicit failure reasons; unrelated observations/outcomes cannot be attached or reported as successful blocks. |

Rules, targets, decisions and observation facts are immutable. The policy
validates a complete replacement before changing a rule. The domain repository
port is implemented by FST-048, which supplies storage and its single-active-policy
constraint. `domain.readmodel.DistractionLog` pairs a detection with an optional
sealed `DistractionBlockOutcome`, checking their facts and chronology. Log
projection/delivery and persistence belong to FST-037. Category fixtures use
`OTHER`; a complete learner-facing category catalogue is not defined here.

Before implementation, 132 existing tests passed and the new contracts failed
compilation on missing production types. The original 88 FST-008 cases passed
after implementation. Four additional cases cover invalid path normalization
and event/log invariants; the IDN case also checks a Unicode DNS separator.

Final local verification: `cd backend && ./mvnw verify -o` passed **224 tests**
(132 existing + 92 FST-008), with zero failures, errors or skips, and produced
the repackaged Spring Boot JAR. The local JDK is 27; Maven compiles with Java
release 17. `git diff --check` passed. No new framework or external library is
required by the domain, and no OS/browser/filesystem/network operations occur
in policy evaluation.

These tests verify domain contracts, not actual monitoring, blocking, browser
support, debounce, restriction cleanup, outbox delivery, storage or UI behavior.
Those remain in the assigned follow-on stories and require their own approvals.

## Blocking rule management verification (FST-048)

[ADR-007](../decisions/ADR-007-BlockingRuleManagement.md) defines the implemented contract.
Verification on 2026-11-26 passed: full offline Maven verify (239 tests),
frontend Vitest (82 tests), frontend build/lint, offline Rust compilation and
Rust formatting. Fifteen API integration cases cover CRUD, normalized target
replacement, concurrent distinct/duplicate creates, database constraints,
rollback, exports, history preservation and delete-all. Three frontend transport
cases cover PATCH, validation errors and bodyless deletion. OS/browser runtime
enforcement is not implemented or verified by this story.

- Verify V5 on empty and existing databases, one seeded empty policy, FK/target
  shape checks and canonical uniqueness for both application and website rules.
- Exercise CRUD, typed-target replacement, toggles, stable IDs/timestamps,
  reload in a fresh persistence context and reconstructed matching semantics.
- Reject invalid, duplicate, unknown-ID and malformed requests with the agreed
  status/envelope; prove failed edits preserve all previous values.
- Coordinate concurrent requests with latches/barriers: first reads/writes,
  distinct creates, duplicate creates and edits must preserve data without
  duplicate policies, lost updates or unexpected HTTP 500 responses.
- Check token/origin restrictions, PATCH CORS preflight and native method
  dispatch. Do not weaken the token or local-origin boundary.
- Verify JSON and CSV ZIP rule exports, confirmation-protected delete-all,
  preservation during history cleanup and transaction rollback on failure.
- Run full backend verification, relevant frontend transport tests, frontend
  build/lint and native Rust checks. Runtime OS/browser enforcement is tested
  in its own stories and must not be inferred from rule CRUD succeeding.

## Application suspension verification (FST-028)

The [suspension design](../decisions/ADR-010-ApplicationSuspension.md) records
consent, policy evaluation, guardian leases and restoration limits. Full offline
backend verification passes 269 tests; a subsequent cross-platform adapter test
also verifies recovery after a confirmed guardian restriction is forcibly killed.
Native tests operate only on child processes they create, covering suspend/resume,
EOF, lease expiry, journal recovery/locking, stale identities and external stops.
Coordinator tests cover LOG/WARN/BLOCK, debounce, new instances, pause/rule changes,
changes during native operations, failures and pending restoration. Persistence/API
tests verify consent, export/reset, snapshots and independent session dispatch.

Frontend Vitest passes 98 tests, including opt-in consent, failed preference saves
and global warning/failure feedback. Build, privacy audit, Rust compilation and
formatting pass; lint has only existing SettingsPage warnings. The bundled Java
17 successfully launches the packaged PropertiesLauncher guardian and suspends,
resumes and restores an owned fixture on EOF. CI checks native adapter behavior
on Windows/macOS/Linux and staged runtime classpaths. The complete native matrix
passed for `a9c8d9c` on 2026-11-26, including packaged smoke checks:
[CI evidence](https://github.com/MiguelRibeiro1210/Focus-and-Study-Tracking/actions/runs/45767001748).

### Website proxy and recovery (FST-050)

Java 17 verification passes 290 backend tests when the optional owned Firefox
fixture is enabled. Policy tests cover subdomains, precedence, separate consent,
pause/read failures, outcomes and debounce. Transport tests prove CONNECT byte
transparency, denial before upstream connection, existing tunnel closure,
validated HTTP authority and proxy credential stripping. Ownership tests cover
exclusive journals, startup recovery, EOF/lease restoration, failed restoration
and preserving replacement proxy settings. Runtime tests use a fake settings
store in a separate guardian process; they never change live user settings.
API integration checks bearer/origin security, strict consent, export and reset.

Run the optional browser check with `FOCUSDESKTOP_FIREFOX=/path/to/firefox`
and `./mvnw -Dtest=BrowserProxyIntegrationTest test`. It creates a temporary
profile and controlled origin, verifies PAC routing and allowed/blocked HTTP,
and confirms real Firefox HTTPS CONNECT denial without reaching the destination.
No test certificate or live browser profile is modified. Other browser/OS
combinations remain unverified. The ordinary packaged network guard still runs
with website consent off. The source audit allows only the locally intercepted
reserved challenge authority; it does not claim dynamic proxy sockets are local.

## 10. Distraction log projection (FST-037)

Full Java 17 verification passes 296 backend tests with the optional controlled
Firefox check enabled. Projection integration covers application/website targets,
outcome-first and duplicate delivery, pending/success/failure, incompatible facts
and chronology, deterministic outcome selection, malformed-event retry isolation,
transaction rollback, authenticated filtered pagination, JSON/CSV export and
history/retention/delete-all cleanup. A concurrent test holds an observation
transaction open while deletion starts, then proves the source event and history
are removed after the write commits; a delayed post-delete append is discarded.
Final log/outbox checks pass 12 tests after adding immutable duplicate-append
validation: retries cannot overwrite a snapshot or reset its delivery marker.

Frontend history checks cover truthful outcome labels, session filtering,
pagination, preserving date inputs on read failure, retry and strict wire outcome
validation (107 frontend tests pass in total). No projection/replay test invokes
native enforcement. Logs cannot
claim successful blocking without a matching confirmed outcome. Native browser
coverage remains tracked separately under FST-050.

## 11. Related documents

- [Functional requirements](../requirements/FunctionalRequirements.md)
- [Non-functional requirements](../requirements/NonFunctionalRequirements.md)
- [Traceability](../requirements/Traceability.md)
- [Persistence](../data/Persistence.md)
- [API](../api/RESTAndWebSocket.md)
