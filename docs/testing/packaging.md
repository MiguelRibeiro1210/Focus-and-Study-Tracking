# FST-041 packaged application smoke checklist

Build the installer on its matching operating system and architecture with the
commands in `frontend/README.md`. The build downloads the pinned Temurin JRE
and verifies its SHA-256 before bundling. Use a clean test account or back up
existing application data before checking upgrade behavior.

## First install and launch

1. Install the platform artifact: Windows `.exe` (NSIS), macOS `.dmg`, Linux
   `.deb` or `.AppImage`.
2. Confirm FocusDesktop starts on a machine where Java is not installed and
   `java` is not available on `PATH`.
3. Confirm the status reaches **Local API ready** and the timer screen loads.
4. Start a work session. Confirm the UI changes and the session remains
   consistent after refreshing/reopening the window.
5. Confirm that a session state change reaches the UI over the WebSocket.
6. Exit FocusDesktop from its normal window control. Confirm it exits cleanly
   and port 8080 is no longer serving the app's backend.

## Persistence and upgrade

1. Install and launch version 0.1.0, then create or start a session and note
   the application-data directory's database and `api-token` files.
2. Install a later build over 0.1.0 without deleting the application-data
   directory.
3. Launch the upgraded app. Confirm the token is still present, the active
   session is recovered, and the backend starts normally.
4. Exit normally and confirm the backend stops.

The application-data locations are `%LOCALAPPDATA%/FocusDesktop` on Windows,
`~/Library/Application Support/FocusDesktop` on macOS, and
`$XDG_DATA_HOME/focusdesktop` (or `~/.local/share/focusdesktop`) on Linux.

For the ad-hoc signed macOS test build, install the DMG and open FocusDesktop.
If macOS blocks the first launch, use **System Settings → Privacy & Security →
Open Anyway** to approve this test build. It is not notarized.

## Build pipeline checks

The GitHub Actions packaging matrix builds x64 installers on Ubuntu 22.04 and
Windows Server 2025, plus x64 and ARM64 DMGs on macOS 15 Intel and Apple Silicon
runners. It uploads each installer as a separate workflow artifact, retained for
14 days. To send a tester a build before release, download the relevant artifact
from the workflow run; the tester does not need repository access or build
tools.

To publish all installers as GitHub Release assets, push a version tag such as
`v0.1.0`. After every matrix build and smoke check passes, the workflow creates
a release containing the Windows NSIS installer, Intel and Apple Silicon DMGs,
and Linux DEB and AppImage. Complete the first-launch and upgrade checks above
on each platform before publishing a release.

The macOS test build is ad-hoc signed but not notarized. Other local packages
are unsigned. Developer ID signing and notarization are outside this story.
