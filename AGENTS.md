# Repository Guidelines

## Project Structure

`backend/` contains the Java 17 Spring Boot application, tests, and Flyway migrations. `frontend/` contains the React/TypeScript UI, Vite scripts, tests, and Tauri Rust shell in `frontend/src-tauri/`. Documentation is in `docs/`; `PROJECT_BOARD.md` tracks user stories.

## Build, Test, and Development

Run commands from the indicated directory:

- Backend: `cd backend && ./mvnw verify` compiles and tests the application.
- Frontend: from `frontend/`, run `npm ci`, `npm run dev`, `npm run build`, `npm run lint`, or `npm test` to install, develop, build, lint, or run Vitest.
- Desktop smoke: build the backend JAR, then run `cd frontend && npm run smoke:tauri` to open Tauri and check sidecar integration.

## Code and Test Conventions

Use four-space Java and two-space TypeScript/TSX indentation. Types/components use `PascalCase`; methods/fields use `camelCase`. Frontend tests use `*.test.ts(x)`; backend tests use `*Test.java`. Keep domain rules independent of Spring and persistence.

## Testing Changes

Backend tests use JUnit 5 and Spring Boot Test; frontend tests use Vitest. Cover meaningful domain, use-case, persistence, API, or UI behavior rather than trivial data holders.

## Commits and Pull Requests

Use the observed commit prefixes `feat`, `fix`, `docs`, or `chore`, optionally scoped, e.g. `feat(frontend): ...`. PRs should summarize behavior, link the `FST-*` story, list verification, and include UI screenshots when relevant.

## Feature Workflow and Security

These instructions apply only to this repository. For each feature or user story:

1. Inspect the existing frontend, backend, requirements, documentation, tests, and `PROJECT_BOARD.md`. Compare UI and API contracts with the domain rules and identify gaps or conflicting decisions.
2. Review the design and implementation choices with the user, ask about material unresolved product decisions, and agree on an implementation plan before coding.
3. Record the agreed decisions in the relevant source documentation. If a decision changes the design, recheck and revise the frontend design against those decisions before implementation. Update diagrams only when their flows change.
4. Implement the backend behavior and meaningful backend tests first. Then implement the corresponding frontend behavior and meaningful frontend tests. Do not test trivial data holders.
5. Run the relevant tests and checks, inspect the diff, and report what changed and what passed or failed.
6. Update `PROJECT_BOARD.md` last. Keep one story In Progress, and mark work Done only when its acceptance criteria and verification are complete.

Never commit API tokens, local data, or secrets. The API is local-only and uses a per-installation bearer token.
