# Session Cookie Opacity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace raw UUID session cookies with opaque session tokens, unifying web UI and API auth through the `plt_sessions` table.

**Architecture:** The web UI currently stores the user's UUID directly in `app_session`. After this change, login creates an opaque `oss_...` token via `SecurityService.createSession()`, stores it in the cookie, and `WebContext` resolves it via `SecurityService.lookupSession()`. Logout deletes the session from the database before clearing the cookie. No schema changes needed — `plt_sessions` already exists.

**Tech Stack:** Kotlin, http4k, jOOQ, Testcontainers (for integration tests)

---

### Task 1: Add `deleteSession()` to SecurityService

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt:287-289`

- [ ] **Step 1: Add the method**

Insert after `deleteExpiredSessions()` (line 289):

```kotlin
fun deleteSession(rawToken: String) {
    val repo = sessionRepository ?: return
    repo.deleteByTokenHash(hashToken(rawToken))
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -pl platform-security compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt
git commit -m "feat(security): add deleteSession method for server-side session revocation"
```

---

### Task 2: Rename `SessionCookie.create` parameter

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SessionCookie.kt:4`

- [ ] **Step 1: Rename parameter from `userId` to `value`**

Change line 4 from:
```kotlin
fun create(userId: String, secure: Boolean, maxAgeSeconds: Long? = null): String {
```
to:
```kotlin
fun create(value: String, secure: Boolean, maxAgeSeconds: Long? = null): String {
```

Change line 7 from:
```kotlin
return "${WebContext.SESSION_COOKIE}=$userId; Path=/; HttpOnly; SameSite=Strict$securePart$maxAgePart"
```
to:
```kotlin
return "${WebContext.SESSION_COOKIE}=$value; Path=/; HttpOnly; SameSite=Strict$securePart$maxAgePart"
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SessionCookie.kt
git commit -m "refactor(web): rename SessionCookie.create parameter for generic token values"
```

---

### Task 3: Thread SecurityService into WebContext and stateFilter

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt:1-91`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:240-296`

- [ ] **Step 1: Update WebContext constructor and user resolution**

In `WebContext.kt`, add imports at the top:
```kotlin
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.SessionLookup
```

Change the constructor (lines 18-25) to add `securityService`:
```kotlin
class WebContext(
    val request: Request,
    private val devDashboardEnabled: Boolean = false,
    private val userRepository: UserRepository? = null,
    private val appVersion: String = "dev",
    private val jwtService: JwtService? = null,
    private val securityService: SecurityService? = null,
    private val pluginOptions: PluginOptions = PluginOptions(),
)
```

Replace the `user` property (lines 74-91) with:
```kotlin
val user: User? by lazy {
    request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
        when (val lookup = securityService?.lookupSession(rawToken)) {
            is SessionLookup.Active -> lookup.user
            SessionLookup.Expired -> null
            SessionLookup.NotFound -> null
            null -> null
        }
    }
        ?: request.cookie(JWT_COOKIE)?.value?.let { token ->
            jwtService?.extractClaims(token)?.let { (userId, _) ->
                userRepository?.findById(userId)?.takeIf { it.enabled }
            }
        }
}
```

Remove the now-unused `java.util.UUID` import if no other code uses it.

- [ ] **Step 2: Update stateFilter to accept and pass SecurityService**

In `Filters.kt`, add import:
```kotlin
import io.github.rygel.outerstellar.platform.security.SecurityService
```

Change `stateFilter` signature (lines 240-245) to:
```kotlin
fun stateFilter(
    devDashboardEnabled: Boolean,
    userRepository: UserRepository,
    appVersion: String = "dev",
    jwtService: io.github.rygel.outerstellar.platform.security.JwtService? = null,
    securityService: SecurityService? = null,
    pluginOptions: PluginOptions = PluginOptions(),
): Filter = Filter { next: HttpHandler ->
```

Change the `WebContext(...)` constructor call inside (line 249) to:
```kotlin
val context =
    WebContext(request, devDashboardEnabled, userRepository, appVersion, jwtService, securityService, pluginOptions)
```

- [ ] **Step 3: Update stateFilter call in App.kt**

In `App.kt`, the `buildFilterChain` method calls `Filters.stateFilter(...)` at around line 517. Add `securityService` to the call:

```kotlin
.then(
    Filters.stateFilter(
        config.devDashboardEnabled,
        userRepository,
        config.version,
        jwtService,
        securityService,
        PluginOptions(
            navItems = plugin?.navItems ?: emptyList(),
            textResolver = plugin?.textResolver,
            adminNavItems = adminNavItems,
        ),
    )
)
```

- [ ] **Step 4: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "feat(web): resolve session cookies via SecurityService instead of raw UUID"
```

---

### Task 4: Change password login to create opaque session token

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt:68-76`

- [ ] **Step 1: Replace UUID cookie with session token**

In `AuthRoutes.kt`, the sign-in success block (lines 68-76) currently does:
```kotlin
Response(Status.FOUND)
    .header("location", ctx.url(returnTo))
    .header("Set-Cookie", SessionCookie.create(user.id.toString(), sessionCookieSecure))
```

Change to:
```kotlin
val sessionToken = securityService.createSession(user.id)
Response(Status.FOUND)
    .header("location", ctx.url(returnTo))
    .header("Set-Cookie", SessionCookie.create(sessionToken, sessionCookieSecure))
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt
git commit -m "feat(auth): create opaque session token on password login"
```

---

### Task 5: Change OAuth callback to create opaque session token

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/OAuthRoutes.kt:128-145`

- [ ] **Step 1: Replace UUID cookie with session token and consistent flags**

The `handleCallback` success block (lines 132-145) currently sets `user.id.toString()` directly via an http4k `Cookie`. Replace the entire `try` block (lines 128-149) with:

```kotlin
return try {
    val userInfo = provider.exchangeCode(validated.code, validated.state, redirectUri)
    val user = securityService.findOrCreateOAuthUser(providerName, userInfo.subject, userInfo.email)
    val sessionToken = securityService.createSession(user.id)

    logger.info("OAuth sign-in successful: user={} provider={}", user.username, providerName)
    Response(Status.FOUND)
        .header("location", "/")
        .header(
            "Set-Cookie",
            SessionCookie.create(sessionToken, sessionCookieSecure),
        )
        .cookie(Cookie("oauth_state", "", maxAge = 0L, path = "/"))
} catch (e: OAuthException) {
    logger.warn("OAuth callback error for provider={}: {}", providerName, e.message)
    Response(Status.FOUND).header("location", "/auth?oauth_error=true")
}
```

This also fixes the flag inconsistency — the manual `Cookie()` was missing `SameSite=Strict`.

- [ ] **Step 2: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/OAuthRoutes.kt
git commit -m "feat(oauth): create opaque session token on OAuth callback"
```

---

### Task 6: Change logout to revoke server-side session

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt:335-342`

- [ ] **Step 1: Add session deletion to logout handler**

In `App.kt`, the logout route (lines 335-342) currently just clears the cookie. Replace it with:

```kotlin
routes +=
    ("/logout" bindContract POST).to { request: org.http4k.core.Request ->
        val rawToken = request.cookie(io.github.rygel.outerstellar.platform.web.WebContext.SESSION_COOKIE)?.value
        if (rawToken != null) {
            securityService.deleteSession(rawToken)
        }
        Response(Status.FOUND)
            .header("location", request.webContext.url("/"))
            .header(
                "Set-Cookie",
                io.github.rygel.outerstellar.platform.web.SessionCookie.clear(config.sessionCookieSecure),
            )
    }
```

Note: `request.cookie(...)` needs the http4k cookie import. Check if it's already imported. The file already uses `org.http4k.core.cookie.cookie` extension somewhere — search for the import.

Actually, looking at the code, the logout handler is inside `buildUiRoutes()` which doesn't have direct access to `securityService`. It does have it as a parameter (line 292). So we can use it directly.

- [ ] **Step 2: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "feat(auth): delete server-side session on logout"
```

---

### Task 7: Update SyncWebSocket to use SecurityService

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SyncWebSocket.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt:88`

- [ ] **Step 1: Rewrite SyncWebSocket**

Replace the entire file content with:

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.SessionLookup
import java.util.concurrent.ConcurrentHashMap
import org.http4k.core.Request
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsHandler
import org.http4k.websocket.WsMessage
import org.http4k.websocket.WsResponse
import org.http4k.websocket.WsStatus
import org.slf4j.LoggerFactory

private const val WS_AUTH_REQUIRED_STATUS = 4401

class SyncWebSocket(private val securityService: SecurityService) :
    io.github.rygel.outerstellar.platform.service.EventPublisher {
    private val logger = LoggerFactory.getLogger(SyncWebSocket::class.java)
    private val connections = ConcurrentHashMap.newKeySet<Websocket>()

    val handler: WsHandler = { request: Request ->
        WsResponse { ws: Websocket ->
            val sessionCookie =
                request
                    .header("Cookie")
                    ?.split(";")
                    ?.map { it.trim() }
                    ?.find { it.startsWith("${WebContext.SESSION_COOKIE}=") }
                    ?.substringAfter("=")
            val user =
                sessionCookie?.let { rawToken ->
                    when (val lookup = securityService.lookupSession(rawToken)) {
                        is SessionLookup.Active -> lookup.user
                        SessionLookup.Expired -> null
                        SessionLookup.NotFound -> null
                    }
                }
            if (user == null) {
                logger.warn("WebSocket connection rejected: no valid session")
                ws.close(WsStatus(WS_AUTH_REQUIRED_STATUS, "Authentication required"))
                return@WsResponse
            }

            connections.add(ws)
            logger.info("WebSocket connection established for user {}. Total: {}", user.username, connections.size)

            ws.onMessage { msg -> logger.debug("Received message: {}", msg.bodyString()) }

            ws.onClose {
                connections.remove(ws)
                logger.info("WebSocket connection closed. Total: {}", connections.size)
            }

            ws.onError { e ->
                logger.error("WebSocket error: {}", e.message)
                connections.remove(ws)
            }
        }
    }

    override fun publishRefresh(targetId: String) {
        val message = WsMessage("refresh:$targetId")
        val failed = mutableListOf<Websocket>()
        connections.forEach { ws ->
            try {
                ws.send(message)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.warn("Failed to send refresh message to websocket: {}", e.message)
                failed += ws
            }
        }
        connections.removeAll(failed.toSet())
    }
}
```

- [ ] **Step 2: Update DI wiring in WebModule.kt**

Change line 88 from:
```kotlin
single { SyncWebSocket(get()) }
```
to:
```kotlin
single { SyncWebSocket(get<SecurityService>()) }
```

- [ ] **Step 3: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SyncWebSocket.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt
git commit -m "refactor(websocket): use SecurityService for session lookup instead of UserRepository"
```

---

### Task 8: Fix devAutoLogin to create opaque session token

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:215-238`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt:515`

- [ ] **Step 1: Update devAutoLogin signature and implementation**

Change the `devAutoLogin` method signature (line 215) from:
```kotlin
fun devAutoLogin(enabled: Boolean, userRepository: UserRepository): Filter = Filter { next ->
```
to:
```kotlin
fun devAutoLogin(
    enabled: Boolean,
    userRepository: UserRepository,
    securityService: SecurityService,
    sessionCookieSecure: Boolean,
): Filter = Filter { next ->
```

Change the body (lines 222-229) from:
```kotlin
val admin = userRepository.findByUsername("admin")
if (admin != null) {
    userRepository.updateLastActivity(admin.id)
    val response = next(request.cookie(Cookie(WebContext.SESSION_COOKIE, admin.id.toString())))
    if (response.cookies().none { it.name == WebContext.SESSION_COOKIE }) {
        response.cookie(Cookie(WebContext.SESSION_COOKIE, admin.id.toString(), path = "/"))
    } else {
        response
    }
```
to:
```kotlin
val admin = userRepository.findByUsername("admin")
if (admin != null) {
    val token = securityService.createSession(admin.id)
    val response = next(request.cookie(Cookie(WebContext.SESSION_COOKIE, token)))
    if (response.cookies().none { it.name == WebContext.SESSION_COOKIE }) {
        response.header("Set-Cookie", SessionCookie.create(token, sessionCookieSecure))
    } else {
        response
    }
```

- [ ] **Step 2: Update the call in App.kt**

In `App.kt` `buildFilterChain()`, change line 515 from:
```kotlin
.then(Filters.devAutoLogin(config.devMode, userRepository))
```
to:
```kotlin
.then(Filters.devAutoLogin(config.devMode, userRepository, securityService, config.sessionCookieSecure))
```

- [ ] **Step 3: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "fix(dev): dev auto-login creates opaque session token"
```

---

### Task 9: Rework sessionTimeout filter

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:298-335`

Now that `WebContext.user` resolves through `SecurityService.lookupSession()` which already checks expiry and extends the session, the `sessionTimeout` filter is largely redundant. An expired session simply produces `null` for `WebContext.user`, which means `SecurityRules.authenticated()` will redirect to `/auth`.

The remaining value of `sessionTimeout` is providing the `?expired=true` redirect parameter so users see "session expired" instead of a generic login page. We can check if the session lookup found an expired token.

- [ ] **Step 1: Add sessionLookup to WebContext**

In `WebContext.kt`, add a new lazy property after `user`:

```kotlin
val sessionExpired: Boolean by lazy {
    request.cookie(SESSION_COOKIE)?.value?.let { rawToken ->
        securityService?.lookupSession(rawToken) is SessionLookup.Expired
    } ?: false
}
```

- [ ] **Step 2: Simplify sessionTimeout filter**

Replace the `sessionTimeout` method (lines 298-335) with:

```kotlin
fun sessionTimeout(
    sessionCookieSecure: Boolean,
): Filter = Filter { next: HttpHandler ->
    { request ->
        val ctx =
            try {
                request.webContext
            } catch (e: IllegalStateException) {
                null
            }

        if (ctx?.sessionExpired == true && ctx.user == null) {
            if (request.uri.path.startsWith("/api/")) {
                Response(Status.UNAUTHORIZED).header("X-Session-Expired", "true").body("Session expired")
            } else {
                Response(Status.FOUND)
                    .header("location", "/auth?expired=true")
                    .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
            }
        } else {
            next(request)
        }
    }
}
```

Note: The old filter took `timeoutMinutes`, `userRepository`, and `activityUpdater` parameters. Those are no longer needed because `SecurityService.lookupSession()` handles expiry checking and activity recording.

- [ ] **Step 3: Update the call in App.kt**

In `App.kt` `buildFilterChain()`, the call at around lines 548-554:

Old:
```kotlin
.then(
    Filters.sessionTimeout(
        config.sessionTimeoutMinutes,
        userRepository,
        config.sessionCookieSecure,
        activityUpdater,
    )
)
```

New:
```kotlin
.then(
    Filters.sessionTimeout(config.sessionCookieSecure)
)
```

- [ ] **Step 4: Verify it compiles**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "refactor(web): simplify sessionTimeout to use SecurityService expiry"
```

---

### Task 10: Update integration tests

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SessionSecurityIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SessionTimeoutIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`

The key change: tests that create session cookies using `user.id.toString()` must now use `securityService.createSession(user.id)` to get an opaque token.

- [ ] **Step 1: Update WebTest.buildApp to pass securityService to app()**

In `WebTest.kt`, the `buildApp` method already has a `securityService` parameter but doesn't always pass it through to the `app()` call when `jwtService` and other params aren't passed. The existing `buildApp` already passes `securityService` to `app()`. Verify this is correct.

- [ ] **Step 2: Update SessionSecurityIntegrationTest**

The `sessionFor(user)` helper at line 85 creates cookies with UUIDs:
```kotlin
private fun sessionFor(user: User) = Cookie(WebContext.SESSION_COOKIE, user.id.toString())
```

This needs to create real sessions. Add a `securityService` field and update:

```kotlin
private lateinit var securityService: SecurityService
private lateinit var sessionTokens: MutableMap<UUID, String>

@BeforeEach
fun setupTest() {
    securityService =
        SecurityService(
            userRepository,
            encoder,
            sessionRepository = sessionRepository,
            apiKeyRepository = apiKeyRepository,
            resetRepository = passwordResetRepository,
            auditRepository = auditRepository,
        )
    sessionTokens = mutableMapOf()

    regularUser = User(...)
    adminUser = User(...)
    disabledUser = User(...)
    userRepository.save(regularUser)
    userRepository.save(adminUser)
    userRepository.save(disabledUser)

    sessionTokens[regularUser.id] = securityService.createSession(regularUser.id)
    sessionTokens[adminUser.id] = securityService.createSession(adminUser.id)

    app = buildApp(securityService = securityService)
}

private fun sessionFor(user: User): Cookie {
    val token = sessionTokens[user.id] ?: securityService.createSession(user.id).also { sessionTokens[user.id] = it }
    return Cookie(WebContext.SESSION_COOKIE, token)
}
```

The "non-UUID session cookie is rejected" test should still pass — any non-`oss_` string will fail `lookupSession()`. Update its assertion to expect redirect (same as before).

The "unknown UUID in session cookie is rejected" test should now use a random opaque string, not a UUID. Change to:
```kotlin
fun `unknown token in session cookie is rejected`() {
    val response =
        app(
            Request(GET, "/auth/change-password")
                .cookie(Cookie(WebContext.SESSION_COOKIE, "oss_" + "a".repeat(48)))
        )
    assertEquals(Status.FOUND, response.status)
    assertTrue(response.header("location").orEmpty().contains("/auth"))
}
```

The "disabled user" test needs the session created BEFORE disabling (or create the session, then disable). Actually, `lookupSession()` checks `user.enabled`, so a session for a disabled user will return `NotFound`. The session should be created before the user is disabled. But the current test saves the user already disabled. Create a session for them first:

Actually, `SecurityService.createSession()` doesn't check if the user is enabled. It just creates a session record. `lookupSession()` checks `user.enabled` on lookup. So:
1. Save the user as enabled
2. Create the session
3. Then disable the user

Update the disabled user setup:
```kotlin
disabledUser = User(
    id = UUID.randomUUID(),
    username = "disableduser",
    email = "disabled@test.com",
    passwordHash = encoder.encode(testPassword()),
    role = UserRole.USER,
    enabled = true,
)
userRepository.save(disabledUser)
sessionTokens[disabledUser.id] = securityService.createSession(disabledUser.id)
userRepository.updateEnabled(disabledUser.id, false)
```

For the logout tests (`POST logout clears the session cookie`), the test sends a UUID cookie. After the change, `logout` reads the cookie token and calls `securityService.deleteSession(rawToken)`. The test should use the opaque token cookie instead, which it will via `sessionFor()`.

- [ ] **Step 3: Update SessionTimeoutIntegrationTest**

The timeout test creates sessions and manipulates `expires_at` and `lastActivityAt`. After the change, `sessionTimeout` no longer checks `lastActivityAt` — it checks `WebContext.sessionExpired` which uses `SecurityService.lookupSession()`. `lookupSession()` checks the `plt_sessions` table for active sessions.

For the expired session cookie test:
- Create a session token, then set `expires_at` to past — this will cause `lookupSession()` to return `Expired` (since `findByTokenHash` only finds active ones, and `findByTokenHashIncludingExpired` finds it)

Update test to use opaque tokens:

```kotlin
@Test
fun `expired session cookie on HTML route redirects to auth with expired param`() {
    val response = app(Request(GET, "/").cookie(Cookie(WebContext.SESSION_COOKIE, expiredToken)))
    assertEquals(Status.FOUND, response.status, "Expired session should cause redirect")
    val location = response.header("location").orEmpty()
    assertTrue(location.contains("expired"), "Redirect location should indicate session expired, got: $location")
}

@Test
fun `active session cookie on HTML route is accepted`() {
    val response = app(Request(GET, "/").cookie(Cookie(WebContext.SESSION_COOKIE, activeToken)))
    assertEquals(Status.OK, response.status, "Active session should succeed")
}
```

The `configureActivityTimestamps()` method can be removed since `sessionTimeout` no longer checks `lastActivityAt`.

- [ ] **Step 4: Run the web tests**

Run: `mvn -pl platform-web test -pl '!platform-web'`
Actually: `mvn -pl platform-web test`
Expected: All tests pass

If tests fail, investigate and fix. Common issues:
- Tests that create `WebContext` directly with UUID cookies
- Tests in other files that use `Cookie(WebContext.SESSION_COOKIE, user.id.toString())`

Search for all occurrences:
```
grep -rn "SESSION_COOKIE, .*.id.toString()" platform-web/src/test/
```

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/test/
git commit -m "test(web): update integration tests for opaque session cookies"
```

---

### Task 11: Run full reactor verification

- [ ] **Step 1: Run full reactor verify (excluding desktop)**

```bash
mvn clean verify -T4 -pl '!platform-desktop,!platform-desktop-javafx'
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Fix any failures**

Investigate and fix any test failures. Check:
- Other test files that use `Cookie(WebContext.SESSION_COOKIE, user.id.toString())`
- OAuth integration tests
- Auth HTML flow tests
- Authentication workflow tests

- [ ] **Step 3: Final commit if needed**

```bash
git add -A
git commit -m "fix: resolve remaining test failures from session cookie migration"
```
