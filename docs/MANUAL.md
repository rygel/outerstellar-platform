# Outerstellar Platform — Developer Manual

## Prerequisites

- **JDK 21** (strict: `[21, 22)`)
- **Maven 3.9.0+**
- **PostgreSQL 18** (via Podman/Docker or local install)
- **Node.js** (for Tailwind CSS build)

The project uses `outerstellar-framework` artifacts from GitHub Packages. Ensure your `settings.xml` includes the `github-rygel` server entry with a Personal Access Token.

## Quick Start

```bash
# Start PostgreSQL
podman compose -f docker/podman-compose.yml up -d

# Full build (skip tests)
mvn clean install -DskipTests

# Start web app with hot reload
./scripts/start-web.ps1

# Stop
./scripts/stop-web.ps1
```

The web app starts on `http://localhost:8080`. A first-boot admin user is created with a random password (logged to console). Set `ADMIN_PASSWORD` env var to control this.

## Module Structure

```
platform-core              Domain models, services, configuration
platform-persistence-jooq  jOOQ repositories + Flyway migrations
platform-persistence-jdbi  JDBI repositories (alternative to jOOQ)
platform-security          Auth, permissions, OAuth, API keys
platform-sync-client       Sync DTOs and client sync service
platform-web               http4k web server, JTE templates, HTMX
platform-desktop           Swing desktop client with two-way sync
platform-seed              Database seeding utility
```

### Dependency Graph

```
platform-core
    ├── platform-persistence-jooq
    ├── platform-persistence-jdbi
    ├── platform-security
    └── platform-sync-client
         ├── platform-web       (depends on all above)
         └── platform-desktop   (depends on core + persistence + security + sync)
```

## Configuration

Configuration is loaded by `AppConfig.fromEnvironment()`. Each property follows this precedence: **environment variable > YAML > default**.

### YAML Profiles

Set `APP_PROFILE` to select a profile. The loader first tries `/application-{profile}.yaml`, then falls back to `/application.yaml`.

| Profile | File | Purpose |
|---|---|---|
| `default` | (all defaults) | Production-like defaults |
| `dev` | `application-dev.yaml` | Enables dev dashboard, dev mode, 120min session |
| `prod` | `application-prod.yaml` | Secure cookies, 30min session, no CORS wildcard |
| `postgres` | `application-postgres.yaml` | Local PostgreSQL connection |

### Reference

| Property | Env Var | Default | Description |
|---|---|---|---|
| `port` | `PORT` | `8080` | HTTP listen port |
| `jdbcUrl` | `JDBC_URL` | `jdbc:postgresql://localhost:5432/outerstellar` | Database JDBC URL |
| `jdbcUser` | `JDBC_USER` | `outerstellar` | Database user |
| `jdbcPassword` | `JDBC_PASSWORD` | `outerstellar` | Database password |
| `devMode` | `DEVMODE` | `false` | Enables dev auto-login, relaxed security |
| `devDashboardEnabled` | `DEV_DASHBOARD_ENABLED` | `false` | Admin diagnostics page |
| `sessionTimeoutMinutes` | `SESSIONTIMEOUTMINUTES` | `30` | Session expiry in minutes |
| `sessionCookieSecure` | `SESSIONCOOKIESECURE` | `false` | Set `true` in production with HTTPS |
| `corsOrigins` | `CORSORIGINS` | `*` | Allowed CORS origins (empty = disabled) |
| `csrfEnabled` | `CSRFENABLED` | `true` | CSRF double-submit cookie protection |
| `appBaseUrl` | `APPBASEURL` | `http://localhost:8080` | Base URL for email links and OAuth |
| `email.enabled` | `EMAIL_ENABLED` | `false` | Enable email delivery |
| `email.host` | `EMAIL_HOST` | `localhost` | SMTP host |
| `email.port` | `EMAIL_PORT` | `587` | SMTP port |
| `email.username` | `EMAIL_USERNAME` | | SMTP username |
| `email.password` | `EMAIL_PASSWORD` | | SMTP password |
| `email.from` | `EMAIL_FROM` | `noreply@example.com` | From address |
| `email.startTls` | `EMAIL_STARTTLS` | `true` | Use STARTTLS |
| `jwt.enabled` | `JWT_ENABLED` | `false` | Enable JWT authentication |
| `jwt.secret` | `JWT_SECRET` | | HMAC-SHA256 secret (required when enabled) |
| `jwt.issuer` | `JWT_ISSUER` | `outerstellar` | JWT issuer claim |
| `jwt.expirySeconds` | `JWT_EXPIRYSECONDS` | `86400` | Token validity (24h) |
| `segment.enabled` | `SEGMENT_ENABLED` | `false` | Enable Segment analytics |
| `segment.writeKey` | `SEGMENT_WRITEKEY` | | Segment write key |

## Database

### Schema

All tables use the `plt_` prefix to avoid collisions with plugin tables. Migrations are in `platform-persistence-jooq/src/main/resources/db/migration/`.

| Migration | Tables |
|---|---|
| V1 | `plt_messages`, `plt_sync_state`, `plt_outbox`, `plt_users`, `plt_contacts`, `plt_contact_emails`, `plt_contact_phones`, `plt_contact_socials`, `plt_audit_log`, `plt_password_reset_tokens`, `plt_api_keys`, `plt_oauth_connections`, `plt_device_tokens`, `plt_notifications` |
| V2 | Adds `avatar_url`, email/push notification flags to `plt_users` |
| V3 | Creates `plt_sessions` |
| V4 | Adds `language`, `theme`, `layout` to `plt_users` |

### jOOQ Code Generation

Generated sources are version-controlled under `platform-persistence-jooq/src/main/generated/jooq`. Regenerate after schema changes:

```bash
mvn -pl platform-persistence-jooq -Pjooq-codegen generate-sources
```

Commit migration and generated source changes together.

### Dual Persistence

The platform ships two interchangeable persistence modules (`jooq` and `jdbi`) that implement identical repository interfaces. Switch by replacing the Maven dependency and importing the other module's `persistenceModule` in Koin. No other code changes needed.

## Plugin Development

### PlatformPlugin Interface

Plugins implement `PlatformPlugin` and register as a Koin `single`:

```kotlin
class MyPlugin : PlatformPlugin {
    override val id = "my-plugin"
    override val appLabel = "My App"

    override fun navItems() = listOf(
        PluginNavItem("Dashboard", "/dashboard", "dashboard-3-line")
    )

    override fun routes(context: PluginContext) = listOf(
        myDashboardRoute(context)
    )

    override fun koinModules() = listOf(myPluginModule)
}
```

Pass the plugin to `startKoin` **before** host modules.

### What Plugins Can Do

| Capability | Override | Description |
|---|---|---|
| Routes | `routes(context)` | Add HTTP routes (http4k `ContractRoute`) |
| Filters | `filters(context)` | Add filters before route dispatch |
| Nav items | `navItems()` | Replace sidebar navigation |
| Koin modules | `koinModules()` | Register additional services |
| Migrations | `PluginMigrationSource` | Separate Flyway instance with own history table |
| Text overrides | `textResolver` | Custom translations |
| Template overrides | `templateOverrides()` | Override JTE templates from plugin classpath |
| Route exclusion | `excludeDefaultRoutes` | Remove specific host routes |

### PluginContext

Passed to `routes()` and `filters()`, provides access to:

- `renderer` — JTE template renderer
- `config` — AppConfig
- `securityService` — auth operations
- `userRepository` — user data access
- `analytics` — tracking service
- `notificationService` — push notifications
- `pageFactory` — web page construction

### Plugin Migrations

Plugins use a separate Flyway instance with configurable history table (default: `flyway_plugin_history`). This prevents version conflicts with platform migrations (V1–V4). Plugins can use any version numbers.

```kotlin
override val migrationLocation = "classpath:db/migration/my-plugin"
override val migrationHistoryTable = "flyway_my_plugin_history"
```

## Security

### Authentication Realms

The platform uses a chainable `AuthRealm` architecture:

1. **SessionRealm** — authenticates `oss_`-prefixed session cookies
2. **ApiKeyRealm** — authenticates `osk_`-prefixed API key tokens

Each realm returns `Authenticated(user)`, `Expired`, or `Skipped` (try next realm).

### User Model

```kotlin
data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val role: UserRole,              // USER or ADMIN
    val enabled: Boolean,
    val language: String?,
    val theme: String?,
    val layout: String?,
)
```

### Permissions

Wildcard `domain:action:instance` permission model:

- `Permission("*", "*")` — full admin access
- `Permission("message", "*")` — all message actions
- `Permission("message", "read", "123")` — specific instance

Default: ADMIN gets full access; USER gets `message:*`, `profile:*`, `notification:*`, `contact:*`.

### Route Protection

```kotlin
SecurityRules.authenticated(handler)         // redirect to login if anonymous
SecurityRules.hasRole(UserRole.ADMIN, handler) // 403 if wrong role
SecurityRules.hasPermission(perm, resolver, handler) // 403 if insufficient
```

## i18n

Message bundles in `platform-core/src/main/resources/`:

- `messages.properties` — English (default)
- `messages_fr.properties` — French

Key convention: `web.*` for web UI, `swing.*` for desktop UI. Use `{0}` or `{name}` for parameter injection.

The `i18n-validator-maven-plugin` validates key consistency across bundles at build time.

## Theming

32 built-in themes defined in `themes.json` with color palettes (background, foreground, accent, success, danger, warning, etc.). Users select themes via the settings page; preference stored in `plt_users.theme`.

Swing desktop uses FlatLaf with `ThemeManager` applying palette colors to `UIManager` defaults.

## Native Image

### Building

```bash
# Set GraalVM as JAVA_HOME
$env:JAVA_HOME = 'path\to\graalvm-ce-25'

# Build native image
mvn -pl platform-web -Pnative package -DskipTests
```

Output: `platform-web/target/outerstellar-web.exe`

Requirements:
- GraalVM 25+ with `native-image`
- `JTE_PRODUCTION=true` is set automatically as a build arg

### Running

```bash
$env:JDBC_URL = 'jdbc:postgresql://localhost:5432/outerstellar'
$env:JTE_PRODUCTION = 'true'
$env:APP_PROFILE = 'dev'
./platform-web/target/outerstellar-web.exe
```

### UPX Compression (Optional)

```bash
mvn -pl platform-web -Pnative,native-upx package -DskipTests
```

Requires `upx` on PATH. Disabled by default due to antivirus false positives.

### Docker Native Image

```bash
docker build -f docker/Dockerfile.native -t outerstellar-platform:native .
```

### Reverse Proxy

The application does not bundle gzip compression or TLS. Compression, TLS termination, and security headers (HSTS) should be handled by a reverse proxy such as Caddy, nginx, or Traefik. See the reverse proxy section in `docs/aot-native-image.md` for configuration examples.

## Testing

### Web Tests

Tests use **Testcontainers** (`postgres:18`) with a shared companion-object container. The `WebTest` base class provides:

- Flyway migration on first access
- Table cleanup between tests (17 DELETE statements)
- Precompiled JTE templates (`jte.production=true`)
- `buildApp()` returning an in-memory `HttpHandler` (no running server)
- `TestOverrides` for injecting mock services

Web tests run sequentially (`parallel=none`) to avoid database races.

### Desktop Tests

Desktop/Swing tests run in a Podman container with Xvfb:

```bash
podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .
podman run --rm --network host outerstellar-test-desktop
```

**Never run desktop tests on the host** — they capture mouse and keyboard.

### Maven Profiles

| Profile | Purpose |
|---|---|
| `-Pcoverage` | JaCoCo coverage |
| `-Ptests-headless` | Swing tests in headless mode |
| `-Ptests-headful` | Swing tests with real UI |
| `-Ptest-desktop` | Desktop tests in Docker container |
| `-Pfast` | Compile only, skip all quality checks |

## Quality Gates

All enforced at `verify` phase:

| Tool | Purpose |
|---|---|
| Spotless (ktfmt) | Code formatting |
| Checkstyle | Java style |
| PMD + CPD | Code smell detection |
| SpotBugs | Bug detection |
| Detekt | Kotlin static analysis |
| Maven Enforcer | Dependency convergence, Java version |

Run all checks:

```bash
mvn spotless:check checkstyle:check pmd:check spotbugs:check detekt:check
```

## API Reference

### Sync

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/sync` | Push dirty messages, receive changes since given epoch |
| `GET` | `/api/v1/sync?since=` | Pull changes since epoch |
| `POST` | `/api/v1/contacts/sync` | Push dirty contacts, receive changes |
| `GET` | `/api/v1/contacts/sync?since=` | Pull contact changes |

### Auth

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Authenticate, set session cookie |
| `POST` | `/api/v1/auth/register` | Create account, set session cookie |
| `POST` | `/api/v1/auth/logout` | Invalidate session |

### Admin

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/users` | User listing (ADMIN only) |
| `POST` | `/admin/users/{id}/toggle-enabled` | Enable/disable user |
| `POST` | `/admin/users/{id}/toggle-role` | Promote/demote role |

### Other

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check (DB status) |
| `GET` | `/metrics` | Prometheus metrics (requires auth) |
| `GET` | `/api/v1/notifications` | User notifications |
| `POST` | `/api/v1/notifications/{id}/read` | Mark notification read |
| `POST` | `/api/v1/notifications/read-all` | Mark all read |
| `WS` | `/ws/sync` | WebSocket for real-time UI refresh |

## Scripts

| Script | Purpose |
|---|---|
| `scripts/start-web.ps1` | Start web dev stack (Tailwind watcher, Maven watcher, app) |
| `scripts/stop-web.ps1` | Kill web dev stack |
| `scripts/start-swing.ps1` | Start Swing desktop client |
| `generate-jooq.ps1` | Regenerate jOOQ sources |

---

## Configuration Reference

All configuration is read from `application.yaml` (or `application-{profile}.yaml`) and may be overridden by environment variables.

### Runtime Sizing (runtime section)

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `runtime.hikariMaximumPoolSize` | `HIKARI_MAX_POOL_SIZE` | 20 | HikariCP maximum connections |
| `runtime.hikariMinimumIdle` | `HIKARI_MIN_IDLE` | 2 | HikariCP minimum idle connections |
| `runtime.hikariIdleTimeoutMs` | `HIKARI_IDLE_TIMEOUT_MS` | 300000 | Max idle time before pool removes connection |
| `runtime.hikariMaxLifetimeMs` | `HIKARI_MAX_LIFETIME_MS` | 1800000 | Max lifetime of a connection in the pool |
| `runtime.hikariConnectionTimeoutMs` | `HIKARI_CONNECTION_TIMEOUT_MS` | 10000 | Max wait for a connection from the pool |
| `runtime.hikariLeakDetectionThresholdMs` | `HIKARI_LEAK_DETECTION_THRESHOLD_MS` | 60000 | Leak detection threshold |
| `runtime.flywayEnabled` | `FLYWAY_ENABLED` | true | Run Flyway migrations on startup. Set `false` when running migrations externally. |
| `runtime.jtePreloadEnabled` | `JTE_PRELOAD_ENABLED` | false | Preload JTE template classes at startup. Reduces first-request latency. |
| `runtime.cacheMessageMaxSize` | `CACHE_MESSAGE_MAX_SIZE` | 1000 | Max entries in message cache |
| `runtime.cacheMessageExpireMinutes` | `CACHE_MESSAGE_EXPIRE_MINUTES` | 10 | Message cache entry TTL |
| `runtime.cacheGravatarMaxSize` | `CACHE_GRAVATAR_MAX_SIZE` | 10000 | Max entries in gravatar cache |
| `runtime.rateLimitIpCapacity` | `RATE_LIMIT_IP_CAPACITY` | 10 | Per-IP token bucket capacity |
| `runtime.rateLimitIpRefillPerMinute` | `RATE_LIMIT_IP_REFILL_PER_MINUTE` | 10 | Per-IP token refill rate (tokens/minute) |
| `runtime.rateLimitAccountCapacity` | `RATE_LIMIT_ACCOUNT_CAPACITY` | 20 | Per-account token bucket capacity |
| `runtime.rateLimitAccountWindowMs` | `RATE_LIMIT_ACCOUNT_WINDOW_MS` | 900000 | Per-account token refill window (ms) |

### Example: Small Profile

```yaml
runtime:
  hikariMaximumPoolSize: 4
  hikariMinimumIdle: 1
  flywayEnabled: false
  jtePreloadEnabled: false
  cacheMessageMaxSize: 100
  rateLimitIpCapacity: 20
```

### Example: Large Profile

```yaml
runtime:
  hikariMaximumPoolSize: 50
  hikariMinimumIdle: 5
  flywayEnabled: true
  jtePreloadEnabled: true
  cacheMessageMaxSize: 10000
  cacheMessageExpireMinutes: 30
```

### Core Configuration

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `port` | `PORT` | 8080 | HTTP server port |
| `jdbcUrl` | `JDBC_URL` | `jdbc:postgresql://localhost:5432/outerstellar` | Database JDBC URL |
| `jdbcUser` | `JDBC_USER` | `outerstellar` | Database user |
| `jdbcPassword` | `JDBC_PASSWORD` | `outerstellar` | Database password |
| `profile` | `APP_PROFILE` | `default` | Active configuration profile |
| `devDashboardEnabled` | `DEV_DASHBOARD_ENABLED` | false | Enable `/admin/dev` dashboard |
| `devMode` | `DEVMODE` | false | Enable dev auto-login |
| `sessionCookieSecure` | `SESSIONCOOKIESECURE` | true | Set Secure flag on session cookie |
| `sessionTimeoutMinutes` | `SESSIONTIMEOUTMINUTES` | 30 | Session idle timeout |
| `corsOrigins` | `CORSORIGINS` | "" | Allowed CORS origins (comma-separated) |
| `csrfEnabled` | `CSRFENABLED` | true | Enable CSRF protection |
| `appBaseUrl` | `APPBASEURL` | http://localhost:8080 | External URL for canonical links |
| `maxFailedLoginAttempts` | `MAX_FAILED_LOGIN_ATTEMPTS` | 10 | Account lockout threshold |
| `lockoutDurationSeconds` | `LOCKOUT_DURATION_SECONDS` | 900 | Account lockout duration |
| `cspPolicy` | `CSP_POLICY` | (default CSP string) | Content-Security-Policy header |
| `version` | `VERSION` | `dev` | Application version label |

---

### JVM Tuning (items #17–18)

For small deployments (<256 MB heap), use these JVM flags:

```bash
java -Xms64m -Xmx256m -XX:+UseContainerSupport -XX:+UseSerialGC -jar platform-web/target/platform-web-*.jar
```

- `-Xms64m` / `-Xmx256m` — small heap, no over-allocation
- `-XX:+UseContainerSupport` — respect container memory limits (default in JDK 10+)
- `-XX:+UseSerialGC` — single-threaded GC, best for heaps < 512 MB

For standard deployments (256 MB–2 GB heap), the default G1GC is appropriate:

```bash
java -Xms256m -Xmx1g -jar platform-web/target/platform-web-*.jar
```

For large deployments (2 GB+ heap), enable parallel GC and preload:

```bash
java -Xms2g -Xmx4g -XX:+UseParallelGC -Djte.production=true -DJTE_PRELOAD_ENABLED=true -jar platform-web/target/platform-web-*.jar
```

> **JVM vs Native Image (item #17):** Native image (GraalVM) can reduce cold start time from ~3-5s to <1s and idle RSS from ~150 MB to ~50 MB. However, peak throughput is typically comparable or slightly lower than JVM due to the absence of JIT optimizations. The best deployment strategy is: use JVM for steady-state workloads (servers), use native-image for short-lived or auto-scaling workloads (serverless, batch jobs). Run your own benchmark with `APP_PROFILE=small` on both runtimes before choosing.
