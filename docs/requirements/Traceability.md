# Traceability Matrix

This matrix connects requirements, use cases, diagrams and project-board tasks.
It is intentionally explicit: when a requirement changes, the affected use
case, diagram and task should be visible in one place.

## 1. Functional requirements → use cases → diagrams → board

| Requirement | Use case | Sequence diagram | Board task |
|---|---|---|---|
| FR-SES-01 | UC-01 | [SD_StartSession](../SD/SD_StartSession.puml) | FST-005, FST-009, FST-014 |
| FR-SES-02 | UC-02 | [SD_PauseSession](../SD/SD_PauseSession.puml) | FST-010, FST-014 |
| FR-SES-03 | UC-03 | [SD_ResumeSession](../SD/SD_ResumeSession.puml) | FST-010, FST-014 |
| FR-SES-04 | UC-04 | [SD_StopSession](../SD/SD_StopSession.puml) | FST-010, FST-014 |
| FR-SES-05 | UC-05 | [SD_CompleteInterval](../SD/SD_CompleteInterval.puml), [SD_StartNextInterval](../SD/SD_StartNextInterval.puml) | FST-006, FST-038, FST-015 |
| FR-SES-06 | UC-06 | [SD_SkipBreak](../SD/SD_SkipBreak.puml) | FST-006, FST-038, FST-017 |
| FR-SES-07 | UC-01 | [SD_StartSession](../SD/SD_StartSession.puml) | FST-005, FST-009 |
| FR-SES-08 | UC-01 | [SD_StartSession](../SD/SD_StartSession.puml) | FST-005, FST-013 |
| FR-SES-09 | UC-01, UC-05 | [SD_StartSession](../SD/SD_StartSession.puml), [SD_CompleteInterval](../SD/SD_CompleteInterval.puml) | FST-006, FST-015 |
| FR-SES-10 | UC-01 | [SD_StartSession](../SD/SD_StartSession.puml) | FST-005 |
| FR-SES-11 | UC-04 | [SD_StopSession](../SD/SD_StopSession.puml) | FST-005, FST-011 |
| FR-HIS-01 | UC-04 | [SD_StopSession](../SD/SD_StopSession.puml) | FST-013 |
| FR-HIS-02 | UC-07 | [SD_QuerySessionHistory](../SD/SD_QuerySessionHistory.puml) | FST-012, FST-018 |
| FR-HIS-03 | UC-07 | [SD_QuerySessionHistory](../SD/SD_QuerySessionHistory.puml) | FST-012, FST-018 |
| FR-HIS-04 | UC-07 | [SD_QuerySessionHistory](../SD/SD_QuerySessionHistory.puml) | FST-018 |
| FR-HIS-05 | UC-07 | [SD_QuerySessionHistory](../SD/SD_QuerySessionHistory.puml) | FST-018 |
| FR-STR-01 | UC-08 | [SD_CalculateDailyStreak](../SD/SD_CalculateDailyStreak.puml) | FST-011, FST-018 |
| FR-STR-02 | UC-08 | [SD_CalculateDailyStreak](../SD/SD_CalculateDailyStreak.puml) | FST-011, FST-018 |
| FR-STR-03 | UC-08 | [SD_CalculateDailyStreak](../SD/SD_CalculateDailyStreak.puml) | FST-011 |
| FR-STR-04 | UC-08 | [SD_CalculateDailyStreak](../SD/SD_CalculateDailyStreak.puml) | FST-011 |
| FR-ANA-01 | UC-09 | [SD_ViewAnalytics](../SD/SD_ViewAnalytics.puml) | FST-019 |
| FR-ANA-02 | UC-09 | [SD_ViewAnalytics](../SD/SD_ViewAnalytics.puml) | FST-019 |
| FR-ANA-03 | UC-09 | [SD_ViewAnalytics](../SD/SD_ViewAnalytics.puml) | FST-019 |
| FR-ANA-04 | UC-09, UC-15 | [SD_ViewAnalytics](../SD/SD_ViewAnalytics.puml), [SD_ReviewDistractionLog](../SD/SD_ReviewDistractionLog.puml) | FST-019, FST-028 |
| FR-ANA-05 | UC-09 | [SD_ViewAnalytics](../SD/SD_ViewAnalytics.puml) | FST-019 |
| FR-SET-01 | UC-10 | [SD_ConfigureTimerSettings](../SD/SD_ConfigureTimerSettings.puml) | FST-020 |
| FR-SET-02 | UC-10 | [SD_ConfigureTimerSettings](../SD/SD_ConfigureTimerSettings.puml) | FST-020 |
| FR-SET-03 | UC-10 | [SD_ConfigureTimerSettings](../SD/SD_ConfigureTimerSettings.puml) | FST-020 |
| FR-SET-04 | UC-11 | [SD_ConfigureApplicationSettings](../SD/SD_ConfigureApplicationSettings.puml) | FST-020 |
| FR-SET-05 | UC-10 | [SD_ConfigureTimerSettings](../SD/SD_ConfigureTimerSettings.puml) | FST-020 |
| FR-SET-06 | UC-10 | [SD_ConfigureTimerSettings](../SD/SD_ConfigureTimerSettings.puml) | FST-005, FST-020 |
| FR-DIS-01 | UC-12 | [SD_ManageBlockingRules](../SD/SD_ManageBlockingRules.puml) | FST-028 |
| FR-DIS-02 | UC-12 | [SD_ManageBlockingRules](../SD/SD_ManageBlockingRules.puml) | FST-028 |
| FR-DIS-03 | UC-13 | [SD_DetectDistraction](../SD/SD_DetectDistraction.puml) | FST-027 |
| FR-DIS-04 | UC-13 | [SD_DetectDistraction](../SD/SD_DetectDistraction.puml) | FST-028 |
| FR-DIS-05 | UC-14 | [SD_BlockDistraction](../SD/SD_BlockDistraction.puml) | FST-028 |
| FR-DIS-06 | UC-13, UC-15 | [SD_DetectDistraction](../SD/SD_DetectDistraction.puml), [SD_ReviewDistractionLog](../SD/SD_ReviewDistractionLog.puml) | FST-008, FST-028 |
| FR-DIS-07 | UC-15 | [SD_ReviewDistractionLog](../SD/SD_ReviewDistractionLog.puml) | FST-028 |
| FR-DIS-08 | UC-13 | [SD_DetectDistraction](../SD/SD_DetectDistraction.puml) | FST-027 |
| FR-DIS-09 | UC-14 | [SD_BlockDistraction](../SD/SD_BlockDistraction.puml) | FST-028 |
| FR-PRV-01 | all | [System architecture](../SystemArchitecture/SystemArchitecture.puml) | FST-004, FST-013 |
| FR-PRV-02 | all | [Architecture](../SystemArchitecture/Architecture.md) | FST-026 |
| FR-PRV-03 | all | [REST API](../api/RESTAndWebSocket.md) | FST-014 |
| FR-PRV-04 | UC-11 | [SD_ConfigureApplicationSettings](../SD/SD_ConfigureApplicationSettings.puml) | FST-020 |
| FR-PRV-05 | UC-11 | [SD_ConfigureApplicationSettings](../SD/SD_ConfigureApplicationSettings.puml) | FST-020 |
| FR-PRV-06 | — | — | FST-036 |
| FR-CAP-01 | UC-16 | — | FST-030 |
| FR-REC-01 | UC-17 | — | FST-029 |
| FR-REC-02 | UC-18 | — | FST-029 |
| FR-REC-03 | UC-16..18 | — | FST-026, FST-030 |
## 2. Use cases → requirements

| Use case | Requirements |
|---|---|
| UC-01 Start focus session | FR-SES-01, FR-SES-07, FR-SES-08, FR-SES-09, FR-SES-10 |
| UC-02 Pause session | FR-SES-02 |
| UC-03 Resume session | FR-SES-03 |
| UC-04 Stop session | FR-SES-04, FR-SES-11, FR-HIS-01 |
| UC-05 Complete interval | FR-SES-05, FR-SES-09 |
| UC-06 Skip break | FR-SES-06 |
| UC-07 View session history | FR-HIS-02..05 |
| UC-08 View daily streak | FR-STR-01..04 |
| UC-09 View productivity analytics | FR-ANA-01..05 |
| UC-10 Configure timer settings | FR-SET-01, FR-SET-02, FR-SET-03, FR-SET-05, FR-SET-06 |
| UC-11 Configure application settings | FR-SET-04, FR-PRV-04, FR-PRV-05 |
| UC-12 Manage blocking rules | FR-DIS-01, FR-DIS-02 |
| UC-13 Detect distracting application | FR-DIS-03, FR-DIS-04, FR-DIS-06, FR-DIS-08 |
| UC-14 Block distracting application | FR-DIS-05, FR-DIS-09 |
| UC-15 Review distraction log | FR-DIS-06, FR-DIS-07, FR-ANA-04 |
| UC-16 Capture session screenshot | FR-CAP-01, FR-REC-03 |
| UC-17 Recognize study content | FR-REC-01, FR-REC-03 |
| UC-18 Categorize session automatically | FR-REC-02, FR-REC-03 |

## 3. Non-functional requirements → verification

| NFR group | Verification artefact |
|---|---|
| NFR-PRV-* | Network-blocking integration test, privacy audit, security tests |
| NFR-SEC-* | MockMvc security tests, dependency scan, secret scan |
| NFR-PERF-* | API benchmarks, seeded query benchmarks, profiling |
| NFR-REL-* | Restart test, crash test, event replay test, E2E reconnect |
| NFR-PORT-* | CI matrix, installer smoke tests, offline E2E |
| NFR-USE-* | E2E tests, axe-core, manual UX review |
| NFR-MAINT-* | ArchUnit, JaCoCo, CI timing, PR checklist |
| NFR-OBS-* | Log review, diagnostics export test, health endpoint test |
| NFR-DATA-* | Retention test, backup/restore test, migration test |
| NFR-SCALE-* | Seeded benchmark, growth test |

## 4. Project-board alignment

| Board task | Documentation coverage |
|---|---|
| FST-003 Spring Boot entry point | [Architecture](../SystemArchitecture/Architecture.md) |
| FST-004 H2 datasource | [Persistence](../data/Persistence.md), [ADR-005](../decisions/ADR-005-H2VsSQLite.md) |
| FST-005 FocusSession aggregate | [Domain model](../DM/DomainModel.md) |
| FST-006 Timer domain service | [Domain model](../DM/DomainModel.md), [SD_CompleteInterval](../SD/SD_CompleteInterval.puml) |
| FST-008 DistractionLog read model | [Domain model](../DM/DomainModel.md), [Persistence](../data/Persistence.md) |
| FST-009 StartSessionUseCase | [SD_StartSession](../SD/SD_StartSession.puml) |
| FST-010 Pause/resume/stop use cases | [SD_PauseSession](../SD/SD_PauseSession.puml), [SD_ResumeSession](../SD/SD_ResumeSession.puml), [SD_StopSession](../SD/SD_StopSession.puml) |
| FST-011 CalculateDailyStreakUseCase | [SD_CalculateDailyStreak](../SD/SD_CalculateDailyStreak.puml) |
| FST-012 Session history query | [SD_QuerySessionHistory](../SD/SD_QuerySessionHistory.puml) |
| FST-013 JPA repositories | [Persistence](../data/Persistence.md) |
| FST-014 REST API | [REST and WebSocket API](../api/RESTAndWebSocket.md) |
| FST-015 WebSocket updates | [REST and WebSocket API](../api/RESTAndWebSocket.md), [SD_StartSession](../SD/SD_StartSession.puml) |
| FST-016 React frontend | [System architecture](../SystemArchitecture/Architecture.md) |
| FST-017 Timer screen | [Use case UC-01](../UseCases/UseCases.md) |
| FST-018 History and streaks view | [Use case UC-07/UC-08](../UseCases/UseCases.md) |
| FST-019 Analytics dashboard | [Use case UC-09](../UseCases/UseCases.md) |
| FST-020 Settings screen | [Use case UC-10/UC-11](../UseCases/UseCases.md) |
| FST-021 Frontend integration | [REST and WebSocket API](../api/RESTAndWebSocket.md) |
| FST-022 Tauri packaging | [ADR-003](../decisions/ADR-003-TauriVsElectron.md) |
| FST-023 Domain unit tests | [Test strategy](../testing/TestStrategy.md) |
| FST-024 REST integration tests | [Test strategy](../testing/TestStrategy.md) |
| FST-025 CI workflow | [Test strategy](../testing/TestStrategy.md) |
| FST-026 Privacy audit | [ADR-001](../decisions/ADR-001-LocalFirst.md), [Test strategy](../testing/TestStrategy.md) |
| FST-027 OS active-window detection | [SD_DetectDistraction](../SD/SD_DetectDistraction.puml) |
| FST-028 Distraction blocking | [SD_BlockDistraction](../SD/SD_BlockDistraction.puml), [SD_ManageBlockingRules](../SD/SD_ManageBlockingRules.puml) |
| FST-029 Content recognition | [Domain model](../DM/DomainModel.md) (future context) |
| FST-030 Screen recording | [Domain model](../DM/DomainModel.md) (future context) |
| FST-031 Documentation | This directory |
| FST-032 README structure | [Project README](../../README.md) |
| FST-034 Maven wrapper | [Architecture](../SystemArchitecture/Architecture.md) |
| FST-035 Docs structure | This directory |
| FST-036 JSON/CSV data export | [Functional requirements](FunctionalRequirements.md) |

## 5. Maintenance rule

When a requirement or use case changes:

1. update the requirement table;
2. update the matching use case specification;
3. update the sequence diagram or relevant design document if the interaction or model changed;
4. regenerate the SVG;
5. update this matrix and, if needed, the project board.

## 6. Related documents

- [Functional requirements](FunctionalRequirements.md)
- [Non-functional requirements](NonFunctionalRequirements.md)
- [Use case catalogue](../UseCases/UseCases.md)
- [Project board](../../PROJECT_BOARD.md)
