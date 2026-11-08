# FocusDesktop Documentation

> Local-first desktop productivity environment for tracking focus sessions,
> blocking digital distractions and understanding work habits without sending
> personal data to the cloud.

This directory is the entry point for the project documentation. It is written
for contributors, reviewers and future maintainers. The diagrams are PlantUML
sources (`.puml`) with rendered SVG versions (`.svg`); the PlantUML files are
the source of truth.

## Documentation map

| Area | Document | What it answers |
|---|---|---|
| Requirements | [Vision](requirements/Vision.md) | Why the product exists, for whom, and what is in/out of scope |
| Requirements | [Functional requirements](requirements/FunctionalRequirements.md) | What the system must do, with priorities and acceptance criteria |
| Requirements | [Non-functional requirements](requirements/NonFunctionalRequirements.md) | Privacy, performance, reliability, portability and quality targets |
| Requirements | [Traceability](requirements/Traceability.md) | How requirements map to use cases, diagrams and project-board tasks |
| Architecture | [System architecture](SystemArchitecture/Architecture.md) | Containers, layers, runtime flows, deployment and privacy model |
| Architecture | [System architecture diagram](SystemArchitecture/SystemArchitecture.svg) | Visual overview of the hybrid desktop architecture |
| Use cases | [Use case catalogue](UseCases/UseCases.md) | Actors, use case catalogue and detailed specifications |
| Use cases | [Use case diagram](UseCases/UseCases.svg) | Actor/use-case overview |
| Domain | [Domain model](DM/DomainModel.md) | Bounded contexts, aggregates, value objects, invariants and events |
| Domain | [Domain model diagram](DM/DM.svg) | Conceptual domain model |
| Domain | [Session state machine](StateMachines/FocusSessionState.svg) | Allowed focus-session transitions |
| Sequences | [Sequence diagrams](SD/SequenceDiagrams.md) | Use-case flows with controllers, use cases, domain and adapters |
| Sequences | [System sequence diagrams](SSD/SystemSequenceDiagrams.md) | Actor/system black-box interactions |
| API | [REST and WebSocket API](api/RESTAndWebSocket.md) | Endpoints, DTOs, errors and live timer events |
| Data | [Persistence](data/Persistence.md) | Schema, indexes, transactions, retention and migrations |
| Quality | [Test strategy](testing/TestStrategy.md) | Test levels, privacy audit, coverage and CI gates |
| Decisions | [ADR-001 Local-first](decisions/ADR-001-LocalFirst.md) | Why the product never depends on a cloud service |
| Decisions | [ADR-002 Spring Boot sidecar](decisions/ADR-002-SpringBootSidecar.md) | Why the core engine is a local Java process |
| Decisions | [ADR-003 Tauri vs Electron](decisions/ADR-003-TauriVsElectron.md) | Desktop shell choice and trade-offs |
| Decisions | [ADR-004 DDD layering](decisions/ADR-004-DDD-Layering.md) | Dependency rule and package structure |
| Decisions | [ADR-005 H2 vs SQLite](decisions/ADR-005-H2VsSQLite.md) | Local database choice for the MVP |
| Reference | [Glossary](glossary.md) | Ubiquitous language and technical terms |

## Reading paths

**New contributor**

1. [Vision](requirements/Vision.md)
2. [System architecture](SystemArchitecture/Architecture.md)
3. [Domain model](DM/DomainModel.md)
4. [Use case catalogue](UseCases/UseCases.md)
5. The relevant sequence diagram in [`SD/`](SD/)
6. [Test strategy](testing/TestStrategy.md)

**Backend developer**

1. [Domain model](DM/DomainModel.md)
2. [System architecture](SystemArchitecture/Architecture.md)
3. [REST and WebSocket API](api/RESTAndWebSocket.md)
4. [Persistence](data/Persistence.md)
5. [ADR-004 DDD layering](decisions/ADR-004-DDD-Layering.md)

**Frontend developer**

1. [System architecture](SystemArchitecture/Architecture.md)
2. [Use case catalogue](UseCases/UseCases.md)
3. [REST and WebSocket API](api/RESTAndWebSocket.md)
4. [System sequence diagrams](SSD/SystemSequenceDiagrams.md)

**Reviewer / architect**

1. [Vision](requirements/Vision.md)
2. [Non-functional requirements](requirements/NonFunctionalRequirements.md)
3. [Architecture](SystemArchitecture/Architecture.md)
4. [ADRs](decisions/)
5. [Traceability](requirements/Traceability.md)

## Diagram conventions

- **Source of truth:** every `.svg` has a sibling `.puml`. Edit the `.puml`,
  then regenerate the SVG.
- **Titles:** diagrams start with `title <Type> - <Subject>` so they remain
  identifiable when exported.
- **Identifiers:** requirements use `FR-*` / `NFR-*`, use cases use `UC-*`,
  architecture decisions use `ADR-*`, and project-board tasks use `FST-*`.
- **Phases:** `MVP` is the first usable release; `Phase 2` covers distraction
  blocking; `Phase 3` covers content intelligence and is documented as future
  scope.
- **Layers:** diagrams use the DDD names *Domain*, *Application* and
  *Infrastructure* consistently.
- **Sequence diagrams:** messages are numbered (`autonumber`), error paths are
  explicit (`alt`/`else`), and transactional outbox events are dispatched only
  after the domain transaction commits.
- **Colours:** diagrams intentionally use the default PlantUML palette with
  light grey fills so they print well and match the examples in
  [`DocExamples/`](DocExamples/).

## Documentation status

| Area | Status | Notes |
|---|---|---|
| Vision and requirements | Complete | Ready for review |
| Use cases | Complete | 18 use cases, 15 specified in detail |
| Domain model | Complete | 5 bounded contexts, including Progress & Analytics as read models |
| Architecture | Complete | Runtime, deployment and read-model projection views |
| Sequence diagrams | Complete | 16 diagrams across 15 detailed flows, including pending-interval start and read-model projections |
| System sequence diagrams | Complete | 7 actor/system flows |
| API | Complete | REST + WebSocket contract |
| Persistence | Complete | ER model and table definitions |
| Test strategy | Complete | Levels, privacy audit and CI gates |
| ADRs | Complete | 5 decisions recorded |

## Related documents

- [Project README](../README.md) — product overview and setup instructions
- [Project board](../PROJECT_BOARD.md) — task tracking with `FST-*` identifiers
- [Documentation examples](DocExamples/) — reference diagrams supplied with the
  project; not part of the product documentation set
