# Focus-and-Study-Tracking

> An open-source, privacy-first desktop productivity environment designed to help developers, students, and professionals track work sessions, eliminate digital distractions, and analyze workflow habits directly from their primary workstation.

## 📌 Table of Contents
* [About the Project](#-about-the-project)
* [System Architecture](#%EF%B8%8F-system-architecture)
* [Technology Stack](#%EF%B8%8F-technology-stack)
* [Core Features & Roadmap](#%EF%B8%8F-core-features--roadmap)
* [Domain-Driven Design (DDD)](#%EF%B8%8F-domain-driven-design-ddd)
* [Project Structure](#-project-structure)
* [Getting Started & Installation](#%EF%B8%8F-getting-started--installation)
* [Privacy & Data Security](#-privacy--data-security)
* [Contributing](#-contributing)
* [License](#-license)

## 🚀 About the Project
In an era of continuous digital distractions, maintaining sustained focus during academic or professional work is a significant challenge. While many productivity solutions target mobile devices, desk-bound users primarily operate on personal computers where distractions are most prevalent.

**FocusDesktop** is a native desktop application that integrates directly into the user's workspace. It bridges a robust local backend system with a modern user interface, providing a local-first alternative to monitor productivity, restrict distractions, and analyze session history without compromising personal privacy.

## 🏗️ System Architecture
This application utilizes a modern **hybrid desktop architecture**:
1. **The Core Engine (Backend):** A lightweight Java Spring Boot application runs seamlessly in the background as a local process. It manages the timer logic, system hooks (for distraction blocking), and local database persistence.
2. **The Interface (Frontend):** A React + TypeScript UI is packaged as a native desktop window using **Tauri**.
3. **Communication:** The frontend communicates with the local Spring Boot backend via a loopback REST API and WebSocket, so product features work offline.

## 🛠️ Technology Stack
* **Backend Core:** Java 17+, Spring Boot
* **Desktop UI Wrapper:** Tauri (Rust-based)
* **Frontend UI:** React (TypeScript)
* **Database:** H2 (embedded local database for the MVP)
* **Architecture Pattern:** Domain-Driven Design (DDD)

## Design

[Visualizar o design no Figma](https://www.figma.com/design/85wWEJcb1dyJLyDbH7YpTT/Focus_Desktop?node-id=3-228&t=M7IYhVYHEIhZolOO-1)

## 🗺️ Core Features & Roadmap

### Phase 1: MVP
* **Desktop Focus Timer:** Precise session-tracking engine to manage deep-work intervals and breaks.
* **Session Management & History:** Detailed logging of study durations, daily streaks, and historical productivity logs.
* **Local-First Storage:** Complete data privacy, storing all metrics directly on the user's machine.

### Phase 2: Advanced Capabilities
* **Distraction Blocking:** OS-level restriction of non-essential applications and background processes during active focus sessions.
* **Screen & Activity Recording:** Optional local desktop screen capture to visually log study sessions.
* **Automated Content Recognition:** Intelligent analysis to automatically identify study materials and categorize logs without manual user input.

## 🏛️ Domain-Driven Design (DDD)
To maintain high software engineering standards, the Java backend follows **Domain-Driven Design (DDD)** principles, structured into strictly decoupled layers:
* **Domain Layer:** Contains pure business logic, entities (e.g., `FocusSession`, `DistractionLog`), value objects, and domain services. Completely isolated from Spring/infrastructure dependencies.
* **Application Layer:** Orchestrates use cases (e.g., `StartSessionUseCase`, `CalculateDailyStreakUseCase`).
* **Infrastructure Layer:** Handles technical concerns such as H2 persistence, OS-level window detection, and Spring Boot REST Controllers.

## 📁 Project Structure
```text
FocusDesktop/
├── backend/                  # Spring Boot Java application
│   ├── src/main/java/com/focusdesktop/
│   │   ├── domain/           # Core business logic
│   │   ├── application/      # Use cases & application services
│   │   └── infrastructure/   # DB persistence, OS hooks, REST API
│   └── pom.xml / build.gradle
├── frontend/                 # React + TypeScript desktop UI
│   ├── src/                  # React components and frontend logic
│   ├── src-tauri/            # Tauri desktop configuration
│   └── package.json
└── docs/                     # Architecture diagrams and reports
```

## ⚙️ Getting Started & Installation

### Prerequisites
Ensure you have the following installed on your local machine:
* **Java Development Kit (JDK 17 or higher)**
* **Node.js** (`^20.19.0` or `>=22.12.0`) & npm
* **Rust stable** (install through [rustup](https://rustup.rs/)) for the Tauri desktop app
* On Debian/Ubuntu, Tauri's Linux development libraries: `libwebkit2gtk-4.1-dev`,
  `build-essential`, `curl`, `wget`, `file`, `libxdo-dev`, `libssl-dev`,
  `librsvg2-dev`, and `libayatana-appindicator3-dev`

The Maven Wrapper downloads the pinned Maven distribution on its first run, so
Maven does not need to be installed globally. Internet access is required only
for that first download and for dependencies not already cached.

The wrapper is a launcher; Maven goals specify the task. Run these commands from
`backend/`:

| Goal | Command | Purpose |
|---|---|---|
| Run the backend | `./mvnw spring-boot:run` | Start the local Spring Boot application. |
| Run tests | `./mvnw test` | Compile and run the test suite. |
| Verify the project | `./mvnw verify` | Run the full Maven verification lifecycle. |
| Package the backend | `./mvnw package` | Build the application JAR. |

On Windows, use `mvnw.cmd` with the same Maven goals.

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/FocusDesktop.git
   cd FocusDesktop
   ```

2. **Build the local backend JAR:**
   ```bash
   cd backend
   ./mvnw package -DskipTests
   ```
   On Windows, run `mvnw.cmd package -DskipTests` from `backend/`.

   The H2 database is stored in the platform's local application-data directory.
   Set `FOCUSDESKTOP_DATA_DIR` to use a different directory.

3. **Start the desktop app:**
   ```bash
   # In a new terminal, from the repository root:
   cd frontend
   npm install
   npm run tauri:dev
   ```

   This starts Vite, opens the FocusDesktop native window, and launches the
   backend sidecar. Java must be installed during development. To preview the
   interface in a browser instead, run `npm run dev` from `frontend/` while
   starting the backend separately with `./mvnw spring-boot:run`.

   Website blocking in browser development needs an explicitly enabled backend:
   ```bash
   # From backend/, replacing the ordinary backend launch:
   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--focusdesktop.blocking.monitor-enabled=true
   ```
   Start `npm run dev` separately. Open **Settings → Blocking**, use
   **Automatic connection**, turn on **Block websites** and accept the connection
   and forwarding consent. Linux (including Hyprland) uses native browser policies
   and needs PolicyKit (`pkexec`) plus an authentication agent. macOS asks for
   administrator permission to change network proxy settings. Windows uses user
   browser policies and system proxy settings.
   Choose **Connect browser** and open the connection-check address in each
   browser you use. One check does not verify every browser or profile. Policies
   keep normal browser relaunches connected while protection and the backend are
   running; no special FocusDesktop browser window is required. Supported Chromium
   browsers receive a local policy refresh without closing windows. If necessary,
   use Reload policies on the browser's policy page once. Firefox may need one
   initial restart. Flatpak/Snap and custom proxy overrides need separate setup.
   Focus sessions, pauses and rule edits reuse the connection. Turning website
   blocking off or stopping the app restores owned settings and stops forwarding.
   Permission denial or a conflicting proxy is reported; retry explicitly in
   Settings. If restoration cannot be confirmed, use **Retry restoration**.
   After upgrading from launch-only setup, allow website blocking again: the new
   permission scope needs fresh consent. Then test your enabled `youtube.com`
   Block rule during a running work interval. Cached pages may remain visible.

   To select apps, open **Distractions → Add distraction → Application**.
   Search recognized apps, check several, and choose **Add selected**.
   The catalog includes native apps and recognized Store/MSIX, macOS bundle,
   Flatpak and Snap identities. **Refresh** picks up new installations;
   **App not listed? Add manually** keeps executable/process entry available.
   Saving rules does not turn protection on: enable **Block apps** in Settings.
   Unresolved launchers remain manual. Browser web-app shortcuts are never
   automatically treated as the entire browser. See [the picker design](docs/decisions/ADR-016-ApplicationPicker.md).
   Starting a session checks enabled Block rules. If setup is missing, choose
   **Set up blocking**, **Start without blocking**, or **Cancel**. Opening Settings
   keeps your project name and timer choices; returning never auto-starts a session.

4. **Build the desktop app:**
   ```bash
   cd frontend
   npm run tauri:build
   ```

   This stages the backend and verified Temurin JRE, then creates the native
   installer for the current x64 host under
   `frontend/src-tauri/target/release/bundle/`: NSIS on Windows, DMG on macOS,
   and DEB plus AppImage on Linux. Packaged users do not need Java installed.
   Build on the target operating system; cross-compilation is not configured.
   Local release packages are unsigned and may show the operating system's
   first-launch warning. See [frontend packaging instructions](frontend/README.md#release-packages)
   and the [FST-041 package smoke checklist](docs/testing/FST-041-packaging.md).

   To publish installers on GitHub, push a version tag such as `v0.1.0`. Once
   the build and packaging matrix passes, the workflow creates a GitHub Release
   containing the Windows NSIS installer, both macOS DMGs, and the Linux DEB
   and AppImage. The files are also retained as workflow artifacts for
   non-release builds.

## 🔒 Privacy & Data Security
FocusDesktop adheres strictly to a **local-first philosophy**. All user productivity logs, session histories, and analytical data are stored entirely on the local machine. The application does not require an internet connection for its core features, ensuring maximum data privacy.

## 👥 Contributing
Contributions, feature suggestions, and architectural feedback are welcome. See
[`CONTRIBUTING.md`](CONTRIBUTING.md) for setup, code and test conventions, and
pull request guidance. Use the GitHub issue forms to report a bug or propose a
feature, then submit changes through a pull request.

## 📄 License
This project is licensed under the [MIT License](LICENSE).
