# Test Strategy

The test strategy protects the timer, privacy and read-model guarantees. Tests
are layered: fast domain tests for invariants, application tests with fakes,
integration tests for adapters, and a small E2E suite for desktop flows.

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

## 10. Related documents

- [Functional requirements](../requirements/FunctionalRequirements.md)
- [Non-functional requirements](../requirements/NonFunctionalRequirements.md)
- [Traceability](../requirements/Traceability.md)
- [Persistence](../data/Persistence.md)
- [API](../api/RESTAndWebSocket.md)
