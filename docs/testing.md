# Testing Guide

This document describes the Outerstellar Platform test architecture, patterns, and conventions. It is the authoritative reference for how to write tests in this project.

## Table of Contents

- [Test Architecture](#test-architecture)
- [Container Caching](#container-caching)
- [Test Base Classes](#test-base-classes)
- [How to Write New Tests](#how-to-write-new-tests)
- [JTE Template Testing](#jte-template-testing)
- [End-to-End Tests Only](#end-to-end-tests-only)
- [Desktop Testing](#desktop-testing)
- [http4k Testing Modules](#http4k-testing-modules)
- [Anti-Patterns](#anti-patterns)
- [Running Tests](#running-tests)

## Test Architecture

Every test in this project exercises the full stack. There are no unit tests that mock databases — tests hit a real PostgreSQL instance via Testcontainers, run actual Flyway migrations, and verify behavior end-to-end through the HTTP layer or the repository layer.

The test infrastructure has three layers:

```
platform-web:        WebTest (shared container + datasource + repos + http4k app)
                        |
platform-persistence-jdbi:  JdbiTest (shared container + datasource + Jdbi handle)
                        |
platform-core / platform-security / platform-sync-client:  No database (pure unit tests)
```

- **WebTest** starts one PostgreSQL container, runs all migrations, and constructs all repositories and the full http4k HTTP handler. ~50 integration test classes extend it.
- **JdbiTest** starts one PostgreSQL container, runs all migrations, and provides a shared Jdbi handle. All JDBI repository tests extend it.
- **platform-core, platform-security, platform-sync-client** have no database dependency. Their tests are pure unit tests (MockK, in-memory stubs).

### Maven Surefire Configuration

- **`reuseForks: true`** — one JVM fork per module. Companion-object singletons are shared across all test classes in the fork.
- **`forkedProcessTimeoutInSeconds: 300`** — 5-minute hard kill per fork. If a test hangs, it gets killed.
- **`junit.jupiter.execution.timeout.default: 120s`** — per-test timeout.
- **`parallel: classes`** at root level, but **`parallel: none`** for platform-web (shared mutable database state cannot tolerate concurrency).
- Platform-web tests run sequentially. All other modules run up to 4 test classes concurrently.

## Container Caching

### One PostgreSQL per JVM Fork

Each test base class uses a Kotlin companion object to hold a singleton `PostgreSQLContainer`. Because `reuseForks=true`, this container starts once and is reused by every test class in the module:

```kotlin
companion object {
    private val container = PostgreSQLContainer<Nothing>("postgres:18").apply {
        withDatabaseName("outerstellar")
        withUsername("outerstellar")
        withPassword("outerstellar")
        start()
    }
}
```

### Lazy Initialization for All Resources

Everything except the container itself uses `by lazy` so it initializes on first access, not on class load:

```kotlin
private val dataSource: DataSource by lazy {
    createDataSource(container.jdbcUrl, container.username, container.password)
        .also { migrate(it) }
}

val testDsl: DSLContext by lazy { DSL.using(dataSource, POSTGRES) }

val userRepository by lazy { JdbiUserRepository(jdbi) }
val messageRepository by lazy { JdbiMessageRepository(jdbi) }
```

This means if a test class only uses `userRepository`, the `messageRepository` is never constructed. Expensive resources (DataSource, DSLContext, repositories) initialize exactly once, on demand.

### Row-Level Cleanup Between Tests

Tests do not restart the container or recreate the database between tests. Instead, they delete all rows in foreign-key-safe order:

```kotlin
@AfterEach
fun cleanup() {
    testJdbi.useHandle<Exception> { handle ->
        CleanupTables.ALL.forEach { table -> handle.execute("DELETE FROM $table") }
    }
}
```

This is fast (milliseconds) compared to container restart (seconds).

## Test Base Classes

### WebTest (`platform-web`)

**Path:** `platform-web/src/test/kotlin/.../web/WebTest.kt`

The primary test base for all platform-web integration tests. Provides:

| Resource | Type | Notes |
|----------|------|-------|
| `container` | `PostgreSQLContainer` | Singleton, shared across ~50 test classes |
| `testConfig` | `AppConfig` | Pre-configured with container JDBC details |
| `testJdbi` | `Jdbi` | Database context, lazily initialized |
| `renderer` | JTE renderer | Precompiled (`jte.production=true`) |
| `userRepository`, `messageRepository`, etc. | Repository instances | All lazily initialized |
| `buildApp(...)` | `HttpHandler` | Factory that assembles the full http4k app |
| `createSecurityService()` | `SecurityService` | Constructs SecurityService with default repos |
| `withAuthenticatedUser()` | `Triple<token, userId, username>` | Creates user + session, returns cookie value |
| `testPasswordHash` | `String` | Pre-computed BCrypt hash (cached, not per-test) |
| `cleanup()` | Row-level DELETE | Called in `@AfterEach` |

**Usage pattern:**

```kotlin
class MyFeatureTest : WebTest() {

    private val app: HttpHandler by lazy { buildApp() }

    @AfterEach
    fun reset() = cleanup()

    @Test
    fun `should do something meaningful`() {
        // Arrange: insert test data directly via repository
        val user = userRepository.create(User(...))

        // Act: hit the HTTP handler
        val response = app(Request(GET, "/messages"))

        // Assert: use http4k hamkrest matchers
        assertThat(response, hasStatus(Status.OK).and(bodyContains(user.displayName)))
    }
}
```

**Overriding dependencies:** Use `TestOverrides` to swap individual services without changing the base class:

```kotlin
private val app: HttpHandler by lazy {
    buildApp(overrides = TestOverrides(
        messageCache = StubMessageCache(),
        notificationService = myMockNotificationService,
    ))
}
```

### JdbiTest (`platform-persistence-jdbi`)

**Path:** `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiTest.kt`

Provides a shared Jdbi handle against the singleton PostgreSQL container.

**Usage pattern:**

```kotlin
class JdbiMessageRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiMessageRepository(jdbi) }

    @AfterEach
    fun reset() = cleanup()

    @Test
    fun `should insert and retrieve message`() {
        val msg = repo.create(Message(...))
        val found = repo.findById(msg.id)
        assertNotNull(found)
        assertEquals(msg.syncId, found.syncId)
    }
}
```

## How to Write New Tests

### For platform-web (HTTP integration tests)

1. Extend `WebTest`
2. Use `by lazy` for the app and any test-specific dependencies
3. Call `cleanup()` in `@AfterEach`
4. Insert test data via repositories (not HTTP)
5. Call `app(Request(...))` to exercise the HTTP layer
6. Assert on status code **and** response body content and/or side effects

### For platform-persistence-jdbi (repository tests)

1. Extend `JdbiTest`
2. Use `by lazy` for the repository under test
3. Call `cleanup()` in `@AfterEach`
4. Insert test data, call repository methods, verify state

### For platform-core, platform-security, platform-sync-client (unit tests)

1. Use MockK for mocking dependencies
2. No database needed — test business logic in isolation
3. Use the stubs in `Stubs.kt` (platform-web) when you need no-op implementations

## JTE Template Testing

Always use precompiled templates in tests. The Maven Surefire config sets `jte.production=true`, which means `createRenderer()` uses `TemplateEngine.createPrecompiled(ContentType.Html)` — templates are compiled at build time by the `jte-maven-plugin`, not at test runtime.

**Never** use `TemplateEngine.create(DirectoryCodeResolver(...))` in tests. Source compilation adds 60+ seconds per Surefire fork.

## End-to-End Tests Only

This project uses **full end-to-end tests only**. There are zero smoke tests.

### What is a proper test

A proper test asserts **meaningful behavior** — correct data, correct state transitions, correct error handling, correct HTML content. It exercises the full stack: HTTP request → filters → routes → services → persistence → database.

Examples of proper assertions:
- Response body contains specific HTML elements with expected data
- After a POST, a subsequent GET reflects the created entity
- A deleted entity no longer appears in listings
- Error responses contain specific error messages
- Side effects (audit log entries, notifications) are verifiable

### What is a smoke test (forbidden)

A smoke test is a superficial check that validates infrastructure, not behavior:

| Smoke test (wrong) | Proper test (right) |
|---------------------|---------------------|
| `assertEquals(200, response.status)` with no body assertion | Verify response body contains expected data |
| "Does it start?" | "Does it return the correct page with correct content?" |
| "Does the endpoint exist?" | "Does the endpoint handle valid input, invalid input, and edge cases?" |
| Only checking status code | Checking status code + body + side effects |

If you are tempted to write a test that only checks an HTTP status code without verifying the response body or side effects, write a real test instead.

### Example: Bad vs. Good

```kotlin
// BAD: smoke test — proves nothing meaningful
@Test
fun `health check returns 200`() {
    val response = app(Request(GET, "/health"))
    assertThat(response, hasStatus(Status.OK))
}

// GOOD: end-to-end test — verifies actual behavior
@Test
fun `health check reports database connectivity`() {
    val response = app(Request(GET, "/health"))
    assertThat(response, hasStatus(Status.OK).and(bodyContains("UP")))
}
```

## Desktop Testing

Desktop/Swing tests must **never** run directly on the host machine — they capture the mouse and keyboard. Always use Podman:

```powershell
# Build the test image (only when dependencies change)
podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .

# Run tests
podman run --rm --network host `
    -v "${env:USERPROFILE}\.m2\repository:/root/.m2/repository" `
    -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml" `
    -v "/var/run/docker.sock:/var/run/docker.sock" `
    outerstellar-test-desktop
```

## http4k Testing Modules

The project uses four http4k testing modules for assertion quality and template verification:

### hamkrest matchers (`http4k-testing-hamkrest`)

All HTTP assertions use hamkrest matchers from `HttpMatchers.kt`. Compose with `.and()`:

```kotlin
import io.github.rygel.outerstellar.platform.web.*

assertThat(response, hasOkStatus())
assertThat(response, hasStatus(Status.NOT_FOUND))
assertThat(response, bodyContains("expected text"))
assertThat(response, hasContentType("text/html"))
assertThat(response, hasRedirect("/auth/sign-in"))
assertThat(response, hasOkStatus().and(bodyContains("data")))
```

### Approval testing (`http4k-testing-approval`)

Snapshot HTML responses to golden files. Catches template regressions that hamkrest assertions miss:

```kotlin
@ExtendWith(ApprovalTest::class)
class ErrorPagesApprovalTest : WebTest() {
    @Test
    fun `error page matches snapshot`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/errors/not-found")))
    }
}
```

Golden files (`.approved`) live in `src/test/resources/`. To accept changes, copy `.actual` to `.approved` and commit.

### WebDriver (`http4k-testing-webdriver`)

JSoup-backed WebDriver — navigate HTML pages, find elements, submit forms — all in-memory:

```kotlin
val driver = Http4kWebDriver(app)
driver.navigate().to("http://test/auth/sign-in")
```

### Chaos (`http4k-testing-chaos`)

Inject failures (500 errors, latency) into the HTTP handler for resilience testing.

## Anti-Patterns

### Never create your own PostgreSQL container

If you're in platform-web, extend `WebTest`. If you're in platform-persistence-jdbi, extend `JdbiTest`. These classes already manage a shared container. Creating another container wastes ~10 seconds of startup time per redundant instance.

### Never eagerly initialize expensive resources

Use `by lazy` for DataSource, DSLContext, Jdbi handles, repositories, and any service that depends on them. Eager initialization in `@BeforeEach` creates unnecessary overhead when test classes only use a subset of resources.

### Never use `TemplateEngine.create(DirectoryCodeResolver(...))` in tests

This compiles JTE templates from source at test runtime. It adds 60+ seconds per Surefire fork. The production code path (`createRenderer()`) already uses precompiled templates when `jte.production=true`.

### Never write smoke tests

See [End-to-End Tests Only](#end-to-end-tests-only). Every test must assert meaningful behavior beyond HTTP status codes.

### Never run desktop tests on the host

See [Desktop Testing](#desktop-testing). Use Podman containers exclusively.

## Running Tests

### Full reactor (non-desktop modules)

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

### Single module

```powershell
mvn -pl platform-web test
mvn -pl platform-persistence-jdbi test
```

### Specific test class

```powershell
mvn -pl platform-web test -Dtest=HealthCheckIntegrationTest
```

### Skip CSS build (when npm hangs)

```powershell
mvn -pl platform-web test -Dexec.skip=true
```

### Fast compile (skip quality checks)

```powershell
mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

### Desktop tests in Podman

See [Desktop Testing](#desktop-testing).
