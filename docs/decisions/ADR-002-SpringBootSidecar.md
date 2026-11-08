# ADR-002: Run the Java core as a local sidecar

**Status:** Accepted  
**Decision date:** 2026-11-08

## Context

The desktop shell needs a reliable timer, persistence and OS integration while
keeping the user interface independent of domain code.

## Decision

Run the core engine as a local Java 17+ Spring Boot process alongside the Tauri
desktop client. The client communicates through the loopback REST and
WebSocket contracts. The backend owns session timing, domain rules and
persistence; the shell starts and supervises the process.

## Consequences

- The desktop bundle includes a compatible runtime and manages sidecar startup
  and shutdown.
- The local API is secured with loopback binding and a per-installation token.
- The UI can be replaced without coupling it to Java implementation details.
