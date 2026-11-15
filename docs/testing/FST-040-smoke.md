# FST-040 sidecar smoke check

The automated smoke runner launches the Tauri development shell and exercises
the sidecar over its real local REST and WebSocket transports. It uses a fresh
temporary data directory and removes it when the run finishes.

Build the backend JAR, then run the smoke from `frontend/`:

```sh
cd backend
./mvnw package -DskipTests
cd ../frontend
npm run smoke:tauri
```

The runner covers:

- Tauri starts the backend and waits until `/health` responds.
- An authenticated REST request starts a session.
- The authenticated WebSocket receives the matching `session.started` event.
- Closing and reopening the Tauri shell stops and restarts its backend; the
  active session is recovered from the same application-data directory.
- Shutdown uses the authenticated private lifecycle endpoint, with an OS
  process-signal fallback if the backend is not responding.
- The recovered session can be stopped, and closing the shell releases port
  8080.

The runner opens the Tauri window and requires a working graphical session. It
currently automates process-group shutdown on macOS/Linux. On Windows, run the
same checks manually:

1. Launch `npm run tauri:dev` after building the backend JAR. Confirm the timer
   appears only after the backend is ready.
2. Start a session and confirm it counts down and receives live updates.
3. Close the app window. Confirm the backend no longer responds on port 8080.
4. Launch the app again. Confirm the active session is recovered.
5. Stop the session, close the app, and verify no backend process remains.
6. Repeat with port 8080 occupied or the JAR unavailable. Confirm the startup
   screen gives an actionable error, exposes the diagnostics log path, and its
   Retry action starts the backend after the issue is corrected.

The backend log is `backend.log` in the same per-user data directory as the
database and API token. The runner uses `FOCUSDESKTOP_DATA_DIR` to isolate its
data and does not inspect or print the token.
