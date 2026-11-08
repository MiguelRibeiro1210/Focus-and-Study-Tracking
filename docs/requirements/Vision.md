# Vision and Scope

## 1. Product summary

**FocusDesktop** is a local-first desktop application that helps students,
developers and knowledge workers run deliberate focus sessions, avoid digital
distractions and review how they actually spend their working time. It combines
a precise session timer, session history, streaks and productivity analytics in
one native desktop experience. All personal data stays on the user's machine.

The product is delivered as two cooperating processes:

- a **desktop client** (React + TypeScript inside a Tauri shell) that owns the
  user experience;
- a **local core engine** (Java 17 + Spring Boot) that owns the domain logic,
  persistence and OS integrations.

The two processes communicate over a loopback-only REST API and a WebSocket
channel. There is no cloud account, no telemetry and no mandatory network
connection.

## 2. Problem

Knowledge work happens on the same computer that provides entertainment,
messaging and endless notifications. Existing productivity tools are usually
mobile-first, cloud-first or browser-first. That creates three problems:

1. **Fragmentation** — the timer, the distraction blocker and the analytics live
   in different products, so the data never tells one coherent story.
2. **Privacy** — cloud dashboards require uploading detailed records of what the
   user studied, when and for how long.
3. **Context switching** — opening a web app to start a session is itself a
   distraction, and browser-based timers are unreliable when tabs are throttled.

## 3. Target users

| Persona | Context | Primary needs |
|---|---|---|
| **Student** | Exam preparation, coursework, revision | Long study blocks, break reminders, streak motivation, subject-level history |
| **Developer** | Deep work, debugging, code reviews | Reliable timer, distraction blocking, session analytics, low resource usage |
| **Professional** | Focused individual work in an office or at home | Privacy, history, weekly productivity trends, minimal setup |
| **Privacy-conscious user** | Any of the above | Local-only storage, no account, no telemetry, explicit data controls |

### Primary actor

The **Learner** is the person using the application. The term is used in the
documentation for any of the personas above.

### Secondary actors

- **Operating System** — supplies the active window/application and enforces
  blocking requests.
- **Timer / Scheduler** — drives automatic interval completion while a session
  is running.

## 4. Value proposition

> *One private desktop workspace for planning focus, protecting it from
> distractions and understanding it afterwards.*

Differentiators:

- **Local-first by design** — the core product works offline and stores data in
  a local database.
- **One coherent model** — sessions, intervals, distractions and daily progress
  share the same domain language.
- **Native reliability** — a background process owns timing and persistence, so
  the timer survives UI reloads and browser throttling.
- **Explainable analytics** — every number can be traced back to sessions and
  intervals; there is no opaque scoring service.
- **Extensible architecture** — DDD boundaries make it possible to add blocking,
  screen capture and content recognition without rewriting the core.

## 5. Goals

| ID | Goal | Measure |
|---|---|---|
| G-01 | Make starting a focus session effortless | Start action reachable in ≤ 2 clicks from the timer screen |
| G-02 | Keep timing trustworthy | Drift ≤ 1 second over a 2-hour session |
| G-03 | Make progress visible | Daily streak and weekly focus totals available on the dashboard |
| G-04 | Protect focus | Phase 2 can detect and block configured distracting applications |
| G-05 | Preserve privacy | No outbound network calls in the core runtime; all data local |
| G-06 | Stay maintainable | Domain layer independent of Spring/infrastructure, enforced by tests |

## 6. Non-goals

- Team collaboration, shared dashboards or manager reporting.
- Cloud sync, multi-device accounts or web access to personal data.
- Mobile applications.
- Gamification beyond streaks and focus scores.
- Automatic grading, tutoring or learning-content recommendations.
- Surveillance of employees or students; the product is user-owned and
  user-controlled.

## 7. Scope by phase

### MVP (Phase 1)

- Start, pause, resume and stop a focus session.
- Automatic work/break interval transitions, including long breaks.
- Configurable timer durations and auto-start behaviour.
- Session history with date filtering and pagination.
- Daily streak and current/longest streak.
- Productivity analytics: focus time, completed sessions, average session,
  focus score and distraction count.
- Local persistence and recovery of an active session after restart.
- Local-only API with a per-installation token.
- Desktop UI packaged with Tauri.

### Phase 2

- Active-window detection while a session is running.
- Blocking rules for applications and window-title patterns.
- Block, warn or log actions.
- Distraction log and review screen.
- Analytics breakdown by distraction category.

### Phase 3 (future)

- Optional local screen capture during sessions.
- Content recognition to identify study material.
- Automatic session categorization.
- Data export in JSON/CSV and richer retention controls.

## 8. Success metrics

These are product-level indicators, measured locally and never uploaded:

| Metric | Target |
|---|---|
| Session start success rate | ≥ 99% of attempts without an active session |
| Timer drift | ≤ 1 s over 2 hours |
| Crash-free sessions | ≥ 99.5% |
| History query latency | < 300 ms for 10,000 sessions |
| Daily active use (personal benchmark) | ≥ 3 sessions/day for engaged users |
| Privacy audit | 0 outbound calls in core runtime tests |

## 9. Assumptions

- The user runs a supported desktop OS (Windows 10/11, macOS 12+, modern Linux).
- The application is installed locally and has permission to read the active
  window (Phase 2) and, optionally, capture the screen (Phase 3).
- The local database is a single-user store; there is no concurrent multi-user
  access.
- A system clock is available. Streaks and daily aggregates use the operating
  system's current time zone; changing it changes how instants map to local
  calendar dates, including historical views.
- Phase 2 OS integrations may be limited by platform security policies; the
  product degrades gracefully when a capability is unavailable.

## 10. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| OS APIs differ or are restricted | Blocking is unreliable on some platforms | Adapter per OS, capability detection, no-op fallback, clear UI messaging |
| Timer drift or sleep/suspend | Incorrect session durations | Timestamp-based calculations, monotonic clock where possible, reconciliation on resume |
| Database corruption | Loss of history | Local backups, WAL/journal mode, integrity check on startup |
| Scope creep into cloud features | Privacy promise weakened | ADR-001 and privacy tests in CI |
| UI and backend get out of sync | Inconsistent state | Single source of truth in backend, WebSocket reconciliation, contract tests |
| Over-engineering the MVP | Delayed first release | Phase gates, backlog priorities, YAGNI on infrastructure |

## 11. Stakeholders

- **Product owner / maintainer** — prioritises the backlog and protects scope.
- **Contributors** — implement backend, frontend, OS adapters and tests.
- **Users** — own their data and decide whether to enable optional capabilities.
- **Reviewers** — verify architecture, privacy and documentation consistency.

## 12. Related documents

- [Functional requirements](FunctionalRequirements.md)
- [Non-functional requirements](NonFunctionalRequirements.md)
- [Traceability](Traceability.md)
- [System architecture](../SystemArchitecture/Architecture.md)
- [Domain model](../DM/DomainModel.md)
- [Glossary](../glossary.md)
