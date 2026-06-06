# Testing Guide

This document describes the Outerstellar Platform test architecture, patterns, and conventions. It is the authoritative reference for how to write tests in this project.

## Table of Contents

- [Test Architecture](#test-architecture)
- [Container Caching](#container-caching)
- [Test Base Classes](#test-base-classes)
- [How to Write New Tests](#how-to-write-new-tests)
- [JTE Template Testing](#jte-template-testing)
- [End-to-End Tests Only](#end-to-end-tests-only)
- [Error Handling Tests](#error-handling-tests)
- [Desktop Testing](#desktop-testing)
- [http4k Testing Modules](#http4k-testing-modules)
- [Anti-Patterns](#anti-patterns)
- [Running Tests](#running-tests)

## Test Architecture

Every test in this project exercises the full stack. There are no unit tests that mock databases — tests hit a real PostgreSQL instance via Testcontainers, run actual Flyway migrations, and verify behavior end-to-end through the HTTP layer or the repository layer.

The test infrastructure has three layers:

```
platform-testkit:  SharedPostgres (singleton reused container)
                                  + TestDatabase (per-class database)
                                      |
platform-web:        WebTest (per-class DB via SharedPostgres + repos + http4k app)
                                      |
platform-persistence-jdbi:  JdbiTest (per-class DB via SharedPostgres + Jdbi handle)
                                      |
platform-core / platform-security / platform-sync-client:  No database (pure unit tests)
```

- **SharedPostgres** (in `platform-testkit`) manages a single reused PostgreSQL 18 container via Testcontainers `withReuse(true)`. Each test class gets its own database via `CREATE DATABASE`, dropped in `@AfterAll`.
- **WebTest** creates a per-class database, runs all migrations, and constructs all repositories and the full http4k HTTP handler. ~55 integration test classes extend it.
- **JdbiTest** creates a per-class database, runs all migrations, and provides a Jdbi handle. All JDBI repository tests extend it.
- **platform-core, platform-security, platform-sync-client** have no database dependency. Their tests are pure unit tests (MockK, in-memory stubs).

### Maven Surefire Configuration

- **`reuseForks: true`** — one JVM fork per module. Container singleton is shared across all test classes in the fork.
- **`forkedProcessTimeoutInSeconds: 300`** — 5-minute hard kill per fork. If a test hangs, it gets killed.
- **`junit.jupiter.execution.timeout.default: 120s`** — per-test timeout.
- **platform-web: `parallel: classes`, `threadCount: 4`** — test classes run concurrently, each with its own database. Methods within a class run sequentially.
- All other modules run with root-level parallel settings.

## Container Architecture

### Shared PostgreSQL Container (platform-testkit)

All database-dependent tests share a single PostgreSQL 18 container managed by `SharedPostgres` in `platform-testkit`. The container uses Testcontainers `withReuse(true)` to persist across test runs.

Each test class gets its own database via `SharedPostgres.createDatabase(className)`, which runs `CREATE DATABASE` on the shared container. The database is dropped in `@AfterAll` via `testDb.drop()`. This means:

- No cross-class data contamination — each class starts with a fresh, migrated database
- `@AfterEach` cleanup deletes rows between test methods within a class
- Container startup happens once per JVM fork (not per test class)

```kotlin
@TestInstance(PER_CLASS)
abstract class WebTest {
    private val testDb = SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))
    val testJdbi: Jdbi by lazy { testDb.jdbi }

    @AfterEach
    fun cleanDatabase() { /* DELETE from all tables */ }

    @AfterAll
    fun tearDown() { testDb.drop() }
}
```

### Lazy Initialization for All Resources

Everything except the container itself uses `by lazy` so it initializes on first access, not on class load:

```kotlin
val testJdbi: Jdbi by lazy { testDb.jdbi }
val userRepository by lazy { JdbiUserRepository(testJdbi) }
val messageRepository by lazy { JdbiMessageRepository(testJdbi) }
```

This means if a test class only uses `userRepository`, the `messageRepository` is never constructed. Expensive resources (DataSource, Jdbi, repositories) initialize exactly once, on demand.

### Row-Level Cleanup Between Tests (within a class)

Tests do not drop and recreate the database between methods. Instead, they delete all rows in foreign-key-safe order:

```kotlin
@AfterEach
fun cleanDatabase() {
    testJdbi.useHandle<Exception> { handle ->
        tablesToDelete.forEach { table -> handle.execute("DELETE FROM $table") }
    }
}
```

This is fast (milliseconds) compared to recreating the database (hundreds of milliseconds).

## Test Base Classes

### WebTest (`platform-web`)

**Path:** `platform-web/src/test/kotlin/.../web/WebTest.kt`

The primary test base for all platform-web integration tests. Provides:

| Resource | Type | Notes |
|----------|------|-------|
| `testDb` | `TestDatabase` | Per-class database, created via `SharedPostgres.createDatabase()` |
| `testConfig` | `AppConfig` | Pre-configured with per-class DB JDBC details |
| `testJdbi` | `Jdbi` | Database context, lazily initialized |
| `renderer` | JTE renderer | Precompiled (`jte.production=true`) |
| `userRepository`, `messageRepository`, etc. | Repository instances | All lazily initialized |
| `buildApp(...)` | `HttpHandler` | Factory that assembles the full http4k app |
| `createSecurityService()` | `SecurityService` | Constructs SecurityService with default repos |
| `withAuthenticatedUser()` | `Triple<token, userId, username>` | Creates user + session, returns cookie value |
| `testPasswordHash` | `String` | Pre-computed BCrypt hash (cached, not per-test) |
| `cleanDatabase()` | Row-level DELETE | Called in `@AfterEach` |

**Usage pattern:**

```kotlin
class MyFeatureTest : WebTest() {

    private val app: HttpHandler by lazy { buildApp() }

    @Test
    fun `should do something meaningful`() {
        val user = withAuthenticatedUser()
        val response = app(Request(GET, "/messages").header("Authorization", "Bearer ${user.first}"))
        assertThat(response, hasStatus(Status.OK).and(bodyContains(user.third)))
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

Provides a per-class database via `SharedPostgres` and a Jdbi handle against it.

**Usage pattern:**

```kotlin
class JdbiMessageRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiMessageRepository(jdbi) }

    @Test
    fun `should insert and retrieve message`() {
        val msg = repo.create(Message(...))
        val found = repo.findById(msg.id)
        assertNotNull(found)
        assertEquals(msg.syncId, found.syncSyncId)
    }
}
```

## How to Write New Tests

### For platform-web (HTTP integration tests)

1. Extend `WebTest`
2. Use `by lazy` for the app and any test-specific dependencies
3. The base class handles `@AfterEach` cleanup (row deletion) and `@AfterAll` (database drop)
4. Insert test data via `withAuthenticatedUser()` or repositories (not HTTP)
5. Call `app(Request(...))` to exercise the HTTP layer
6. Assert on status code **and** response body content and/or side effects

### For platform-persistence-jdbi (repository tests)

1. Extend `JdbiTest`
2. Use `by lazy` for the repository under test
3. The base class handles `@AfterEach` cleanup (row deletion) and `@AfterAll` (database drop)
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

## Error Handling Tests

Error handling tests must verify clear behavior, not fallback behavior. A passing test should prove that the system reports the failure correctly, logs or exposes a useful diagnostic path, and does not hide missing required state behind guessed defaults.

Rules:
- Do not add tests that approve synthesized substitutes for required state, services, renderers, resources, locales, templates, or configuration.
- Test expected boundary responses explicitly: API JSON errors, HTMX-safe text errors, normal HTML error pages, and the renderer-independent emergency HTML response used only when normal error-page rendering fails.
- Assert that sensitive exception messages are not leaked to users, while request references and logged errors preserve enough context for diagnosis.
- When a fallback is truly intentional, name the test after the boundary behavior and explain why continuing is correct. This should be rare.
- If a missing dependency or invalid configuration is the scenario under test, assert a clear failure or validation error. Do not assert that the system quietly keeps going.

## Desktop Testing

Desktop/Swing tests must **never** run directly on the host machine — they capture the mouse and keyboard. Always use Podman:

```powershell
# Preferred wrapper: builds the desktop-test image, runs under Xvfb,
# and copies reports to platform-desktop/target/surefire-reports-docker.
pwsh scripts/test-desktop.ps1

# Bash equivalent:
bash docker/run-desktop-tests.sh

# Manual equivalent, if the wrapper is unavailable:
podman build --target desktop-test -t outerstellar-test-desktop -f docker/Dockerfile.build .
podman run --rm --network host `
    -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml:ro" `
    -e "DOCKER_HOST=unix:///var/run/docker.sock" `
    -v "/var/run/docker.sock:/var/run/docker.sock" `
    outerstellar-test-desktop
```

### Dockerfile dependency-cache stages

The desktop wrapper builds `docker/Dockerfile.build --target desktop-test`. That Dockerfile has partial-POM dependency-cache stages which copy `pom.xml` files before the full source tree.

When the Maven module list in the parent `pom.xml` changes, update every partial-POM copy list in `docker/Dockerfile.build`, including the `desktop-deps` stage. Otherwise the cache warmup command can fail with `Child module /app/<module> does not exist` before the real test stage runs. Treat that as a Dockerfile maintenance bug, even if the later full-source stage still passes.

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

If you're in platform-web, extend `WebTest`. If you're in platform-persistence-jdbi, extend `JdbiTest`. These classes already manage a per-class database via the shared container. Creating another container wastes ~10 seconds of startup time per redundant instance.

### Never eagerly initialize expensive resources

Use `by lazy` for DataSource, Jdbi handles, repositories, and any service that depends on them. Eager initialization in `@BeforeEach` creates unnecessary overhead when test classes only use a subset of resources.

### Never use `TemplateEngine.create(DirectoryCodeResolver(...))` in tests

This compiles JTE templates from source at test runtime. It adds 60+ seconds per Surefire fork. The production code path (`createRenderer()`) already uses precompiled templates when `jte.production=true`.

### Never write smoke tests

See [End-to-End Tests Only](#end-to-end-tests-only). Every test must assert meaningful behavior beyond HTTP status codes.

### Never test hidden fallbacks as success

Fallbacks are counterproductive in almost all error-handling paths. Do not write tests that make a broken dependency, missing request state, missing config, missing template, or unreadable file look successful by accepting a guessed substitute. The correct behavior is usually a clear error plus logging.

Allowed exception: an explicitly documented boundary response, such as API JSON errors, HTMX-safe text errors, or the renderer-independent emergency HTML page used when normal error rendering fails.

### Never run desktop tests on the host

See [Desktop Testing](#desktop-testing). Use Podman containers exclusively.

## Running Tests

### Full reactor (non-desktop modules)

```powershell
mvn --% clean verify -T4 -pl !platform-desktop,!platform-desktop-javafx
```

### Single module

```powershell
mvn -pl platform-web -am test
mvn -pl platform-persistence-jdbi -am test
```

### Specific test class

```powershell
mvn -pl platform-web -am test -Dtest=HealthCheckIntegrationTest
```

### Skip CSS build (when npm hangs)

```powershell
mvn -pl platform-web -am test -Dexec.skip=true
```

### Fast compile (skip quality checks)

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

### Desktop tests in Podman

See [Desktop Testing](#desktop-testing).
