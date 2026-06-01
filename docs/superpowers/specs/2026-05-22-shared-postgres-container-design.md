# Shared PostgreSQL Container with Per-Class Databases

**Date:** 2026-05-22
**Status:** Approved
**Scope:** Test infrastructure — all modules with database-dependent tests

## Problem

Each module with database tests starts its own PostgreSQL container:
- `WebTest` (55 tests, platform-web) — one container per JVM fork
- `JdbiTest` (13 tests, platform-persistence-jdbi) — one container per JVM fork
- `KoinModuleTest`, `SwingStartupE2ETest`, Playwright E2E tests — each start their own container

That's 2–6 container startups per `mvn verify` run. Each startup takes ~3–5 seconds.

Tests share a single `outerstellar` database within each module. Test isolation relies on `@AfterEach` DELETE across all tables in FK-safe order via `CleanupTables.ALL`. This creates:
- Fragile FK ordering that must be updated when tables are added
- State leakage risk if cleanup is skipped or ordering is wrong
- No parallel-safety within JdbiTest (runs `parallel=classes` with 4 threads against one database)

## Decision

1. **One reused PostgreSQL container** across all modules via Testcontainers `withReuse(true)`
2. **Per-class databases** — each test class gets its own database, dropped in `@AfterAll`
3. **`@TestInstance(PER_CLASS)`** on both `WebTest` and `JdbiTest` base classes
4. **No cleanup code** — databases are dropped, not DELETE'd

## Architecture

### New module: `platform-testkit`

A small shared test utility module producing a `test-jar`.

```
platform-testkit/
  src/main/kotlin/
    SharedPostgres.kt       — container lifecycle + CREATE/DROP DATABASE
    TestDatabase.kt         — per-class database handle (jdbcUrl, Jdbi, DataSource)
  src/main/resources/
    .testcontainers.properties  — testcontainers.reuse.enable=true
```

Sources go in `src/main/kotlin` (not `src/test/kotlin`) because `maven-jar-plugin`'s `test-jar` goal only packages compiled main classes. Test sources are excluded.

### SharedPostgres

Singleton object that manages the reused container:

```kotlin
object SharedPostgres {
    private val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:18")
            .withDatabaseName("outerstellar_base")
            .withUsername("outerstellar")
            .withPassword("outerstellar")
            .withReuse(true)
            .apply { start() }

    private val baseJdbcUrl: String
        get() = container.jdbcUrl.replaceAfterLast('/', "")

    fun createDatabase(name: String): TestDatabase {
        val dbName = "test_$name"
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().execute("CREATE DATABASE $dbName")
        }
        val jdbcUrl = baseJdbcUrl + dbName
        return TestDatabase(dbName, jdbcUrl, container.username, container.password)
    }

    fun dropDatabase(dbName: String) {
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
            conn.createStatement().execute("DROP DATABASE IF EXISTS $dbName")
        }
    }
}
```

Key details:
- `withReuse(true)` — container survives JVM exit, reusable across Surefire forks
- `createDatabase()` — CREATE DATABASE via the base connection, returns a `TestDatabase`
- `dropDatabase()` — DROP DATABASE IF EXISTS after tests finish
- Database name is sanitized by the caller (derived from test class name, lowercase, underscores only, max 58 chars to stay under PostgreSQL's 63-char identifier limit with the `test_` prefix)

### TestDatabase

Per-class database handle:

```kotlin
class TestDatabase(
    val dbName: String,
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String,
) {
    val dataSource: DataSource by lazy {
        createDataSource(jdbcUrl, jdbcUser, jdbcPassword).also { migrate(it) }
    }

    val jdbi: Jdbi by lazy { Jdbi.create(dataSource) }

    fun drop() {
        SharedPostgres.dropDatabase(dbName)
    }
}
```

- `dataSource` lazily created with Flyway migration applied on first access
- `jdbi` lazily created from the datasource
- `drop()` called in `@AfterAll`

### Container reuse configuration

`platform-testkit/src/main/resources/.testcontainers.properties`:
```
testcontainers.reuse.enable=true
```

This file is on the classpath of every module that depends on `platform-testkit`. Testcontainers reads it automatically. No per-machine configuration needed.

### Maven module setup

`platform-testkit/pom.xml`:
- Depends on: `platform-core`, `platform-persistence-jdbi` (for `createDataSource`, `migrate`), `testcontainers-postgresql`, `jdbi3-core`, `slf4j-api`
- Produces a `test-jar` via `maven-jar-plugin` with `test-jar` goal
- Parent: root POM (inherits version management, extensions)

Consumers add:
```xml
<dependency>
    <groupId>io.github.rygel</groupId>
    <artifactId>outerstellar-platform-testkit</artifactId>
    <version>${project.version}</version>
    <classifier>tests</classifier>
    <scope>test</scope>
</dependency>
```

### Database name sanitization utility

```kotlin
fun sanitizeDbName(className: String): String =
    className.lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .take(58)
```

Included in `SharedPostgres.kt` or as a top-level function. Used by WebTest and JdbiTest.

## WebTest changes

```kotlin
@TestInstance(PER_CLASS)
abstract class WebTest {
    private val testDb = SharedPostgres.createDatabase(
        sanitizeDbName(this::class.simpleName!!)
    )

    val testConfig by lazy {
        AppConfig(
            port = 0,
            jdbcUrl = testDb.jdbcUrl,
            jdbcUser = testDb.jdbcUser,
            jdbcPassword = testDb.jdbcPassword,
            devDashboardEnabled = true,
            csrfEnabled = false,
            corsOrigins = "*",
            appleOAuth = AppleOAuthConfig(
                enabled = true,
                clientId = "test.client.id",
                teamId = "test.team",
                keyId = "test.key",
                privateKeyPem = "test.pem",
            ),
        )
    }

    val testJdbi by lazy { testDb.jdbi }
    val userRepository by lazy { JdbiUserRepository(testJdbi) }
    val messageRepository by lazy { JdbiMessageRepository(testJdbi) }
    val contactRepository by lazy { JdbiContactRepository(testJdbi) }
    val sessionRepository by lazy { JdbiSessionRepository(testJdbi) }
    val apiKeyRepository by lazy { JdbiApiKeyRepository(testJdbi) }
    val auditRepository by lazy { JdbiAuditRepository(testJdbi) }
    val notificationRepository by lazy { JdbiNotificationRepository(testJdbi) }
    val passwordResetRepository by lazy { JdbiPasswordResetRepository(testJdbi) }
    val oauthRepository by lazy { JdbiOAuthRepository(testJdbi) }
    val pollRepository by lazy { JdbiPollRepository(testJdbi) }
    val pollService by lazy { PollService(pollRepository) }
    val renderer by lazy { createRenderer() }
    val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
    val testPasswordHash by lazy { encoder.encode("Test@12345678") }

    fun createSecurityService(userRepository: UserRepository = this.userRepository): SecurityService =
        SecurityService(userRepository, encoder, sessionRepository, apiKeyRepository, passwordResetRepository, auditRepository)

    fun withAuthenticatedUser(
        username: String = "testuser_" + UUID.randomUUID().toString().take(8),
        passwordHash: String = testPasswordHash,
        role: String = "USER",
    ): Triple<String, String, String> { /* unchanged */ }

    fun buildApp(
        config: AppConfig = testConfig,
        securityService: SecurityService = createSecurityService(),
        overrides: TestOverrides = TestOverrides(),
    ): HttpHandler { /* unchanged */ }

    fun cleanup() { /* no-op, kept for backward compatibility */ }

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
```

Changes from current WebTest:
- **Add** `@TestInstance(PER_CLASS)`
- **Move** all state from companion object to instance level
- **Remove** companion object entirely
- **Remove** `@AfterEach resetState()` / `cleanup()` becomes no-op
- **Add** `@AfterAll tearDown()` that drops the database
- All existing callers of `cleanup()` still compile (no-op)
- Each test class automatically gets its own database via `this::class.simpleName`

### Impact on WebTest subclasses

55 test classes currently look like:
```kotlin
class HealthCheckTest : WebTest() {
    private val app by lazy { buildApp() }

    @AfterEach
    fun reset() = cleanup()

    @Test
    fun `test`() { ... }
}
```

After:
```kotlin
class HealthCheckTest : WebTest() {
    private val app by lazy { buildApp() }

    @Test
    fun `test`() { ... }
}
```

The `@AfterEach fun reset() = cleanup()` line is deleted from all 55 classes. No other changes needed.

## JdbiTest changes

```kotlin
@TestInstance(PER_CLASS)
abstract class JdbiTest {
    private val testDb = SharedPostgres.createDatabase(
        sanitizeDbName(this::class.simpleName!!)
    )

    protected val jdbi: Jdbi by lazy { testDb.jdbi }

    protected fun createUser(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
    ): UUID {
        val id = UUID.randomUUID()
        JdbiUserRepository(jdbi).save(User(id = id, username = username, email = "${id.toString().take(6)}@example.com", passwordHash = "hash", role = role))
        return id
    }

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
```

Changes from current JdbiTest:
- **Add** `@TestInstance(PER_CLASS)`
- **Remove** `@BeforeEach setupDatabase()` — no more `lateinit var jdbi` assignment
- **Remove** `@AfterEach cleanDatabase()` — no more DELETE chain
- **Remove** companion object entirely (container, sharedJdbi)
- **Change** `jdbi` from `lateinit var` to `val ... by lazy`
- **Add** `@AfterAll tearDown()` that drops the database

## What gets deleted

- `CleanupTables.kt` — no longer needed for test cleanup. Verify `platform-seeder` doesn't use it; if it does, move the table list into seed.
- `@AfterEach` cleanup in both WebTest and JdbiTest
- Per-module container companion objects
- JdbiTest's companion object entirely
- `@AfterEach fun reset() = cleanup()` from all 55 WebTest subclasses

## E2E / special tests — not affected

The following have their own containers for legitimate reasons:

- `PlaywrightE2ETest` — starts a real Netty server, needs its own container
- `ResponsiveLayoutE2ETest` — same as above
- `SwingStartupE2ETest` — runs in Podman
- `KoinModuleTest` — only checks DI wiring, could optionally adopt `SharedPostgres`

These can be migrated later if desired.

## Performance estimate

Current: 2 container startups x ~4s = ~8s container overhead
After: 1 container startup x ~4s = ~4s (subsequent forks reuse the running container)

Database creation: ~68 x CREATE DATABASE (~5ms each) + ~68 x Flyway migration (~100ms each) = ~7s total

Net change: approximately the same total time, but with perfect test isolation and no cleanup code.

The real win is **eliminating all cleanup code and getting bulletproof test isolation**.

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Stale reused containers | `createDatabase()` detects and handles existing databases; `dropDatabase()` uses `IF EXISTS` |
| Database name collisions | Append `System.identityHashCode(this)` suffix to sanitized class name |
| Max connections | 68 databases in one PostgreSQL is trivial; PostgreSQL handles thousands |
| `PER_CLASS` lifecycle breaks stateful tests | No such tests exist — all state is in companion objects or lazy vals |
| CI container accumulation | `docker rm -f` step in CI workflow, or rely on CI worker recycling |
| `testcontainers.reuse.enable=true` not found | Packaged in `platform-testkit`'s resources, on classpath of all consumers |
