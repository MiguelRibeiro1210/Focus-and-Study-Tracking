# Repository Guidelines

## Project Structure

`backend/` contains the Java 17 Spring Boot application, tests, and Flyway migrations. Source and tests are under `backend/src/main/java` and `backend/src/test/java`. `frontend/` contains the React/TypeScript UI, tests, Vite scripts, and Tauri shell in `frontend/src-tauri/`. Documentation is in `docs/`; `PROJECT_BOARD.md` tracks user stories.

## Build, Test, and Development

Run commands from the indicated directory:

- Backend: `cd backend && ./mvnw verify` compiles and tests the application.
- Frontend: from `frontend/`, run `npm ci`, `npm run dev`, `npm run build`, `npm run lint`, or `npm test` to install, develop, build, lint, or run Vitest.
- Desktop smoke: build the backend JAR, then run `cd frontend && npm run smoke:tauri` to open Tauri and check sidecar integration.

## Code and Test Conventions

Use four-space indentation in Java and two spaces in TypeScript/TSX. Java types and React components use `PascalCase`; methods and fields use `camelCase`. Frontend tests use `*.test.ts(x)`; backend tests use `*Test.java`. Preserve DDD boundaries: domain rules must not depend on Spring or persistence.

## Testing Changes

Backend tests use JUnit 5 and Spring Boot Test; frontend tests use Vitest. Cover meaningful domain, use-case, persistence, API, or UI behavior rather than trivial data holders.

## Commits and Pull Requests

Use the observed commit prefixes `feat`, `fix`, `docs`, or `chore`, optionally scoped, e.g. `feat(frontend): ...`. PRs should summarize behavior, link the `FST-*` story, list verification, and include UI screenshots when relevant.

## Feature Workflow and Security

These instructions apply only to this repository. For each feature or user story:

1. Inspect the current frontend, backend, requirements, relevant documentation, tests, and `PROJECT_BOARD.md` before proposing changes.
2. Compare the UI and API contracts with domain rules. Identify mismatches and missing decisions; ask the user about material unresolved product choices.
3. Update relevant source documentation to reflect agreed behavior before implementation. Update diagrams only when their documented flows change.
4. Implement the agreed scope and add or update tests for meaningful behavior. Do not write tests that only exercise trivial data holders.
5. Run relevant tests and checks, inspect the diff, and report what changed and what passed or failed.
6. Update `PROJECT_BOARD.md` at the end. Keep one story In Progress, and mark work Done only when its acceptance criteria and verification are complete.

Never commit API tokens, local data, or secrets. The API is local-only and uses a per-installation bearer token.
