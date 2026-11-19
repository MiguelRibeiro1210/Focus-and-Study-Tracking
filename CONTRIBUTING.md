# Contributing to FocusDesktop

Thanks for helping improve FocusDesktop. The project is a local-first desktop
application with a Java/Spring Boot backend, a React/TypeScript frontend, and a
Tauri/Rust shell. The repository maintainer is listed in
[`CODEOWNERS`](.github/CODEOWNERS).

## Before you start

- Read [`AGENTS.md`](AGENTS.md) for repository structure, conventions, and the
  feature workflow.
- Check [`PROJECT_BOARD.md`](PROJECT_BOARD.md) and the relevant requirements in
  `docs/` before starting a planned feature. For a substantial story, agree on
  behavior and acceptance criteria before implementation.
- For bugs or feature proposals, use the matching GitHub issue form. Include an
  `FST-*` story ID when one exists.

## Development setup

Install JDK 17, Node.js `^20.19.0` or `>=22.12.0`, npm, and Rust stable for
desktop development. Linux desktop build dependencies are listed in the root
[`README.md`](README.md). Maven is supplied by the wrapper.

Run the backend checks from `backend/`:

```sh
./mvnw verify
```

Run the frontend checks from `frontend/`:

```sh
npm ci
npm run build
npm run lint
npm test
```

To run the desktop app during development, first build the backend JAR with
`./mvnw package -DskipTests` from `backend/`, then run `npm run tauri:dev` from
`frontend/`. See the root and frontend READMEs for platform setup and packaging.

## Code and tests

- Use four spaces in Java and two spaces in TypeScript, TSX, JSON, and YAML.
- Use `PascalCase` for Java types and React components; use `camelCase` for
  methods, fields, functions, and variables.
- Keep domain rules independent from Spring, JPA, and Tauri infrastructure.
- Add tests for meaningful behavior: JUnit 5 backend tests end in `Test.java`;
  Vitest frontend tests use `*.test.ts` or `*.test.tsx`.
- Avoid tests that only exercise trivial data holders. Run the checks relevant
  to your changes and include their results in the pull request.

## Privacy and local data

Core application features must work locally and must not make outbound network
calls. Keep API tokens, database files, logs containing personal data, and
other machine-specific state out of commits. Tests and screenshots must use
synthetic data; redact tokens and personal paths from issue attachments.

## Commits and pull requests

Use the repository's observed commit prefixes and a short scope where useful:

```text
feat(timer): describe the change
fix(history): describe the correction
docs(api): describe the documentation update
chore(ci): describe the maintenance change
```

Pull requests should explain the user-visible or technical change, link the
related issue/story, list tests and checks run, and include screenshots for UI
changes. Call out any known limitations or follow-up work.
