# ADR-003: Use Tauri for the desktop shell

**Status:** Accepted  
**Decision date:** 2026-11-08

## Context

FocusDesktop needs a native desktop window and access to platform capabilities,
while the interface is built with web technologies.

## Decision

Use Tauri with a React and TypeScript client. The Java backend remains a local
sidecar and the UI uses the documented HTTP/WebSocket API.

## Consequences

- The desktop project requires the Rust/Tauri toolchain.
- OS-specific integrations must remain behind adapter interfaces.
- The product documentation treats Electron as a rejected alternative for the
  current architecture, not as a supported packaging option.
