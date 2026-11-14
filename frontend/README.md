# FocusDesktop frontend

React and TypeScript application scaffolded with Vite. The backend remains the
source of truth for session state. During development, Vite proxies `/api` and
`/ws` to `127.0.0.1:8080` and reads the backend bearer token from its local
`api-token` file. The token stays in the Vite process and is never included in
browser code or the production bundle.

Requires Node.js `^20.19.0` or `>=22.12.0`.

## Development

```sh
cd frontend
npm install
npm run dev
```

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
