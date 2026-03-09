### Outerstellar Starter Project

A modern, full-stack Kotlin application designed as a starter template for building robust and scalable systems. This project showcases a clean, multi-module architecture with a focus on reliability, performance, and observability.

---

### Key Architectural Features

- **Multi-Module Architecture**: Split into `core`, `persistence`, `api-client`, `security`, `web`, and `desktop` modules for clear separation of concerns.
- **Transactional Outbox Pattern**: Ensures atomicity and reliability for background tasks and data synchronization.
- **Dependency Injection**: Powered by **Koin** for flexible and testable component wiring.
- **Observability**: Fully integrated with **OpenTelemetry** for distributed tracing and **Micrometer** for real-time metrics.
- **Type-Safe Configuration**: Uses **Hoplite** for robust, multi-environment configuration management.
- **Contract-First API**: synchronization API is defined using **http4k-contract**, providing automatic OpenAPI documentation and type-safe routing.
- **Caching Layer**: Implements a **Caffeine-based caching** system with integrated metrics.
- **Swing Desktop MVVM**: The desktop client uses the **Model-View-ViewModel** pattern for better UI/logic separation.
- **Managed Database Migrations**: Uses **Flyway** for reliable schema versioning and management.

---

### Project Structure

- `core`: Domain models, service interfaces, and shared business logic.
- `persistence`: Database implementation using jOOQ, Flyway migrations, and Caffeine caching.
- `api-client`: Shared DTOs and client logic for synchronization between components.
- `security`: Authentication models, role-based access control, and security filters.
- `web`: The main http4k server, JTE templates, and web-specific infrastructure.
- `desktop`: A Swing-based desktop application implementing the MVVM pattern.

---

### Getting Started

#### Prerequisites
- JDK 17+
- Maven 3.8+
- Node.js (for Tailwind CSS)

#### Build
```bash
mvn clean install
```

#### Run Web Application
```bash
./start-web.ps1
```

#### Run Desktop Application
```bash
./start-swing.ps1
```

---

### Testing

The project includes a comprehensive test suite:
- **Unit Tests**: Business logic verification with MockK.
- **Integration Tests**: Database and service-level integration.
- **Architecture Tests**: Enforcing modular boundaries with ArchUnit.
- **End-to-End Tests**: Full system verification through the web layer.

To run all tests:
```bash
mvn test
```
