### Outerstellar Starter Architecture (C4 Model)

This document provides a comprehensive overview of the Outerstellar starter project architecture using the C4 Model (Context, Container, Component).

---

### 1. System Context Diagram
The System Context diagram shows the Outerstellar system and its relationship with users and external systems.

```mermaid
C4Context
    title System Context diagram for Outerstellar Message System

    Person(user, "User", "A user who wants to send and receive messages.")
    System(outerstellar, "Outerstellar System", "Allows users to manage messages and synchronize data between desktop and web.")
    System_Ext(remote_api, "Remote Sync API", "Optional external instance for cross-device synchronization.")

    Rel(user, outerstellar, "Uses", "HTTPS/Swing")
    Rel(outerstellar, remote_api, "Synchronizes messages with", "JSON/HTTPS")
```

---

### 2. Container Diagram
The Container diagram shows the high-level software containers that make up the Outerstellar system.

```mermaid
C4Container
    title Container diagram for Outerstellar System

    Person(user, "User", "A user of the system.")

    System_Boundary(c1, "Outerstellar") {
        Container(web_app, "Web Application", "Kotlin, http4k, JTE", "Provides a web interface for message management.")
        Container(desktop_app, "Desktop Application", "Kotlin, Swing, MVVM", "A native application for message management and local sync.")
        ContainerDb(db, "Database", "H2 / PostgreSQL", "Stores messages, outbox entries, and configuration.")
    }

    Rel(user, web_app, "Uses", "HTTPS")
    Rel(user, desktop_app, "Uses", "Native UI")
    Rel(web_app, db, "Reads/Writes", "jOOQ")
    Rel(desktop_app, db, "Reads/Writes", "jOOQ")
    Rel(desktop_app, web_app, "Synchronizes with", "JSON/HTTPS")
```

---

### 3. Component Diagram (Web Application)
The Component diagram shows the internal structure of the Web Application container.

```mermaid
C4Component
    title Component diagram for Web Application

    Container(desktop_app, "Desktop Application", "Kotlin, Swing", "Syncs data via API.")
    ContainerDb(db, "Database", "PostgreSQL", "Relational storage.")

    Boundary(web_boundary, "Web Application") {
        Component(app_routes, "Server Routes", "http4k-contract", "Defines API and Web endpoints.")
        Component(msg_service, "Message Service", "Kotlin Service", "Encapsulates business logic and outbox processing.")
        Component(msg_repo, "Message Repository", "jOOQ", "Handles data persistence and caching.")
        Component(outbox_proc, "Outbox Processor", "Background Task", "Processes pending sync tasks.")
        Component(auth_filter, "Security Module", "http4k Filter", "Handles RBAC and authentication.")
    }

    Rel(desktop_app, app_routes, "Calls API", "JSON/HTTPS")
    Rel(app_routes, msg_service, "Uses")
    Rel(app_routes, auth_filter, "Protected by")
    Rel(msg_service, msg_repo, "Uses")
    Rel(msg_service, outbox_proc, "Triggers")
    Rel(outbox_proc, msg_repo, "Updates status")
    Rel(msg_repo, db, "JDBC/jOOQ")
```

---

### 4. Code / Module Structure
The project is organized into several highly decoupled Maven modules:

- **`core`**: Domain models (`StoredMessage`), Service interfaces, and shared business logic.
- **`persistence`**: Implementation of repositories using jOOQ, Flyway migrations, and Caffeine caching.
- **`security`**: Authentication logic, Role-Based Access Control (RBAC), and user models.
- **`api-client`**: Client-side synchronization logic using Resilience4j (Retry/Circuit Breaker).
- **`web`**: The http4k server, JTE templates, and Web Component-based UI.
- **`desktop`**: The Swing application following the MVVM architecture.

---

### 5. Key Architectural Patterns
- **Transactional Outbox**: Ensures eventual consistency between local database updates and remote synchronization.
- **MVVM (Desktop)**: Decouples UI state from business logic in the Swing client.
- **Contract-First API**: Uses `http4k-contract` for typesafe routing and automatic OpenAPI documentation.
- **Optimistic Locking**: Uses a `version` column to prevent lost updates during concurrent modifications.
- **Read/Write Splitting**: Support for primary and replica database routing in the persistence layer.
- **Observability**: Integrated OpenTelemetry for tracing and Micrometer for metrics.
