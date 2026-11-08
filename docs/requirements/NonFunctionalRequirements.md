# Non-Functional Requirements

These requirements define the quality attributes of FocusDesktop. They are
verified by automated tests, manual checks or architecture reviews. Targets are
for the MVP unless a phase is stated.

## 1. Privacy and security

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-PRV-01 | Privacy | The core runtime makes no outbound network connections. | 0 non-loopback sockets in the MVP test suite | Network-blocking integration test, manual audit |
| NFR-PRV-02 | Privacy | The local API binds to `127.0.0.1` and requires a per-installation token. | All endpoints except `/health` require the token | MockMvc security tests |
| NFR-PRV-03 | Privacy | Only data needed for the product is stored. | No keystrokes, clipboard, browsing history or file contents | Schema review, privacy audit |
| NFR-PRV-04 | Privacy | The learner can delete all personal data. | Full deletion in ≤ 2 seconds for 10k sessions | Integration test |
| NFR-PRV-05 | Privacy | No telemetry, analytics SDK or crash uploader is bundled. | 0 outbound calls | Dependency review, network test |
| NFR-SEC-01 | Security | Local API requests from non-loopback origins are rejected. | `403` for non-loopback origins | Integration test |
| NFR-SEC-02 | Security | The token is generated per installation and stored with user-only permissions. | File mode `0600` on Unix | Startup test |
| NFR-SEC-03 | Security | No secrets, tokens or personal data are committed to the repository. | 0 findings | Secret scanning in CI |
| NFR-SEC-04 | Security | Dependencies are scanned for known vulnerabilities. | No high/critical findings at release | CI dependency scan |

## 2. Performance and resource usage

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-PERF-01 | Performance | Starting a session responds quickly. | p95 < 200 ms | API benchmark |
| NFR-PERF-02 | Performance | Timer accuracy is independent of UI rendering. | Drift ≤ 1 s over 2 hours | Domain + integration test with fake clock |
| NFR-PERF-03 | Performance | History query for 10,000 sessions is fast. | p95 < 300 ms | Seeded benchmark |
| NFR-PERF-04 | Performance | Analytics for 366 days is fast. | p95 < 500 ms | Seeded benchmark |
| NFR-PERF-05 | Performance | Idle memory usage is modest. | Backend < 250 MB RSS; UI < 150 MB | Manual profiling |
| NFR-PERF-06 | Performance | Idle CPU usage is negligible. | < 1% average on a modern laptop | Manual profiling |
| NFR-PERF-07 | Performance | UI updates are responsive. | Timer tick rendered within 100 ms of receipt | Manual + E2E timing |
| NFR-PERF-08 | Performance | Database size stays reasonable. | < 50 MB for 5 years of typical use | Seeded growth test |

## 3. Reliability and data integrity

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-REL-01 | Reliability | An active session survives an application restart. | Recovered within 1 s of startup | Integration test |
| NFR-REL-02 | Reliability | At most the last few seconds of a running session can be lost on crash. | ≤ 5 s of unpersisted elapsed time | Crash/restart test |
| NFR-REL-03 | Reliability | Database integrity is checked on startup. | Corrupt DB is detected and reported | Fault-injection test |
| NFR-REL-04 | Reliability | Shutdown is graceful and flushes pending writes. | No partial session writes | Integration test |
| NFR-REL-05 | Reliability | Duplicate event delivery does not corrupt read models. | Projections are idempotent | Replay test |
| NFR-REL-06 | Reliability | A failed OS block does not crash the backend. | Error surfaced and logged | Adapter test with failing port |
| NFR-REL-07 | Reliability | The UI can reconnect to the WebSocket after a drop. | Reconnect within 5 s | E2E test |

## 4. Portability and deployment

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-PORT-01 | Portability | Support Windows 10/11. | Installer and smoke test | CI matrix / manual |
| NFR-PORT-02 | Portability | Support macOS 12+. | Installer and smoke test | CI matrix / manual |
| NFR-PORT-03 | Portability | Support modern Linux (Ubuntu 22.04+). | AppImage/deb and smoke test | CI matrix / manual |
| NFR-PORT-04 | Portability | The application runs without administrator privileges. | Standard user install | Manual test |
| NFR-PORT-05 | Portability | The backend runs on Java 17+. | `java -version` check in installer | Packaging test |
| NFR-PORT-06 | Portability | No internet connection is required after installation. | Full MVP flow offline | Offline E2E test |

## 5. Usability and accessibility

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-USE-01 | Usability | Core session actions are keyboard accessible. | Start/pause/resume/stop have shortcuts | Manual + E2E |
| NFR-USE-02 | Usability | The UI explains why an action is unavailable. | Error messages include a code and a human-readable reason | UX review |
| NFR-USE-03 | Usability | The timer remains readable at a glance. | Remaining time is the largest element on the screen | UX review |
| NFR-USE-04 | Accessibility | Text contrast meets WCAG 2.1 AA. | ≥ 4.5:1 for body text | Automated contrast check |
| NFR-USE-05 | Accessibility | Interactive controls have accessible names. | 100% of controls | axe-core scan |
| NFR-USE-06 | Usability | Empty, loading and error states are designed. | No blank screens | UX review |
| NFR-USE-07 | Usability | The app respects reduced-motion preferences. | Animations disabled when requested | Manual test |

## 6. Maintainability and architecture

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-MAINT-01 | Maintainability | The domain layer has no dependency on Spring or infrastructure. | 0 violations | ArchUnit test |
| NFR-MAINT-02 | Maintainability | Domain logic is covered by unit tests. | ≥ 85% line coverage in `domain` | JaCoCo report |
| NFR-MAINT-03 | Maintainability | Overall backend coverage is healthy. | ≥ 75% line coverage | JaCoCo report |
| NFR-MAINT-04 | Maintainability | The build is fast enough for a tight loop. | Clean build + tests < 5 min | CI timing |
| NFR-MAINT-05 | Maintainability | Public APIs are versioned. | `/api/v1` prefix | Contract review |
| NFR-MAINT-06 | Maintainability | Use cases are one class per intention. | No god services | Code review |
| NFR-MAINT-07 | Maintainability | Documentation and diagrams are updated with behaviour changes. | PR checklist | Review |
| NFR-MAINT-08 | Maintainability | Formatting and static analysis are automated. | CI fails on violations | CI |

## 7. Observability and diagnostics

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-OBS-01 | Observability | Logs are local and structured. | JSON or key-value logs with level and timestamp | Log review |
| NFR-OBS-02 | Observability | The learner can export a diagnostics bundle. | Logs + version + OS info, no personal content | Manual test |
| NFR-OBS-03 | Observability | Domain events are traceable in logs at debug level. | Event type + aggregate id | Log review |
| NFR-OBS-04 | Observability | The app exposes a local health endpoint. | `GET /health` returns status and version | Integration test |

## 8. Data management

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-DATA-01 | Data | Retention is configurable. | 7..3650 days or keep forever | Integration test |
| NFR-DATA-02 | Data | The local database can be backed up and restored. | Copy while app is closed; integrity check on restore | Manual test |
| NFR-DATA-03 | Data | Export produces a portable format. | JSON and CSV | Contract test (Phase 3) |
| NFR-DATA-04 | Data | Schema migrations are versioned. | Flyway migrations, forward-only | Migration test |
| NFR-DATA-05 | Data | Timestamps are stored in UTC. | All `TIMESTAMP` columns UTC | Schema review |
| NFR-DATA-06 | Data | Dates used for streaks and daily aggregates are local calendar dates. | UTC instants are mapped using the operating system's current time zone; zone changes remap historical dates on rebuild/query | Domain test |

## 9. Scale assumptions

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-SCALE-01 | Scale | Support at least 10,000 sessions. | Queries stay within NFR-PERF-03 | Seeded benchmark |
| NFR-SCALE-02 | Scale | Support at least 5 years of history. | DB size within NFR-PERF-08 | Growth test |
| NFR-SCALE-03 | Scale | Single-user, single-installation. | No multi-tenant concerns | Architecture review |

## 10. Compliance and licensing

| ID | Category | Requirement | Target | Verification |
|---|---|---|---|---|
| NFR-COMP-01 | Compliance | Third-party dependencies use permissive licences compatible with MIT. | No copyleft in distributed artefacts | Licence scan |
| NFR-COMP-02 | Compliance | The product does not claim medical or therapeutic benefits. | Copy review | Review |
| NFR-COMP-03 | Compliance | OS integrations respect platform permission prompts. | No silent permission bypass | Manual test |

## 11. Verification summary

| Level | Scope | Tooling |
|---|---|---|
| Unit | Domain invariants, VOs, calculators | JUnit 5, AssertJ |
| Application | Use-case orchestration with fakes | JUnit 5, Mockito/fakes |
| Integration | Persistence, REST, WebSocket, security | Spring Boot Test, MockMvc, H2 |
| Architecture | Dependency rule | ArchUnit |
| E2E | Desktop flows | Playwright + Tauri driver |
| Performance | Latency and memory | JMH / seeded benchmarks |
| Privacy | No outbound calls | Network sandbox test |

## 12. Related documents

- [Functional requirements](FunctionalRequirements.md)
- [Test strategy](../testing/TestStrategy.md)
- [Architecture](../SystemArchitecture/Architecture.md)
- [ADR-001 Local-first](../decisions/ADR-001-LocalFirst.md)
