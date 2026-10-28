# Focus-and-Study-Tracking

> An open-source, privacy-first desktop productivity environment designed to help developers, students, and professionals track work sessions, eliminate digital distractions, and analyze workflow habits directly from their primary workstation.

## 📌 Table of Contents
* [About the Project](#-about-the-project)
* [System Architecture](#-system-architecture)
* [Technology Stack](#-technology-stack)
* [Core Features & Roadmap](#-core-features--roadmap)
* [Domain-Driven Design (DDD)](#-domain-driven-design-ddd)
* [Project Structure](#-project-structure)
* [Getting Started & Installation](#-getting-started--installation)
* [Privacy & Data Security](#-privacy--data-security)
* [Contributing](#-contributing)
* [License](#-license)

## 🚀 About the Project
In an era of continuous digital distractions, maintaining sustained focus during academic or professional work is a significant challenge. While many productivity solutions target mobile devices, desk-bound users primarily operate on personal computers where distractions are most prevalent.

**FocusDesktop** is a native desktop application that integrates directly into the user's workspace. It bridges a robust local backend system with a modern user interface, providing a local-first alternative to monitor productivity, restrict distractions, and analyze session history without compromising personal privacy.

## 🏗️ System Architecture
This application utilizes a modern **hybrid desktop architecture**:
1. **The Core Engine (Backend):** A lightweight Java Spring Boot application runs seamlessly in the background as a local process. It manages the timer logic, system hooks (for distraction blocking), and local database persistence.
2. **The Interface (Frontend):** A modern Web UI (React/Vue) is packaged as a native desktop window using **Tauri** (or Electron). 
3. **Communication:** The frontend communicates with the local Spring Boot backend via a local REST API and WebSockets, ensuring a 100% offline, responsive, and secure desktop experience.

## 🛠️ Technology Stack
* **Backend Core:** Java 17+, Spring Boot
* **Desktop UI Wrapper:** Tauri (Rust-based) / Electron
* **Frontend UI:** React.js / Vue.js (TypeScript)
* **Database:** SQLite / H2 (Embedded local database)
* **Architecture Pattern:** Domain-Driven Design (DDD)

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
* **Infrastructure Layer:** Handles technical concerns such as SQLite repositories, OS-level window detection, and Spring Boot REST Controllers.

## 📁 Project Structure
```text
FocusDesktop/
├── backend/                  # Spring Boot Java application
│   ├── src/main/java/com/focusdesktop/
│   │   ├── domain/           # Core business logic
│   │   ├── application/      # Use cases & application services
│   │   └── infrastructure/   # DB persistence, OS hooks, REST API
│   └── pom.xml / build.gradle
├── frontend/                 # Tauri / Electron UI project
│   ├── src/                  # React/Vue components
│   ├── src-tauri/            # Tauri desktop configuration
│   └── package.json
└── docs/                     # Architecture diagrams and reports
```

## ⚙️ Getting Started & Installation

### Prerequisites
Ensure you have the following installed on your local machine:
* **Java Development Kit (JDK 17 or higher)**
* **Maven** or **Gradle**
* **Node.js** (v16+) & npm/yarn
* *(If using Tauri)* **Rust** environment

### Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/FocusDesktop.git
   cd FocusDesktop
   ```

2. **Start the Backend Engine (Spring Boot):**
   ```bash
   cd backend
   mvn spring-boot:run
   # The backend will start on localhost (e.g., port 8080)
   ```

3. **Start the Desktop Interface (Frontend):**
   ```bash
   # Open a new terminal tab
   cd ../frontend
   npm install
   npm run tauri dev # Or npm start for Electron
   ```

## 🔒 Privacy & Data Security
FocusDesktop adheres strictly to a **local-first philosophy**. All user productivity logs, session histories, and analytical data are stored entirely on the local machine. The application does not require an internet connection for its core features, ensuring maximum data privacy.

## 👥 Contributing
Contributions, feature suggestions, and architectural feedback are highly welcome! Feel free to open an issue or submit a pull request.

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
