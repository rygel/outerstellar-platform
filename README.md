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

---

### Web Architecture & Adding Routes

The web application uses **http4k** with **JTE** templates and **HTMX** for interactivity. Routes are organized into three logical groups in `App.kt`:

1. **UI Routes**: Full HTML pages (e.g., Home, Auth).
2. **Component Routes**: HTMX fragments (e.g., `/components/message-list`).
3. **API Routes**: JSON-based synchronization API.

#### How to Add a New Page:

1. **Create a ViewModel**: Define a Kotlin `data class` implementing `ViewModel` in `WebPageFactory.kt`.
2. **Create a Template**: Add a corresponding `.kte` file in `web/src/main/jte`. Wrap your content using the `Page<T>` wrapper to inherit the global shell:
   ```html
   @import dev.outerstellar.starter.web.MyPage
   @import dev.outerstellar.starter.web.Page
   @param model: Page<MyPage>
   
   @template.dev.outerstellar.starter.web.Layout(shell = model.shell, content = @`
       <h1>${model.data.title}</h1>
   `)
   ```
3. **Update the Factory**: Add a `buildMyPage` method to `WebPageFactory.kt` that returns `Page<MyPage>`.
4. **Register the Route**: Add the route to the appropriate route class (e.g., `HomeRoutes.kt`) and bind it in `App.kt`.
5. **Access State**: Use `WebContext.KEY(request)` or the helper property `request.webContext` to access the current theme, language, and layout state without manual extraction.

#### Native Image Support (GraalVM)

The web application is ready for Ahead-of-Time (AOT) compilation using GraalVM. This produces a standalone native binary with extremely fast startup times and low memory footprint.

To build the native image (requires GraalVM installed and `JAVA_HOME` set correctly):

```bash
mvn package -Pnative -pl web -DskipTests
```

The resulting binary will be located in `web/target/outerstellar-web`.

#### Development Best Practices & Safety Rules

To prevent common Kotlin type-inference issues and library conflicts, follow these rules:

*   **Explicit Request Typing**: In route handlers (`bindContract`), always explicitly type the request parameter. This prevents `ClassCastException` where the compiler might confuse a `Request` with a `ViewModel`.
    ```kotlin
    // ALWAYS DO THIS:
    bindContract GET to { request: Request -> 
        renderer.render(pageFactory.buildAuthPage(request.webContext))
    }
    ```
*   **Fully Qualified Template Types**: JTE and http4k both have a `ContentType` class. Always use the fully qualified name `gg.jte.ContentType.Html` when configuring the template engine to avoid import ambiguity.
*   **Use the Render Extension**: Never manually construct HTML responses. Use `renderer.render(viewModel)` which automatically handles content-type headers and UTF-8 encoding.
*   **Contextual Helpers**: Prefer `request.webContext` over manually creating a `WebContext` instance. This ensures you are using the state already extracted by the global filters.

#### HTMX Patterns & Constraints

*   **Discourage OOB Swaps**: The use of HTMX **Out-of-Band (OOB) swaps is discouraged** by default. They should only be implemented if strictly necessary and only after careful consideration.
    *   **Reasons for avoidance**:
        1. **Increased Server Complexity**: It requires route handlers to wrap multiple unrelated fragments in a single response, bloating template logic.
        2. **Risk of State Desync**: Updating elements far away from the trigger can lead to unpredictable UI states across different tabs or sessions.
        3. **Breaks Locality of Behavior**: It violates the core HTMX principle by spreading the consequences of an action across disparate parts of the DOM.
        4. **Initialization Logic Duplication**: OOB updates only happen on specific actions; ensuring the "Initial Load" logic matches the "Action Result" logic requires repetitive code.
    Prefer standard `hx-target` swaps to keep the flow predictable and maintainable.

---

### Desktop Architecture

The desktop application is built with **Swing** following the **MVVM** pattern and uses **FlatLaf** for a modern, themed look and feel.

*   **No WebSockets for Desktop**: The desktop application explicitly **does not use WebSockets** for synchronization.
    *   **Reasons**: To keep the desktop client's architecture lean and focused on its primary role as a standalone synchronization tool. Standard HTTP-based sync provides a reliable, firewall-friendly connection model that is easier to debug and maintain for a desktop environment. Real-time updates are prioritized for the Web UI, while the desktop app maintains a robust manual or background polling-based sync model.
