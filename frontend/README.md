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

Start the backend separately from `backend/` with `./mvnw spring-boot:run`,
then start the desktop shell:

```sh
cd frontend
npm install
npm run tauri:dev
```

This launches the same Vite frontend in a resizable FocusDesktop native window.
Rust stable and Tauri's platform-specific native dependencies are required.
On Debian/Ubuntu, install the packages listed in the repository's root README.

To build the desktop executable, run `npm run tauri:build`. This does not create
an installer or platform bundle. The app shell currently relies on a separately
running backend. Bundling and launching that backend with the desktop app is
planned in FST-040 and FST-041.

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
