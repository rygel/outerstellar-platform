# Test Suite Improvement Plan

Comprehensive analysis of systemic issues in the Outerstellar Platform test suite. Each item includes the problem, its impact, and a proposed fix.

---

## 1. Stale Classpath Fragility (CRITICAL)

**Problem:** When `platform-persistence-jdbi` or `platform-security` is recompiled but not re-installed, `platform-web`'s test classpath picks up old JARs from `~/.m2/repository/`. Running `mvn -pl platform-web test` without first `mvn -pl platform-persistence-jdbi clean install -DskipTests` causes phantom test failures that don't exist in the actual source code.

**Impact:** We spent hours debugging "pre-existing" failures that were caused entirely by stale classes. Tests pass after `clean install` but fail in a bare `verify`. This is the single biggest time sink in the test workflow.

**Fix options:**
- **Option A (recommended):** Add a Maven enforcer rule or build-helper extension that detects when a SNAPSHOT dependency in the local repo is older than the corresponding source module's `target/classes`. Fail the build with a clear message like "Run `mvn clean install -DskipTests` on module X before testing module Y."
- **Option B:** Add a script `test-all.sh` / `test-all.ps1` that always runs `mvn clean install -DskipTests` on the full reactor before running `mvn verify`. This is the current documented workaround in AGENTS.md but is manual and easy to forget.
- **Option C:** Configure Surefire to use `useModulePath false` and rely on the reactor's compiled output instead of the local Maven repo. This requires testing.

**Files to change:** Root `pom.xml` (enforcer rule), `AGENTS.md` (document the requirement).

---

## 2. DELETE-Based Cleanup Is Duplicated and Fragile (HIGH)

**Problem:** `WebTest.cleanup()` and `JdbiTest.cleanDatabase()` each maintain an identical 17-18 line chain of `DELETE FROM` statements, manually ordered for FK safety. When a new table is added, both must be updated independently. There is no automated check that the cleanup list covers all tables.

**Impact:** Adding a new table without updating the cleanup list causes FK violation errors in unrelated tests. This is a silent trap — it doesn't fail at compile time.

**Fix:**
1. Extract the table list into a shared constant (e.g., `TestCleanup.TABLES_IN_FK_ORDER` in `platform-core` test-jar or a new `platform-test-fixtures` module).
2. Generate the list at build time from Flyway migrations (similar to how `migrations.index` is generated).
3. Consider switching to `TRUNCATE TABLE ... CASCADE` which:
   - Resets auto-increment sequences
   - Is faster than row-by-row DELETE
   - Requires fewer maintenance updates (CASCADE handles FK ordering automatically)
   - Caveat: TRUNCATE cannot run inside a transaction in some DB configs — test this.

**Files to change:** `WebTest.kt`, `JdbiTest.kt`, potentially a new shared test utility module.

---

## 3. Cleanup Is Opt-In in WebTest (HIGH)

**Problem:** In `JdbiTest`, cleanup runs automatically via `@AfterEach` in the base class. In `WebTest`, `cleanup()` is a static companion method — every test class must manually add `@AfterEach fun teardown() = cleanup()`. Forgetting this causes silent test pollution.

**Impact:** Some tests defensively call `cleanup()` in both `@BeforeEach` AND `@AfterEach` because of past pollution incidents. This is a code smell that indicates the underlying pattern is unreliable.

**Fix:** Make `WebTest` an abstract class with `@AfterEach fun teardown() = cleanup()` in the base, identical to how `JdbiTest` does it. Test classes extend `WebTest()` and inherit automatic cleanup.

**Files to change:** `WebTest.kt` — move `cleanup()` call to an `@AfterEach` method in the base class.

---

## 4. ArchitectureTest Doesn't Actually Enforce Rules (HIGH)

**Problem:** `ArchitectureTest.kt` in `platform-core` defines 9 ArchUnit rules, but runs on `platform-core`'s classpath which only contains core classes. All rules targeting security, persistence, web, desktop, or sync modules use `allowEmptyShould(true)` and match 0 classes — they always pass regardless of violations.

**Impact:** Architectural constraints (no cycles, proper layering, repository interface placement) appear tested but are not actually validated for any module other than core. A developer could introduce a forbidden dependency in `platform-web` and the test would still pass.

**Fix options:**
- **Option A (recommended):** Create a new `architecture-tests` module (or test scope in root) that depends on ALL modules and runs ArchUnit against the full classpath. This is the standard ArchUnit approach for multi-module projects.
- **Option B:** Add ArchUnit tests in each module that validate rules against that module's own classes plus all dependencies visible on its classpath.
- **Option C:** Use ArchUnit's `classeses()` API to import from JAR files directly, bypassing the classpath limitation.

**Files to change:** New `architecture-tests` module or root-level test, `pom.xml` for module wiring.

---

## 5. `createUser()` Duplicated 6 Times (MEDIUM)

**Problem:** Six persistence test files contain an identical `createUser()` helper function:

- `JdbiPasswordResetRepositoryTest.kt`
- `JdbiSessionRepositoryTest.kt`
- `JdbiNotificationRepositoryTest.kt`
- `JdbiApiKeyRepositoryTest.kt`
- `JdbiOAuthRepositoryTest.kt`
- `JdbiDeviceTokenRepositoryTest.kt`

Each creates a `User` with random username/email, saves via `userRepo.save()`, returns the UUID.

**Impact:** If the `User` constructor signature changes (e.g., a new required field is added), all 6 files must be updated independently. Missing one causes compile errors that are tedious to track down.

**Fix:** Extract a shared `TestUserFactory` into `JdbiTest` base class or a companion utility:

```kotlin
// In JdbiTest
fun createUser(
    username: String = "user_${UUID.randomUUID().toString().take(6)}",
    role: UserRole = UserRole.USER,
): UUID {
    val user = User(
        id = UUID.randomUUID(),
        username = username,
        email = "$username@test.com",
        passwordHash = "\$2a\$04\$dummyhash",
        role = role,
    )
    JdbiUserRepository(sharedJdbi).save(user)
    return user.id
}
```

Then replace all 6 duplicates with `createUser()`.

**Files to change:** `JdbiTest.kt` (add helper), all 6 test files (remove duplicate, use base class method).

---

## 6. Two Separate PostgreSQL Containers (MEDIUM)

**Problem:** `WebTest` and `JdbiTest` each start their own `PostgreSQLContainer("postgres:18")`. Since they run in different JVM forks (different Maven modules), this means 2 containers, ~2x startup time (~15s each), and ~2x memory.

**Impact:** Not a correctness issue, but slows down the full reactor build and wastes resources on developer machines. The total container overhead is ~30s + ~500MB RAM.

**Fix options:**
- **Option A (recommended):** Use Testcontainers' reuse feature (`testcontainers.reuse.enable=true` in `~/.testcontainers.properties`) with a shared container definition. Both modules connect to the same container using a known database name. This requires careful cleanup isolation.
- **Option B:** Accept the overhead as a trade-off for test isolation. Document it.
- **Option C:** Create a `platform-test-base` module that both `platform-web` and `platform-persistence-jdbi` depend on for test utilities. The container would still be per-fork but the configuration would be centralized.

**Investigation needed:** Whether Maven Surefire forks share a JVM when running the reactor with `-pl` — if they do, a shared companion object approach could work.

---

## 7. No Test Data Builders (MEDIUM)

**Problem:** Every test manually constructs domain objects with `UUID.randomUUID()` and hardcoded field values:

```kotlin
testUser = User(
    id = UUID.randomUUID(),
    username = "searchuser",
    email = "search@test.com",
    passwordHash = encoder.encode("T3st@xxxxxxxxxxxx"),
    role = UserRole.USER,
)
userRepository.save(testUser)
```

This pattern repeats in nearly every test file.

**Impact:** Verbose, repetitive test setup. When domain model constructors change, many test files need updates. No standardized way to create "valid default" test data.

**Fix:** Create a `TestFixture` or `TestData` object with builder-style factory methods:

```kotlin
object TestFixture {
    fun user(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        email: String = "${username}@test.com",
        role: UserRole = UserRole.USER,
    ) = User(
        id = UUID.randomUUID(),
        username = username,
        email = email,
        passwordHash = BCryptPasswordEncoder(4).encode("T3st@password123"),
        role = role,
    )

    fun message(author: String = "author", content: String = "test content") = ...
    fun contact(name: String = "contact") = ...
    // etc.
}
```

**Files to change:** New `TestFixture.kt` in a shared test location, incremental adoption across test files.

---

## 8. Raw SQL in Tests Couples to Physical Schema (LOW)

**Problem:** `JdbiUserRepositoryTest`, `JdbiUserRepositoryStatsTest`, and `JdbiPollRepositoryTest` insert test data via raw SQL (`jdbi.useHandle { handle.execute("INSERT ...") }`). This is necessary to set fields like `created_at` to specific values that the repository API doesn't expose.

**Impact:** If a column is renamed or a constraint changes, the test breaks with a cryptic SQL error instead of a clear assertion failure. Tests are coupled to the physical DDL instead of the logical API.

**Fix options:**
- **Option A:** Add test-only repository methods or a `TestDataHelper` class that encapsulates the raw SQL. If the schema changes, only the helper needs updating.
- **Option B:** Add optional `createdAt` parameters to `save()` or expose a test-only `insertWithTimestamps()` method.
- **Option C (minimal):** Accept raw SQL for the few tests that need it but document the coupling with a comment.

---

## 9. `hashToken()` Duplicated from Production (LOW)

**Problem:** `JdbiSessionRepositoryTest` contains its own SHA-256 hashing implementation instead of importing from production code.

**Impact:** If the production hashing algorithm changes, the test would still pass with the old algorithm — a false negative. Security-sensitive code should be tested against the actual implementation.

**Fix:** Import and use the production hashing function (from `JdbiSessionRepository.Companion.hashToken()` or a shared utility) instead of reimplementing it in the test.

**Files to change:** `JdbiSessionRepositoryTest.kt` — import production hash function.

---

## 10. Web Tests Rebuild the HTTP App Per Test Method (LOW)

**Problem:** Each `@BeforeEach` in web integration tests calls `buildApp()`, which constructs a full `SecurityService`, `WebPageFactory`, and http4k `HttpHandler`. For 593 tests, this means ~593 object construction cycles.

**Impact:** Adds ~15-30 seconds to the total test suite execution time. Not catastrophic but noticeable.

**Fix options:**
- **Option A:** Build the app once per test class (`@BeforeAll`) instead of per test method (`@BeforeEach`). Only reset database state in `@BeforeEach`. This works because the app is stateless — all mutable state lives in the database.
- **Option B (minimal):** Accept the overhead as a trade-off for maximum isolation. The current approach is safe and predictable.

**Investigation needed:** Whether any test class relies on per-test `buildApp()` customization (e.g., `TestOverrides`). If so, Option A would need a `@BeforeEach` override only for those classes.

---

## Priority Order for Implementation

| Priority | Item | Effort | ROI |
|----------|------|--------|-----|
| 1 | #1 Stale classpath detection | Medium | Very High — prevents phantom failures |
| 2 | #3 Opt-in cleanup in WebTest | Small | High — prevents test pollution |
| 3 | #2 Duplicated fragile cleanup | Medium | High — prevents FK violations on schema changes |
| 4 | #4 ArchitectureTest not enforcing | Medium | High — prevents architectural drift |
| 5 | #5 `createUser()` duplication | Small | Medium — reduces maintenance burden |
| 6 | #7 Test data builders | Medium | Medium — reduces boilerplate |
| 7 | #6 Two PostgreSQL containers | Medium | Medium — faster builds |
| 8 | #8 Raw SQL coupling | Small | Low — only affects 3 files |
| 9 | #9 `hashToken()` duplication | Small | Low — one file |
| 10 | #10 App rebuild per test | Small | Low — marginal speed improvement |
