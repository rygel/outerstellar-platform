# Security Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the top 5 security findings from the security audit, starting with CRITICAL and working through HIGH severity.

**Architecture:** Each fix is isolated to a specific module. Fixes 1 (password reset) and 4 (rate limiter) are standalone patches. Fix 2 (UI route auth) adds authentication checks to web routes. Fix 3 (ownership) requires model changes to add `userId` fields — this is deferred since it's a multi-module schema migration. Fix 5 (export scoping) also depends on ownership, so we add API-level auth checks instead.

**Tech Stack:** Kotlin, http4k, jOOQ, JUnit 5, Testcontainers

---

## Task 1: Remove Plaintext Token Fallback in PasswordResetService

**Severity:** CRITICAL
**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/PasswordResetService.kt:51-56`
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PasswordResetFlowIntegrationTest.kt`

The `resetPassword()` method falls back to looking up the raw plaintext token if the hashed lookup fails. This undermines the entire purpose of hashing tokens.

- [ ] **Step 1: Remove the plaintext fallback line**

In `PasswordResetService.kt`, change lines 51-56 from:

```kotlin
fun resetPassword(token: String, newPassword: String) {
    val repository = resetRepository ?: throw IllegalArgumentException("Invalid reset token")
    val resetToken =
        repository.findByToken(hashToken(token))
            ?: repository.findByToken(token)
            ?: throw IllegalArgumentException("Invalid reset token")
```

to:

```kotlin
fun resetPassword(token: String, newPassword: String) {
    val repository = resetRepository ?: throw IllegalArgumentException("Invalid reset token")
    val resetToken =
        repository.findByToken(hashToken(token))
            ?: throw IllegalArgumentException("Invalid reset token")
```

- [ ] **Step 2: Run existing password reset tests to verify nothing breaks**

Run: `mvn -pl platform-web test -Dtest=PasswordResetFlowIntegrationTest -Dexec.skip=true`
Expected: All tests pass (they already use the hashed path because `requestPasswordReset` stores the hash, and `resetPassword` hashes the input before lookup).

- [ ] **Step 3: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/PasswordResetService.kt
git commit -m "fix(security): remove plaintext token fallback in PasswordResetService"
```

---

## Task 2: Fix Rate Limiter IP Resolution Bypass

**Severity:** HIGH
**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/RateLimiter.kt:83-96`
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/RateLimiterIntegrationTest.kt`

When `trustedProxies` is empty and `request.source?.address` is null, the code trusts `X-Forwarded-For` and `X-Real-IP` headers from the client. An attacker can spoof these to bypass rate limiting.

- [ ] **Step 1: Fix the IP resolution logic**

In `RateLimiter.kt`, replace lines 83-96:

```kotlin
val sourceAddress = request.source?.address
val clientIp =
    if (trustedProxies.isNotEmpty() && sourceAddress != null && sourceAddress in trustedProxies) {
        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.header("X-Real-IP")
            ?: sourceAddress
    } else if (sourceAddress != null) {
        sourceAddress
    } else if (trustedProxies.isNotEmpty()) {
        "unknown"
    } else {
        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.header("X-Real-IP")
            ?: "unknown"
    }
```

with:

```kotlin
val sourceAddress = request.source?.address
val clientIp =
    if (trustedProxies.isNotEmpty() && sourceAddress != null && sourceAddress in trustedProxies) {
        request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.header("X-Real-IP")
            ?: sourceAddress
    } else {
        sourceAddress ?: "unknown"
    }
```

This ensures client headers are ONLY trusted when the request comes from a configured trusted proxy. In all other cases, the source address is used directly. If source address is null and no trusted proxy is configured, we use "unknown" — which means all unresolvable requests share one bucket (conservative/blocked), rather than each getting their own bucket (permissive/bypassed).

- [ ] **Step 2: Add test for rate limiter ignoring X-Forwarded-For when no trusted proxies**

In `RateLimiterIntegrationTest.kt`, add a test:

```kotlin
@Test
fun `rate limiter ignores X-Forwarded-For when no trusted proxies configured`() {
    val filter = rateLimitFilter(maxRequests = 2, windowMs = 60_000L, trustedProxies = emptyList())
    val handler = filter.then { Response(Status.OK) }

    for (i in 1..2) {
        val req = Request(Method.POST, "/api/v1/auth/login")
            .header("X-Forwarded-For", "1.2.3.$i")
            .header("content-type", "application/json")
            .body("""{"username":"user$i"}""")
        assertEquals(Status.OK, handler(req).status)
    }

    val spoofedReq = Request(Method.POST, "/api/v1/auth/login")
        .header("X-Forwarded-For", "9.9.9.9")
        .header("content-type", "application/json")
        .body("""{"username":"spoofed"}""")
    assertEquals(Status.TOO_MANY_REQUESTS, handler(spoofedReq).status)
}
```

- [ ] **Step 3: Run rate limiter tests**

Run: `mvn -pl platform-web test -Dtest=RateLimiterIntegrationTest -Dexec.skip=true`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/RateLimiter.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/RateLimiterIntegrationTest.kt
git commit -m "fix(security): rate limiter ignores client IP headers without trusted proxies"
```

---

## Task 3: Add Authentication Checks to UI Routes

**Severity:** HIGH
**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/HomeRoutes.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ContactsRoutes.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SearchRoutes.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ComponentRoutes.kt`
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/MessageActionE2ETest.kt`

The UI routes (HomeRoutes, ContactsRoutes, SearchRoutes, ComponentRoutes) have no authentication checks. Any unauthenticated user can read all messages, contacts, and search results. The pattern already exists in `SettingsRoutes.kt` — check `ctx.user == null` and redirect to `/auth`.

Note: These routes sit inside the global filter chain which resolves the user via session cookie and populates `request.webContext`. If no session cookie exists, `ctx.user` will be `null`.

- [ ] **Step 1: Add auth guard to HomeRoutes**

At the top of each route handler in `HomeRoutes.kt`, add an auth check. The pattern from `SettingsRoutes.kt`:

```kotlin
val ctx = request.webContext
if (ctx.user == null) {
    return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
}
```

Add this to every route handler in `HomeRoutes.kt`:
- Line 46 (`GET /`): add after `val query = ...` block, before `renderer.render(...)`
- Line 58 (`GET /messages/trash`): add at start
- Line 66 (`POST /messages`): add at start
- Line 78 (`POST /messages/restore`): add at start of inner lambda
- Line 89 (`GET /messages/resolve`): add at start of inner lambda
- Line 100 (`POST /messages/resolve`): add at start of inner lambda
- Line 112 (`POST /messages/{syncId}/delete`): add at start of inner lambda
- Line 123 (`GET /messages/{syncId}/edit`): add at start of inner lambda
- Line 133 (`POST /messages/{syncId}/update`): add at start of inner lambda
- Line 146 (`GET /components/footer-status`): this is a component fragment, skip auth (it only shows status text)

For each route, extract `val ctx = request.webContext` at the top and add the null check. For routes inside nested lambdas (path parameter routes), use `request.webContext` directly.

- [ ] **Step 2: Add auth guard to ContactsRoutes**

Same pattern for every route in `ContactsRoutes.kt`. Routes:
- `GET /contacts` (line 35)
- `GET /contacts/new` (line 48)
- `POST /contacts` (line 56) — already has `val ctx = request.webContext`
- `GET /contacts/{syncId}/edit` (line 82)
- `POST /contacts/{syncId}/update` (line 90) — already has `val ctx`
- `POST /contacts/{syncId}/delete` (line 123) — already has `val ctx`
- `POST /contacts/{syncId}/restore` (line 140)
- `GET /contacts/trash/list` (line 151)

- [ ] **Step 3: Add auth guard to SearchRoutes**

For `GET /search` (line 27) and `GET /api/v1/search` (line 41):

For the HTML page route:
```kotlin
val ctx = request.webContext
if (ctx.user == null) {
    return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
}
```

For the JSON API route (under `/api/v1/`):
```kotlin
val ctx = request.webContext
if (ctx.user == null) {
    return@to Response(Status.UNAUTHORIZED)
        .header("content-type", "application/json")
        .body("""{"message":"Authentication required","status":401}""")
}
```

- [ ] **Step 4: Add auth guard to ComponentRoutes**

For `GET /components/message-list` (line 70) — this returns message data, needs auth:
```kotlin
val ctx = request.webContext
if (ctx.user == null) {
    return@to Response(Status.FOUND).header("location", ctx.url("/auth"))
}
```

The other component routes (`/components/navigation/page`, sidebar selectors) are UI chrome, not data — skip auth on those.

- [ ] **Step 5: Add integration test for unauthenticated route access**

Create `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/UnauthenticatedRouteAccessTest.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class UnauthenticatedRouteAccessTest : WebTest() {

    private lateinit var app: org.http4k.core.HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach
    fun teardown() = cleanup()

    private fun assertRedirectsToAuth(request: Request) {
        val response = app(request)
        assertEquals(Status.FOUND, response.status, "Expected redirect to auth for ${request.method} ${request.uri}")
        val location = response.header("location") ?: ""
        assert(location.contains("/auth")) {
            "Expected redirect to /auth, got: $location for ${request.method} ${request.uri}"
        }
    }

    private fun assertUnauthorized(request: Request) {
        val response = app(request)
        assertEquals(Status.UNAUTHORIZED, response.status, "Expected 401 for ${request.method} ${request.uri}")
    }

    @Test
    fun `GET home page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/"))
    }

    @Test
    fun `GET messages trash redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/messages/trash"))
    }

    @Test
    fun `POST create message redirects to auth when not logged in`() {
        assertRedirectsToAuth(
            Request(Method.POST, "/messages")
                .header("content-type", "application/x-www-form-urlencoded")
                .body("author=test&content=test")
        )
    }

    @Test
    fun `GET contacts page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/contacts"))
    }

    @Test
    fun `GET search page redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/search?q=test"))
    }

    @Test
    fun `GET search API returns 401 when not logged in`() {
        assertUnauthorized(Request(Method.GET, "/api/v1/search?q=test"))
    }

    @Test
    fun `GET message list component redirects to auth when not logged in`() {
        assertRedirectsToAuth(Request(Method.GET, "/components/message-list"))
    }
}
```

- [ ] **Step 6: Run all tests**

Run: `mvn -pl platform-web test -Dexec.skip=true`
Expected: All tests pass, including the new `UnauthenticatedRouteAccessTest`.

- [ ] **Step 7: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/HomeRoutes.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ContactsRoutes.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SearchRoutes.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ComponentRoutes.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/UnauthenticatedRouteAccessTest.kt
git commit -m "fix(security): add authentication checks to UI routes"
```

---

## Task 4: Add Defense-in-Depth Admin Checks to SecurityService

**Severity:** HIGH
**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt` (methods `setUserEnabled`, `setUserRole`)
- Test: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`

`setUserEnabled()` and `setUserRole()` don't verify the calling `adminId` belongs to an ADMIN user. Only route-level checks protect these. Add service-layer verification as defense-in-depth.

- [ ] **Step 1: Add admin role verification to setUserEnabled and setUserRole**

In `SecurityService.kt`, find the `setUserEnabled` method and add an admin check at the top:

```kotlin
fun setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean) {
    if (adminId == targetId) {
        throw IllegalArgumentException("Cannot enable/disable your own account")
    }
    val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
    if (admin.role != UserRole.ADMIN) {
        throw InsufficientPermissionException("Only administrators can enable/disable accounts")
    }
    val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
    userRepository.updateEnabled(targetId, enabled)
    audit("USER_${if (enabled) "ENABLED" else "DISABLED"}", actor = admin, target = target)
}
```

Apply the same pattern to `setUserRole`:

```kotlin
fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
    if (adminId == targetId) {
        throw IllegalArgumentException("Cannot change your own role")
    }
    val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
    if (admin.role != UserRole.ADMIN) {
        throw InsufficientPermissionException("Only administrators can change user roles")
    }
    val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
    userRepository.updateRole(targetId, role)
    audit("USER_ROLE_CHANGED", actor = admin, target = target, detail = role.name)
}
```

Note: Check the existing code carefully — `setUserEnabled` may already have the self-check (`adminId == targetId`). Preserve the existing self-check pattern but add the role verification.

- [ ] **Step 2: Add tests for non-admin rejection**

Add tests to the existing `SecurityServiceTest.kt`:

```kotlin
@Test
fun `setUserEnabled rejects non-admin caller`() {
    val user = saveUser(username = "normal", role = UserRole.USER)
    val target = saveUser(username = "target", role = UserRole.USER)
    assertFailsWith<InsufficientPermissionException> {
        service.setUserEnabled(user.id, target.id, false)
    }
}

@Test
fun `setUserRole rejects non-admin caller`() {
    val user = saveUser(username = "normal", role = UserRole.USER)
    val target = saveUser(username = "target", role = UserRole.USER)
    assertFailsWith<InsufficientPermissionException> {
        service.setUserRole(user.id, target.id, UserRole.ADMIN)
    }
}
```

Adjust the test helper method names to match the existing test class conventions (e.g., `saveUser` might be a local helper in the test class).

- [ ] **Step 3: Run security tests**

Run: `mvn -pl platform-security test -Dtest=SecurityServiceTest`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt
git commit -m "fix(security): add admin role verification to SecurityService setUserEnabled and setUserRole"
```

---

## Task 5: Restrict Export Routes to Admin Users

**Severity:** HIGH
**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt:270-277`
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminExportIntegrationTest.kt`

Export routes are in the bearer-authenticated sync contract but accessible to any authenticated user. The export providers dump ALL messages/contacts with no user scoping. Since the data model has no `userId` field on messages/contacts, we restrict export to admin users only.

- [ ] **Step 1: Move export routes to admin contract**

In `App.kt`, move the export route registration from the `syncContract` (lines 270-277) to the `bearerAdminApiContract` (lines 280-285).

Remove from `syncContract`:

```kotlin
val exportProviders =
    listOfNotNull(
        messageService?.let { io.github.rygel.outerstellar.platform.export.MessageExportProvider(it) },
        contactService?.let { io.github.rygel.outerstellar.platform.export.ContactExportProvider(it) },
    )
if (exportProviders.isNotEmpty()) {
    routes += ExportRoutes(exportProviders).routes
}
```

Add to `bearerAdminApiContract`:

```kotlin
val bearerAdminApiContract = contract {
    renderer = OpenApi3(ApiInfo("$appLabel Admin API", "v1.0"), KotlinxSerialization)
    descriptionPath = "/api/v1/admin/api-openapi.json"
    security = bearerAdminSecurity
    routes += UserAdminApi(securityService).routes
    val exportProviders =
        listOfNotNull(
            messageService?.let { io.github.rygel.outerstellar.platform.export.MessageExportProvider(it) },
            contactService?.let { io.github.rygel.outerstellar.platform.export.ContactExportProvider(it) },
        )
    if (exportProviders.isNotEmpty()) {
        routes += ExportRoutes(exportProviders).routes
    }
}
```

Note: The `messageService` and `contactService` variables are already in scope in `buildApiRoutes()`.

Also update the export route paths to be under `/api/v1/admin/export/` instead of `/api/v1/export/`. In `ExportRoutes.kt`, change the path prefix:

```kotlin
"/api/v1/admin/export/${provider.entityType}/csv"
"/api/v1/admin/export/${provider.entityType}/json"
```

- [ ] **Step 2: Update existing export integration test if needed**

Check `AdminExportIntegrationTest.kt` for existing tests that reference the old paths. Update paths from `/api/v1/export/` to `/api/v1/admin/export/` in the test file.

- [ ] **Step 3: Run tests**

Run: `mvn -pl platform-web test -Dtest=AdminExportIntegrationTest -Dexec.skip=true`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ExportRoutes.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminExportIntegrationTest.kt
git commit -m "fix(security): restrict export routes to admin users only"
```

---

## Scope Note: Ownership Checks (Deferred)

Findings #3 (no ownership checks on data operations) and the persistence-layer `JdbiOutboxRepository` enum interpolation require model/schema changes that are too invasive for a security-hotfix pass:

- Adding `userId` / `accountId` to `StoredMessage` and `StoredContact` requires a new Flyway migration, jOOQ regeneration, and changes across all repository implementations, services, and sync protocols.
- This should be planned as a separate multi-module feature with its own migration and backward-compatibility strategy.

These items are tracked in the security backlog but not included in this plan.
