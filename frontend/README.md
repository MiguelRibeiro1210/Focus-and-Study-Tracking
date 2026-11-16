# FocusDesktop frontend

React and TypeScript application scaffolded with Vite. The backend remains the
source of truth for session state. During development, Vite proxies `/api` and
`/ws` to `127.0.0.1:8080` and reads the backend bearer token from its local
`api-token` file. The token stays in the Vite process and is never included in
browser code or the production bundle.

Requires Node.js `^20.19.0` or `>=22.12.0`.

## Browser development

```sh
cd frontend
npm install
npm run dev
```

Vite listens on `127.0.0.1:5173` and proxies `/api` and `/ws` to the local
backend. The port is fixed so the Tauri shell can connect to the expected URL.

## Desktop development

Build the backend JAR once, then let the Tauri shell manage its process:

```sh
cd backend
./mvnw package -DskipTests
cd ../frontend
npm install
npm run tauri:dev
```

The shell starts the JAR on loopback port 8080, waits for `/health`, and only
then enables the timer UI. It passes `FOCUSDESKTOP_DATA_DIR` to the backend,
keeps the bearer token inside the Rust process, and shuts the sidecar down when
the desktop app exits. Java 17 is required for development. Rust stable and
Tauri's platform-specific native dependencies are also required. On
Debian/Ubuntu, install the packages listed in the repository's root README.

If the backend cannot start, the window shows the failure and the diagnostics
log path. The **Retry** action retries sidecar startup. To use a JAR at a
different path set `FOCUSDESKTOP_BACKEND_JAR`; to use a different Java command
set `FOCUSDESKTOP_JAVA`.

## Release packages

`npm run tauri:build` builds a self-contained release and native installer for
the current supported host: NSIS on Windows x64, DMG on macOS x64 or ARM64, and
DEB plus AppImage on Linux x64. The build packages the backend and downloads a
pinned Eclipse Temurin JRE 17.0.21+9 archive for that platform and architecture.
Its SHA-256 is checked against `scripts/runtime-manifest.json` before the JAR
and full JRE are staged as Tauri resources. The release does not require Java
installed on the user's machine.

Build on each target operating system; cross-compilation is not configured.
On macOS, build on an Intel Mac for x64 or an Apple Silicon Mac for ARM64.
Windows NSIS installs per user. macOS packages require macOS 12 or newer.
Linux packages target Ubuntu 22.04-compatible systems. Artifacts are written
under `src-tauri/target/release/bundle/`. macOS apps use ad-hoc signing and are
not notarized; the tester may need to approve the app in **System Settings →
Privacy & Security → Open Anyway** on first launch. Other local packages are
unsigned.

To create an installer without asking the tester to clone or build the project,
run the **Build and package FocusDesktop** workflow from GitHub Actions on the
ARM64 macOS runner. Download the `focusdesktop-macos-arm64-v0.1.0` artifact from
that run and send him the `.dmg`. Workflow artifacts are retained for 14 days;
the tester only needs the DMG.

Application data stays in the user's OS-specific FocusDesktop data directory
across upgrades. Install a newer package over the existing installation to
upgrade; do not remove that data directory if sessions, database, or token
must be retained. The package smoke checklist is in
`../docs/testing/FST-041-packaging.md`.

Set `FOCUSDESKTOP_DATA_DIR` to override the backend data directory. Otherwise
the frontend reads the token from the same OS-specific directory as the backend:
`%LOCALAPPDATA%/FocusDesktop` on Windows, `~/Library/Application Support/FocusDesktop`
on macOS, or `$XDG_DATA_HOME/focusdesktop` (falling back to
`~/.local/share/focusdesktop`) on Linux. The file is named `api-token`. If it
does not exist yet, Vite keeps running and returns HTTP 503 for API requests and
WebSocket upgrades; start the backend and retry.

## Build and lint

```sh
cd frontend
npm run build
npm run lint
npm test
```

## UI placeholders and future data integration

The Overview summary is intentionally rendered with zero-valued placeholders. It
does not represent measured activity yet. Its source is
`src/pages/futureData.ts`:

- `overviewSummary.focusSecondsToday` and `focusSecondsYesterday` feed today's
  focus total and its comparison.
- `overviewSummary.completedSessionsToday` and
  `overviewSummary.distractionsBlockedToday` feed the two activity counts.
- `overviewSummary.dailyGoalSeconds` feeds the goal total, remaining time, and
  progress percentage. The UI safely shows 0% while the goal is zero.

`src/timer/TimerScreen.tsx` formats and displays these values. To implement the
real summary later, provide values from a summary API or application state in
place of the zero-valued `overviewSummary`; keep durations in seconds internally.
The Overview's Work, Short, and Long inputs are local UI state in this component
and do not currently configure a timer session.

Other future integration points are also declared in `src/pages/futureData.ts`:

- `focusHistory` uses `FocusHistoryEntry` and feeds the History page and the
  Overview's recent-sessions table.
- `distractionRules` uses `DistractionRule` and feeds the Distractions table.
- `focusProfiles` uses `FocusProfile` for profile management.
- `timerProfiles` uses `TimerProfile` for saved timer presets.
- `ApplicationPreferences` describes the theme and automatic-next-session
  settings.

These collections are empty until backed by real data. History range selection,
date-range/export/status controls, and distraction profile management are UI
scaffolds; unsupported actions display “Not implemented yet.” The boundary
guidance in `src/pages/DistractionsPage.tsx` is static explanatory copy, not an
active blocking or tracking feature. Settings controls currently change only
their local presentation state and are not persisted or applied to the app.
