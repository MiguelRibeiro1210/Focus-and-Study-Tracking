# FST-044 packaged runtime verification

Release installers must contain the backend JAR and the platform-matched,
app-private Temurin Java runtime. Launch checks must not rely on a Java
installation on the host: packaged smoke tests clear `JAVA_HOME`, remove Java
executables from `PATH`, and set `FOCUSDESKTOP_JAVA` to a nonexistent path.

## CI coverage

The packaging workflow verifies the following outputs:

| Target | Package checks |
|---|---|
| Windows x64 | Silently installs the NSIS package per user and smoke-tests the installed app without system Java. |
| macOS x64 and ARM64 | Mounts each DMG, checks the app and runtime architectures, and smoke-tests the packaged app. |
| Linux x64 | Checks the DEB contains an executable Java runtime and dependency metadata; smoke-tests the AppImage. |

All packaged smoke checks confirm the local backend becomes healthy and the
timer REST/WebSocket flow works. The Linux AppImage targets the Ubuntu
22.04-compatible baseline. The DEB uses package-manager dependencies.

## Clean-install checks

Before publishing a release, perform these checks on clean supported systems:

1. Install the Windows NSIS package for the current user on Windows 10/11
   without Java installed. If WebView2 is missing, allow the default Tauri
   bootstrapper to install it. Launch FocusDesktop and confirm the backend
   reaches `/health`; then disconnect from the internet and confirm it still
   works.
2. Install the macOS DMG on macOS 12 or newer without Java installed. Confirm
   the app launches and its bundled Java runtime starts the backend.
3. Install the DEB through the package manager on Ubuntu 22.04 or newer, and
   launch the AppImage on the same supported baseline. Neither should require
   Java to be installed separately.

The development shell is different: it uses the Java 17 executable available
on the developer's `PATH`. That requirement does not apply to release
installers.
