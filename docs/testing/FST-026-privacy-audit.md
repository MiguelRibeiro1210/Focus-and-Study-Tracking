# FST-026 — Privacy audit

## Runtime boundary

The installed app may communicate with its Spring Boot sidecar only over
loopback (`127.0.0.1`, `localhost` or `::1`). Browser development proxies are
also local. The runtime includes no telemetry SDK, analytics uploader or crash
reporter. Downloading dependencies and the pinned Java runtime while building
an installer is a build-time operation and is outside this boundary.

## Automated checks

- Run `npm run privacy:audit` from `frontend/`. It scans frontend, Tauri and
  backend runtime source for non-local URL endpoints and checks frontend and
  Rust resolved dependency locks plus declared dependencies for telemetry and
  crash-reporting packages.
- In Linux CI, the packaged AppImage smoke test runs with
  `privacy-no-external-network.c` preloaded. It rejects non-loopback TCP
  connections and UDP sends, records any attempted connection, and fails the
  job if one was attempted. The smoke test still exercises local REST and
  WebSocket traffic.
- Existing API tests verify bearer-token enforcement and reject non-local
  origins.

## Scope and limits

The source/dependency audit runs on every CI verification job. Runtime socket
guarding currently runs on the Linux packaged smoke job; Windows and macOS
runtime traffic is covered by source and dependency review, not by an OS-level
network sandbox. Installer build steps require network access to fetch
toolchains and runtime archives; the installed product does not require that
access for its features.
