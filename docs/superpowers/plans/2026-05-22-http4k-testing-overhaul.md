# http4k Testing Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate test waste, adopt http4k testing modules (hamkrest, approval, webdriver, chaos), and strengthen HTML template assertions.

**Architecture:** Four independent layers applied incrementally. Layer 1 is pure refactoring (no new deps). Layers 2-4 add http4k testing modules progressively. Each task compiles and passes independently.

**Tech Stack:** http4k 6.47.0.0, http4k-testing-hamkrest, http4k-testing-approval, http4k-testing-webdriver, http4k-testing-chaos, JUnit 5

---

### Task 1: Extract helper methods to WebTest (Layer 1)

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/Stubs.kt`

- [ ] **Step 1: Add `testPasswordHash` to WebTest companion**

In `WebTest.kt`, add after the `encoder` line (line 92):

```kotlin
val testPasswordHash by lazy { encoder.encode("Test@12345678") }
```

This pre-computes a BCrypt hash once per JVM fork instead of per-test. The password "Test@12345678" meets complexity requirements (12 chars, upper, lower, digit, special).

- [ ] **Step 2: Add `createSecurityService()` to WebTest companion**

In `WebTest.kt`, add after `testPasswordHash`:

```kotlin
fun createSecurityService(
    userRepository: UserRepository = this.userRepository,
    sessionRepository: SessionRepository = this.sessionRepository,
): SecurityService =
    SecurityService(
        userRepository,
        encoder,
        sessionRepository = sessionRepository,
        apiKeyRepository = apiKeyRepository,
        resetRepository = passwordResetRepository,
        auditRepository = auditRepository,
    )
```

Note: `SessionRepository` is already imported. The default parameters make the 20+ call sites trivial.

- [ ] **Step 3: Add `withAuthenticatedUser()` to WebTest companion**

```kotlin
fun withAuthenticatedUser(
    username: String = "testuser_" + java.util.UUID.randomUUID().toString().take(8),
    password: String = "Test@12345678",
    passwordHash: String = testPasswordHash,
    role: String = "USER",
): Triple<String, String, String> {
    val userId = java.util.UUID.randomUUID().toString()
    testJdbi.useHandle<Exception> { handle ->
        handle.execute(
            "INSERT INTO users (id, username, password_hash, role, enabled, avatar_url) VALUES (?, ?, ?, ?::user_role, true, NULL)",
            java.util.UUID.fromString(userId), username, passwordHash, role,
        )
    }
    val token = java.util.UUID.randomUUID().toString()
    val tokenHash = io.github.rygel.outerstellar.platform.security.TokenHashing.hash(token)
    val expiresAt = java.time.Instant.now().plusSeconds(3600)
    sessionRepository.save(
        io.github.rygel.outerstellar.platform.model.Session(
            id = 1L,
            userId = java.util.UUID.fromString(userId),
            tokenHash = tokenHash,
            createdAt = java.time.Instant.now(),
            expiresAt = expiresAt,
            role = role,
        ),
    )
    return Triple(token, userId, username)
}
```

Returns `(sessionToken, userId, username)`. The token is the raw cookie value — use with `sessionCookie()` helpers that already exist in test files.

- [ ] **Step 4: Add `formPost()` helper to Stubs.kt**

In `Stubs.kt`, add at the end:

```kotlin
fun formPost(url: String, vararg pairs: Pair<String, String>): org.http4k.core.Request =
    org.http4k.core.Request(org.http4k.core.Method.POST, url)
        .header("content-type", "application/x-www-form-urlencoded")
        .body(formBody(*pairs))

fun formBody(vararg pairs: Pair<String, String>): String =
    java.net.URLEncoder.encode(pairs.joinToString("&") { "${it.first}=${it.second}" }, "UTF-8")
```

Wait — check if `formBody` already exists. If it does (grep for `fun formBody`), skip this step and only add `formPost`.

- [ ] **Step 5: Compile**

Run: `mvn -pl platform-web -am compile test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 6: Run full test suite**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass (602+)

- [ ] **Step 7: Commit**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/Stubs.kt
git commit -m "refactor(test): extract helpers to WebTest — testPasswordHash, createSecurityService, withAuthenticatedUser, formPost"
```

---

### Task 2: Replace `lateinit var app` with `by lazy` across test classes (Layer 1)

**Files:**
- Modify: ~30 test files in `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/`

**IMPORTANT:** This is a bulk mechanical refactor. For each test class:

1. If the class has `private lateinit var app: HttpHandler` and sets it in `@BeforeEach` with just `app = buildApp()`:
   - Replace with `private val app by lazy { buildApp() }`
   - Remove the `@BeforeEach` function entirely (unless it does other setup too)
   - Remove the `import org.junit.jupiter.api.BeforeEach` if no longer needed

2. If the class sets `app = buildApp(securityService = ...)` or passes custom config per test — keep `lateinit var` + `@BeforeEach`. These need per-test app construction.

**Classes that need per-test app (keep lateinit):**
- `AuthHtmlFlowIntegrationTest` — creates custom SecurityService per test
- `SessionSecurityIntegrationTest` — creates users and sessions in @BeforeEach
- `CsrfProtectionIntegrationTest` — needs csrf enabled
- Any test that passes `TestOverrides` to `buildApp`

**Classes to convert (safe to use `by lazy`):**
- `HealthCheckIntegrationTest`
- `ErrorPagesIntegrationTest`
- `StaticAssetIntegrationTest`
- `SecurityHeadersIntegrationTest`
- `MdcLoggingIntegrationTest`
- `DevDashboardAccessIntegrationTest`
- `DarkModeToggleIntegrationTest`
- `ComponentFragmentIntegrationTest`
- `UnauthenticatedRouteAccessTest`
- `AdminSectionTest`
- And similar classes that just call `buildApp()` with no arguments

- [ ] **Step 1: Convert HealthCheckIntegrationTest as proof**

Replace:
```kotlin
private lateinit var app: HttpHandler

@BeforeEach
fun setupTest() {
    app = buildApp()
}
```

With:
```kotlin
private val app by lazy { buildApp() }
```

Remove `import org.junit.jupiter.api.BeforeEach`.

- [ ] **Step 2: Compile and test the one file**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true" "-Dtest=HealthCheckIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: All 8 tests pass

- [ ] **Step 3: Convert remaining safe test classes**

Apply the same pattern to all classes identified above. Use grep to find them:
```
grep -rl "private lateinit var app" platform-web/src/test/kotlin/
```

For each match, check if `@BeforeEach` does ONLY `app = buildApp()`. If yes, convert.

- [ ] **Step 4: Compile and test**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -u platform-web/src/test/kotlin/
git commit -m "refactor(test): replace lateinit var app with by lazy in simple test classes"
```

---

### Task 3: Use cached `testPasswordHash` across test classes (Layer 1)

**Files:**
- Modify: ~15 test files that call `encoder.encode(testPassword())`

- [ ] **Step 1: Find all files that hash passwords at test time**

Search for `encoder.encode` in test files. Each occurrence should be replaced with `testPasswordHash` (or `WebTest.testPasswordHash` if called from outside WebTest companion scope).

The pattern to replace:
```kotlin
val passwordHash = encoder.encode(testPassword())
```
→
```kotlin
val passwordHash = testPasswordHash
```

And for tests that use a specific password:
```kotlin
val passwordHash = encoder.encode("SomeSpecificPassword1!")
```
→ Keep as-is if unique, or use `testPasswordHash` if the exact password doesn't matter.

- [ ] **Step 2: Convert all instances**

Replace each `encoder.encode(testPassword())` with `testPasswordHash`.

- [ ] **Step 3: Compile and test**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass, ~3s faster per test class that used BCrypt

- [ ] **Step 4: Commit**

```bash
git add -u platform-web/src/test/kotlin/
git commit -m "perf(test): cache BCrypt hash in WebTest companion, eliminate per-test hashing"
```

---

### Task 4: Replace SecurityService boilerplate with `createSecurityService()` (Layer 1)

**Files:**
- Modify: ~20 test files that construct `SecurityService(...)` inline

- [ ] **Step 1: Find all inline SecurityService constructions**

Search for `SecurityService(` in test files. Replace:

```kotlin
SecurityService(
    userRepository,
    encoder,
    sessionRepository = sessionRepository,
    apiKeyRepository = apiKeyRepository,
    resetRepository = passwordResetRepository,
    auditRepository = auditRepository,
)
```

With:
```kotlin
createSecurityService()
```

If custom parameters are passed (e.g. different userRepository), use:
```kotlin
createSecurityService(userRepository = myCustomRepo)
```

- [ ] **Step 2: Also replace in `buildApp()` default parameter**

In `WebTest.kt`, the `buildApp()` method already uses the SecurityService constructor as a default parameter. Change it to:

```kotlin
fun buildApp(
    config: AppConfig = testConfig,
    securityService: SecurityService = createSecurityService(),
    overrides: TestOverrides = TestOverrides(),
): HttpHandler {
```

This makes the default explicit and consistent.

- [ ] **Step 3: Compile and test**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add -u platform-web/src/test/kotlin/
git commit -m "refactor(test): replace SecurityService boilerplate with createSecurityService()"
```

---

### Task 5: Add http4k-testing-hamkrest dependency and create custom matchers (Layer 2)

**Files:**
- Modify: `platform-web/pom.xml`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/HttpMatchers.kt`

- [ ] **Step 1: Add hamkrest dependency to pom.xml**

In `platform-web/pom.xml`, add in the `<dependencies>` section (test scope, near the other http4k deps):

```xml
<dependency>
    <groupId>org.http4k</groupId>
    <artifactId>http4k-testing-hamkrest</artifactId>
    <scope>test</scope>
</dependency>
```

The version is inherited from `http4k.version` property in the parent pom's `<dependencyManagement>`.

- [ ] **Step 2: Create HttpMatchers.kt**

Create `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/HttpMatchers.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.testing.hasBody
import org.http4k.testing.hasHeader
import org.http4k.testing.hasStatus

fun hasOkStatus(): Matcher<Response> = hasStatus(Status.OK)

fun hasStatusFound(): Matcher<Response> = hasStatus(Status.FOUND)

fun hasStatusForbidden(): Matcher<Response> = hasStatus(Status.FORBIDDEN)

fun hasStatusNotFound(): Matcher<Response> = hasStatus(Status.NOT_FOUND)

fun hasStatusUnauthorized(): Matcher<Response> = hasStatus(Status.UNAUTHORIZED)

fun bodyContains(text: String): Matcher<Response> = hasBody(com.natpryce.hamkrest.containsSubstring(text))

fun bodyContains(regex: Regex): Matcher<Response> = hasBody(com.natpryce.hamkrest.matches(regex))

fun hasContentType(type: String): Matcher<Response> = hasHeader("Content-Type", com.natpryce.hamkrest.containsSubstring(type))

fun hasLocation(path: String): Matcher<Response> = hasHeader("Location", com.natpryce.hamkrest.containsSubstring(path))

fun hasRedirect(path: String): Matcher<Response> = hasStatusFound().and(hasLocation(path))

fun hasSessionCookie(): Matcher<Response> = hasHeader("Set-Cookie", com.natpryce.hamkrest.containsSubstring("session="))
```

Note: Check the actual http4k-testing-hamkrest API — `hasStatus`, `hasBody`, `hasHeader` may be top-level functions or extension functions. Read the source jar or compiled API to confirm the exact import paths. The key imports are from `org.http4k.testing.*`.

- [ ] **Step 3: Compile**

Run: `mvn -pl platform-web -am compile test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

If `hasBody`/`hasHeader`/`hasStatus` import paths are wrong, check the actual package by running:
```bash
jar tf ~/.m2/repository/org/http4k/http4k-testing-hamkrest/6.47.0.0/http4k-testing-hamkrest-6.47.0.0.jar | grep -i "match"
```

- [ ] **Step 4: Run existing tests to confirm no breakage**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add platform-web/pom.xml platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/HttpMatchers.kt
git commit -m "feat(test): add http4k-testing-hamkrest and custom HttpMatchers utility"
```

---

### Task 6: Convert HealthCheckIntegrationTest to hamkrest (Layer 2)

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/HealthCheckIntegrationTest.kt`

This is the proof-of-concept conversion. Simple test class, no auth, no forms.

- [ ] **Step 1: Rewrite HealthCheckIntegrationTest using hamkrest**

Replace the entire test class body. The assertions change from:
```kotlin
assertEquals(Status.OK, response.status)
assertTrue(body.contains("UP"))
```

To:
```kotlin
assertThat(response, hasOkStatus())
assertThat(response, bodyContains("UP"))
```

Or composed:
```kotlin
assertThat(response, hasOkStatus().and(bodyContains("UP")))
```

Keep the same test method names and coverage. Every test method should now use `assertThat` with hamkrest matchers.

- [ ] **Step 2: Compile and test**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true" "-Dtest=HealthCheckIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: All 8 tests pass

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/HealthCheckIntegrationTest.kt
git commit -m "refactor(test): convert HealthCheckIntegrationTest to hamkrest matchers"
```

---

### Task 7: Convert remaining test classes to hamkrest (Layer 2, bulk)

**Files:**
- Modify: ~25 test files in `platform-web/src/test/kotlin/`

- [ ] **Step 1: Convert test classes in batches**

Convert 5-8 test classes at a time. For each:
1. Replace `import kotlin.test.*` with hamkrest imports + HttpMatchers
2. Replace `assertEquals(Status.XXX, response.status)` → `assertThat(response, hasStatus(Status.XXX))`
3. Replace `assertTrue(body.contains("text"))` → `assertThat(response, bodyContains("text"))`
4. Replace OR-chained contains with individual matchers or composed matchers
5. Replace `assertEquals("value", response.header("name"))` → `assertThat(response, hasHeader("name", "value"))`

After each batch, compile and test.

**Batch order (easiest first):**
1. ErrorPagesIntegrationTest, StaticAssetIntegrationTest, SecurityHeadersIntegrationTest
2. ContactsPaginationIntegrationTest, ComponentFragmentIntegrationTest
3. RateLimiterIntegrationTest, CsrfProtectionIntegrationTest
4. AuthHtmlFlowIntegrationTest, SessionSecurityIntegrationTest
5. Remaining test classes

- [ ] **Step 2: Full test suite after all conversions**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add -u platform-web/src/test/kotlin/
git commit -m "refactor(test): convert remaining test classes to hamkrest matchers"
```

---

### Task 8: Add http4k-testing-approval and create approval tests (Layer 3)

**Files:**
- Modify: `platform-web/pom.xml`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/approval/ErrorPagesApprovalTest.kt`
- Create: `platform-web/src/test/resources/approval/ErrorPagesApprovalTest/` (golden files generated on first run)

- [ ] **Step 1: Add approval dependency to pom.xml**

```xml
<dependency>
    <groupId>org.http4k</groupId>
    <artifactId>http4k-testing-approval</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create ErrorPagesApprovalTest**

```kotlin
package io.github.rygel.outerstellar.platform.web.approval

import io.github.rygel.outerstellar.platform.web.WebTest
import io.github.rygel.outerstellar.platform.web.buildApp
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.http4k.testing.assertApproved
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApprovalTest::class)
class ErrorPagesApprovalTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `not-found error page`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/errors/not-found")))
    }

    @Test
    fun `server-error page`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/errors/server-error")))
    }

    @Test
    fun `forbidden page`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/errors/forbidden")))
    }

    @Test
    fun `404 unknown path`(approver: Approver) {
        approver.assertApproved(app(Request(GET, "/nonexistent-page-xyzabc")))
    }
}
```

Note: Check the actual `ApprovalTest` and `Approver` API from http4k-testing-approval. The `assertApproved` extension function might have a different signature. Read the jar or javadoc if compilation fails.

- [ ] **Step 3: Run the test to generate `.actual` files**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true" "-Dtest=ErrorPagesApprovalTest" "-Dsurefire.failIfNoSpecifiedTests=false"`

This will FAIL because no `.approved` files exist yet. The test writes `.actual` files.

- [ ] **Step 4: Accept the golden files**

Copy `.actual` files to `.approved`:
```bash
Get-ChildItem -Recurse -Filter "*.actual" -Path "platform-web\src\test\resources" | ForEach-Object {
    Copy-Item $_.FullName ($_.FullName -replace '\.actual$', '.approved')
}
```

If the `.actual` files ended up in `target/` instead, check the approval test configuration for output directory and adjust.

- [ ] **Step 5: Run tests again to confirm they pass**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true" "-Dtest=ErrorPagesApprovalTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: All approval tests pass

- [ ] **Step 6: Commit**

```bash
git add platform-web/pom.xml platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/approval/ platform-web/src/test/resources/approval/
git commit -m "feat(test): add approval testing for error page templates"
```

---

### Task 9: Add WebDriver and Chaos dependencies, create proof-of-concept tests (Layer 4)

**Files:**
- Modify: `platform-web/pom.xml`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/webdriver/AuthFlowWebDriverTest.kt`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/chaos/ChaosErrorPageTest.kt`

- [ ] **Step 1: Add webdriver and chaos dependencies to pom.xml**

```xml
<dependency>
    <groupId>org.http4k</groupId>
    <artifactId>http4k-testing-webdriver</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.http4k</groupId>
    <artifactId>http4k-testing-chaos</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Create AuthFlowWebDriverTest**

```kotlin
package io.github.rygel.outerstellar.platform.web.webdriver

import io.github.rygel.outerstellar.platform.web.WebTest
import io.github.rygel.outerstellar.platform.web.buildApp
import io.github.rygel.outerstellar.platform.web.createSecurityService
import io.github.rygel.outerstellar.platform.web.testPasswordHash
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.testing.webdriver.Http4kWebDriver

class AuthFlowWebDriverTest : WebTest() {

    private val securityService by lazy { createSecurityService() }
    private val app by lazy { buildApp(securityService = securityService) }
    private val driver by lazy { Http4kWebDriver(app) }

    @Test
    fun `login page has username and password fields`() {
        driver.navigate().to("http://test/auth/sign-in")
        val pageSource = driver.pageSource
        assertTrue(pageSource.contains("username") || pageSource.contains("Username"), "Should have username field")
        assertTrue(pageSource.contains("password") || pageSource.contains("Password"), "Should have password field")
    }

    @Test
    fun `health page renders correctly via webdriver`() {
        driver.navigate().to("http://test/health")
        assertTrue(driver.pageSource.isNotEmpty())
    }
}
```

Note: Check `Http4kWebDriver` constructor — it takes an `HttpHandler`. The `navigate().to()` needs a full URL but it's resolved in-memory.

- [ ] **Step 3: Create ChaosErrorPageTest**

```kotlin
package io.github.rygel.outerstellar.platform.web.chaos

import io.github.rygel.outerstellar.platform.web.WebTest
import io.github.rygel.outerstellar.platform.web.buildApp
import io.github.rygel.outerstellar.platform.web.bodyContains
import io.github.rygel.outerstellar.platform.web.hasOkStatus
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.testing.chaos.ChaaoticResponse
import org.http4k.testing.chaos.ChaosEngine
import org.http4k.testing.chaos.ChaosStages
import org.http4k.testing.chaos.Triggers
import org.http4k.testing.chaos.Behaviour

class ChaosErrorPageTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `app handles internal server errors gracefully`() {
        val chaoticApp = ChaosEngine()
            .withChaos(ChaosStages(Triggers.Always, Behaviour.ReturnStatus(org.http4k.core.Status.INTERNAL_SERVER_ERROR)))
            .then(app)
        val response = chaoticApp(Request(GET, "/health"))
        assertEquals(org.http4k.core.Status.INTERNAL_SERVER_ERROR, response.status)
    }
}
```

Note: The Chaos API may differ — check `http4k-testing-chaos` 6.47.0.0 actual classes. The key types are `ChaosEngine`, `ChaosStage`, `Trigger`, `Behaviour`. Adapt the imports and API calls to match the actual module.

- [ ] **Step 4: Compile**

Run: `mvn -pl platform-web -am compile test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

May need to adjust imports/APIs based on the actual http4k-testing-chaos and http4k-testing-webdriver APIs.

- [ ] **Step 5: Run tests**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true" "-Dtest=AuthFlowWebDriverTest,ChaosErrorPageTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: Tests pass

- [ ] **Step 6: Full test suite**

Run: `mvn -pl platform-web -am test "-Dexec.skip=true"`
Expected: All tests pass (602+ original + new webdriver/chaos tests)

- [ ] **Step 7: Commit**

```bash
git add platform-web/pom.xml platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/webdriver/ platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/chaos/
git commit -m "feat(test): add WebDriver and Chaos proof-of-concept tests"
```

---

### Task 10: Update docs/testing.md and AGENTS.md (Documentation)

**Files:**
- Modify: `docs/testing.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Add http4k testing module section to docs/testing.md**

Add a section after the existing test patterns describing:
- hamkrest matchers and HttpMatchers utility
- Approval testing workflow (generate → review → accept golden files)
- WebDriver for HTML navigation testing
- Chaos for resilience testing

- [ ] **Step 2: Update AGENTS.md with http4k testing conventions**

Add to the testing section:
- All new tests should use hamkrest matchers (not raw `assertEquals` for status/headers)
- Approval tests live in `src/test/kotlin/.../approval/`
- Golden files live in `src/test/resources/approval/`

- [ ] **Step 3: Commit**

```bash
git add docs/testing.md AGENTS.md
git commit -m "docs: document http4k testing modules and conventions"
```

---

### Task 11: Full reactor verification and push

- [ ] **Step 1: Full reactor build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`

Expected: BUILD SUCCESS, all tests pass, all quality checks pass

- [ ] **Step 2: Push to PR**

```bash
git push -u origin refactor/http4k-testing-overhaul
gh pr create --base develop --title "feat(test): http4k testing overhaul — waste elimination, hamkrest, approval, webdriver, chaos"
```
