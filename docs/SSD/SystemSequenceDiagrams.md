# System Sequence Diagrams

System sequence diagrams (SSDs) are black-box views: they show an actor and the
FocusDesktop system boundary, without controllers, use cases or database
internals. They are useful for validating scope and actor intent before reading
the detailed [`SD/`](../SD/) flows.

## 1. Diagram index

| Flow | Actor(s) | Source | Render | Scope |
|---|---|---|---|---|
| Start session | Learner | [`SSD_StartSession.puml`](SSD_StartSession.puml) | [`SSD_StartSession.svg`](SSD_StartSession.svg) | Start a focus session and receive the timer state. |
| Pause / resume session | Learner | [`SSD_PauseResumeSession.puml`](SSD_PauseResumeSession.puml) | [`SSD_PauseResumeSession.svg`](SSD_PauseResumeSession.svg) | Pause and resume without losing remaining time. |
| Stop session | Learner | [`SSD_StopSession.puml`](SSD_StopSession.puml) | [`SSD_StopSession.svg`](SSD_StopSession.svg) | Stop the active session and record focus time. |
| History and streak | Learner | [`SSD_HistoryAndStreak.puml`](SSD_HistoryAndStreak.puml) | [`SSD_HistoryAndStreak.svg`](SSD_HistoryAndStreak.svg) | Query history and view the current/longest streak. |
| Analytics | Learner | [`SSD_Analytics.puml`](SSD_Analytics.puml) | [`SSD_Analytics.svg`](SSD_Analytics.svg) | View productivity analytics and daily breakdown. |
| Settings | Learner | [`SSD_Settings.puml`](SSD_Settings.puml) | [`SSD_Settings.svg`](SSD_Settings.svg) | Configure timer, application and retention settings. |
| Blocking rules | Learner | [`SSD_BlockingRules.puml`](SSD_BlockingRules.puml) | [`SSD_BlockingRules.svg`](SSD_BlockingRules.svg) | Manage the active blocking policy and its rules. |

The Phase 2 monitoring and block flows are documented as detailed sequence
diagrams, not as SSDs, because the Operating System is an actor and the flows
are internal to the running system:

- [`SD_DetectDistraction.puml`](../SD/SD_DetectDistraction.puml)
- [`SD_BlockDistraction.puml`](../SD/SD_BlockDistraction.puml)

## 2. How to read an SSD

1. Start at the actor on the left.
2. Each arrow into the system is a user goal or a scheduler trigger.
3. Dashed arrows are responses or asynchronous notifications.
4. Each diagram shows one successful scenario and assumes its preconditions hold.
5. The SSD does not imply a particular internal design; use the matching
   detailed sequence diagram for that.

## 3. Relationship to use cases

| SSD | Use cases |
|---|---|
| Start session | UC-01 |
| Pause / resume session | UC-02, UC-03 |
| Stop session | UC-04 |
| History and streak | UC-07, UC-08 |
| Analytics | UC-09 |
| Settings | UC-10, UC-11 |
| Blocking rules | UC-12 |

UC-05, UC-06, UC-13, UC-14 and UC-15 are timer/OS-driven or query flows; they
have detailed sequence diagrams in [`../SD/`](../SD/) and are not duplicated as
SSDs.

## 4. Related documents

- [Use case catalogue](../UseCases/UseCases.md)
- [Sequence diagrams](../SD/SequenceDiagrams.md)
- [System architecture](../SystemArchitecture/Architecture.md)
