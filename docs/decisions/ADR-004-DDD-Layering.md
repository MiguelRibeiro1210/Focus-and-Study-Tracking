# ADR-004: Keep domain, application and infrastructure layers separate

**Status:** Accepted  
**Decision date:** 2026-11-08

## Context

Timer and session rules must remain testable without Spring, persistence or
desktop APIs. The application also needs replaceable adapters for storage,
events and operating-system capabilities.

## Decision

Use three dependency layers: Infrastructure depends on Application, and
Application depends on Domain. Domain code has no dependency on Spring, JPA,
HTTP or OS adapters. Read-model projections consume domain events and are not
write-side aggregates.

## Consequences

- Use cases coordinate transactions and ports; adapters implement those ports.
- Architecture checks enforce the dependency direction.
- Domain events and the transactional outbox connect writes to projections and
  notifications without making the domain depend on infrastructure.
