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
the current x64 host: NSIS on Windows, DMG on macOS, and DEB plus AppImage on
Linux. The build first packages the backend and downloads the pinned Eclipse
Temurin JRE 17.0.21+9 archive. Its SHA-256 is checked against
`scripts/runtime-manifest.json` before the JAR and full JRE are staged as Tauri
resources. The release does not require Java installed on the user's machine.

Build on each target operating system; cross-compilation is not configured.
The Windows NSIS installer installs per user. macOS packages require macOS 12
or newer. Linux packages target Ubuntu 22.04-compatible systems. Artifacts are
written under `src-tauri/target/release/bundle/`. They are currently unsigned
local release builds, so operating systems may show their standard warning on
first launch.

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
