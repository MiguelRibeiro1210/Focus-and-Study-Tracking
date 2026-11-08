# ADR-005: Use H2 for the MVP database

**Status:** Accepted  
**Decision date:** 2026-11-08

## Context

The MVP needs a local relational database that can be embedded with the Java
backend and used consistently in development and integration tests.

## Decision

Use H2 in embedded file mode for the MVP. Access it through Spring Data JPA and
versioned migrations. SQLite remains a future option; adopting it requires a
separate compatibility decision and migration plan.

## Consequences

- The database file lives in the user's application-data directory.
- Production schema changes use migrations rather than Hibernate auto-update.
- Database backups and recovery are part of local data management.
