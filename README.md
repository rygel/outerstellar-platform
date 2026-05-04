### Outerstellar Platform Project

A modern, full-stack Kotlin application designed as a platform template for building robust and scalable systems. This project showcases a clean, multi-module architecture with a focus on reliability, performance, and observability.

---

### Key Architectural Features

- **Multi-Module Architecture**: Split into `platform-core`, `platform-persistence-jooq`, `platform-persistence-jdbi`, `platform-sync-client`, `platform-security`, `platform-web`, and `platform-desktop` modules for clear separation of concerns.
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

- `platform-core`: Domain models, service interfaces, and shared business logic.
- `platform-persistence-jooq`: Database implementation using jOOQ, Flyway migrations, and Caffeine caching. This is the default persistence module.
- `platform-persistence-jdbi`: Alternative database implementation using JDBI. Drop-in replacement — implements the same repository interfaces and Koin module as `platform-persistence-jooq`. Has zero jOOQ dependency. Swap the Maven artifact to switch.
- `platform-sync-client`: Shared DTOs and client logic for synchronization between components.
- `platform-security`: Authentication models, role-based access control, fine-grained permissions, multi-realm auth, and security filters.
- `platform-web`: The main http4k server, JTE templates, and web-specific infrastructure.
- `platform-desktop`: A Swing-based desktop application implementing the MVVM pattern.

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

#### Maven Profiles

Use these root-level profiles to separate concerns:

- `coverage`: enables coverage collection/report generation for verification runs.
- `tests-headless`: runs tests with desktop UI in headless mode.
- `tests-headful`: runs tests with desktop UI in non-headless mode.
- `runtime-dev`: optimized runtime profile for local development launches.
- `runtime-prod`: optimized runtime profile for production-like launches.

Examples:

```bash
# Coverage + tests
mvn -Pcoverage verify

# Desktop tests in headless mode (CI friendly)
mvn -pl platform-desktop -Ptests-headless test

# Desktop tests with actual UI
mvn -pl platform-desktop -Ptests-headful test

# Run web in dev runtime mode
mvn -pl platform-web -Pruntime-dev compile exec:java
```

#### Regenerate jOOQ Sources (Manual + Version Controlled)

Generated jOOQ sources are checked in under:

- `platform-persistence-jooq/src/main/generated/jooq`

Regenerate them manually when migrations change:

```bash
mvn -pl platform-persistence-jooq -Pjooq-codegen generate-sources
```

PowerShell shortcut:

```bash
./generate-jooq.ps1
```

Then commit the updated generated files together with your migration changes.

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
2. **Create a Template**: Add a corresponding `.kte` file in `platform-web/src/main/jte`. Wrap your content using the `Page<T>` wrapper to inherit the global shell:
   ```html
   @import io.github.rygel.outerstellar.platform.web.MyPage
   @import io.github.rygel.outerstellar.platform.web.Page
   @param model: Page<MyPage>
   
   @template.io.github.rygel.outerstellar.platform.web.Layout(shell = model.shell, content = @`
       <h1>${model.data.title}</h1>
   `)
   ```
3. **Update the Factory**: Add a `buildMyPage` method to `WebPageFactory.kt` that returns `Page<MyPage>`.
4. **Register the Route**: Add the route to the appropriate route class (e.g., `HomeRoutes.kt`) and bind it in `App.kt`.
5. **Access State**: Use `WebContext.KEY(request)` or the helper property `request.webContext` to access the current theme, language, and layout state without manual extraction.

#### Fine-Grained Permissions

Beyond role-based access (`USER`/`ADMIN`), routes can require specific permissions using the wildcard `domain:action:instance` model:

```kotlin
// Require "report:export" permission to access this route
SecurityRules.hasPermission(Permission("report", "export"), permissionResolver, next)
```

The default `RoleBasedPermissionResolver` maps roles to permission sets (admins get `*:*`). For per-user permissions, implement a custom `PermissionResolver` backed by a database table — the interface is a single method.

#### Multi-Realm Authentication

Bearer token authentication is resolved through a chain of `AuthRealm` instances. The default chain tries session tokens first, then API keys. To add a custom auth source (e.g. LDAP, external OAuth tokens):

```kotlin
class LdapRealm(private val ldapClient: LdapClient) : AuthRealm {
    override val name = "ldap"
    override fun authenticate(token: String): AuthResult {
        val user = ldapClient.validateToken(token) ?: return AuthResult.Skipped
        return AuthResult.Authenticated(user)
    }
}

// Register in your Koin module:
single<List<AuthRealm>> { listOf(SessionRealm(get()), ApiKeyRealm(get()), LdapRealm(get())) }
```

#### Native Image Support (GraalVM)

The web application is ready for Ahead-of-Time (AOT) compilation using GraalVM. This produces a standalone native binary with extremely fast startup times and low memory footprint.

To build the native image (requires GraalVM installed and `JAVA_HOME` set correctly):

```bash
mvn package -Pnative -pl platform-web -DskipTests
```

The resulting binary will be located in `platform-web/target/outerstellar-web`.

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

#### Design Notes

*   **Theme Consistency is Mandatory**: Every Swing UI surface (main windows, dialogs, popups, and transient overlays) must use centralized theming. Do not ship unthemed or partially themed screens.
*   **FlatLaf as Source of Truth**: Use FlatLaf/UI defaults (`UIManager` keys and shared theme tokens) for colors, typography, borders, and component states. Avoid hardcoded colors, fonts, and ad-hoc styling in individual windows.
*   **MigLayout as Standard Layout System**: Use **MigLayout** for all new/updated Swing windows and dialogs to keep spacing, alignment, and responsiveness consistent across the app.
*   **Themed Dialog Rule**: Authentication and settings dialogs must apply the same background, foreground, and component style rules as primary windows so there is no visual drift.
*   **Localization Rule**: All user-visible Swing text (window titles, labels, buttons, menu items, and dialog messages) must come from i18n keys. Avoid hardcoded UI strings in production code.
*   **Runtime Language Switch Rule**: Changing language at runtime must refresh currently mounted UI text in-place, not only newly opened windows/dialogs.
*   **Language Regression Test Rule**: Any change touching Swing text or settings flow must include/update an automated language-switch test that verifies key UI labels and menus update correctly.
*   **Design Review Check**: UI work is incomplete until all affected screens are verified for theme parity (light/dark if supported), readable contrast, and consistent spacing.

*   **No WebSockets for Desktop**: The desktop application explicitly **does not use WebSockets** for synchronization.
    *   **Reasons**: To keep the desktop client's architecture lean and focused on its primary role as a standalone synchronization tool. Standard HTTP-based sync provides a reliable, firewall-friendly connection model that is easier to debug and maintain for a desktop environment. Real-time updates are prioritized for the Web UI, while the desktop app maintains a robust manual or background polling-based sync model.
