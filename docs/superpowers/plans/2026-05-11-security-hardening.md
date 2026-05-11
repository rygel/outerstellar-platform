# Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the top-priority security hardening items identified in the code audit.

**Architecture:** All changes target existing modules — `platform-security` (session invalidation, audit logging), `platform-web` (cookie flags, CSP, rate limiting). Each task is independent and can be implemented/tested/committed in isolation.

**Tech Stack:** Kotlin, http4k, JUnit 5 + MockK, Maven

---

### Task 1: Invalidate all user sessions on password change

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt:109-123`
- Test: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `SecurityServiceTest`:

```kotlin
@Test
fun `changePassword invalidates all sessions for the user`() {
    val sessionRepository: SessionRepository = mockk(relaxed = true)
    val serviceWithSessions = SecurityService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        auditRepository = auditRepository,
        sessionRepository = sessionRepository,
    )
    every { userRepository.findById(testUser.id) } returns testUser
    every { passwordEncoder.matches("current", testUser.passwordHash) } returns true
    every { passwordEncoder.encode("newpass12") } returns "new_hash"

    serviceWithSessions.changePassword(testUser.id, "current", "newpass12")

    verify { sessionRepository.deleteByUserId(testUser.id) }
}

@Test
fun `changePassword works without session repository`() {
    val serviceNoSessions = SecurityService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        auditRepository = auditRepository,
    )
    every { userRepository.findById(testUser.id) } returns testUser
    every { passwordEncoder.matches("current", testUser.passwordHash) } returns true
    every { passwordEncoder.encode("newpass12") } returns "new_hash"

    serviceNoSessions.changePassword(testUser.id, "current", "newpass12")

    verify { userRepository.save(any()) }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: The first test FAILS — `changePassword` does not call `sessionRepository.deleteByUserId()`.

- [ ] **Step 3: Write minimal implementation**

In `SecurityService.kt`, modify `changePassword()` — add session invalidation after saving the updated user:

```kotlin
fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
    val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())

    if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
        throw WeakPasswordException("Current password is incorrect")
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
        throw WeakPasswordException("New password must be at least $MIN_PASSWORD_LENGTH characters")
    }

    val updated = user.copy(passwordHash = passwordEncoder.encode(newPassword))
    userRepository.save(updated)
    sessionRepository?.deleteByUserId(userId)
    logger.info("Password changed for user {}", user.username)
    audit("PASSWORD_CHANGED", actor = user)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/.../SecurityService.kt platform-security/src/test/.../SecurityServiceTest.kt
git commit -m "feat(security): invalidate all sessions on password change"
```

---

### Task 2: Persist authentication failures to audit table

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt:55-86`
- Test: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `SecurityServiceTest`:

```kotlin
@Test
fun `authenticate logs AUTHENTICATION_FAILED to audit on wrong password`() {
    every { userRepository.findByUsername("testuser") } returns testUser
    every { passwordEncoder.matches("wrongpass", testUser.passwordHash) } returns false
    every { userRepository.incrementFailedLoginAttempts(testUser.id) } returns 1

    service.authenticate("testuser", "wrongpass")

    verify {
        auditRepository.log(match {
            it.action == "AUTHENTICATION_FAILED" &&
                it.targetUsername == "testuser" &&
                it.detail == "Invalid password"
        })
    }
}

@Test
fun `authenticate logs AUTHENTICATION_FAILED to audit on user not found`() {
    every { userRepository.findByUsername("nobody") } returns null

    service.authenticate("nobody", "anypass")

    verify {
        auditRepository.log(match {
            it.action == "AUTHENTICATION_FAILED" &&
                it.detail == "User not found"
        })
    }
}

@Test
fun `authenticate logs AUTHENTICATION_FAILED to audit on disabled user`() {
    val disabledUser = testUser.copy(enabled = false)
    every { userRepository.findByUsername("testuser") } returns disabledUser

    service.authenticate("testuser", "anypass")

    verify {
        auditRepository.log(match {
            it.action == "AUTHENTICATION_FAILED" &&
                it.targetUsername == "testuser" &&
                it.detail == "Account disabled"
        })
    }
}

@Test
fun `authenticate logs AUTHENTICATION_FAILED to audit on locked account`() {
    val lockedUser = testUser.copy(lockedUntil = Instant.now().plusSeconds(300))
    every { userRepository.findByUsername("testuser") } returns lockedUser

    service.authenticate("testuser", "anypass")

    verify {
        auditRepository.log(match {
            it.action == "AUTHENTICATION_FAILED" &&
                it.targetUsername == "testuser" &&
                it.detail!!.startsWith("Account locked")
        })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: Tests FAIL — `authenticate()` does not write to audit repository on failure.

- [ ] **Step 3: Write minimal implementation**

In `SecurityService.kt`, modify `authenticate()` — add `audit()` calls on each failure path:

```kotlin
fun authenticate(username: String, password: String): User? {
    val user =
        userRepository.findByUsername(username)
            ?: run {
                logger.warn("Authentication failed: User $username not found")
                audit("AUTHENTICATION_FAILED", detail = "User not found", targetUsername = username)
                return null
            }
    if (!user.enabled) {
        logger.warn("Authentication failed: User $username is disabled")
        audit("AUTHENTICATION_FAILED", actor = user, detail = "Account disabled")
        return null
    }
    val lockedUntil = user.lockedUntil
    if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
        logger.warn("Authentication failed: User $username is locked until $lockedUntil")
        audit("AUTHENTICATION_FAILED", actor = user, detail = "Account locked until $lockedUntil")
        return null
    }
    if (passwordEncoder.matches(password, user.passwordHash)) {
        if (user.failedLoginAttempts > 0) {
            userRepository.resetFailedLoginAttempts(user.id)
        }
        logger.info("Authentication successful for user $username")
        return user
    }
    val attempts = userRepository.incrementFailedLoginAttempts(user.id)
    logger.warn("Authentication failed: Invalid password for user $username (attempt $attempts)")
    audit("AUTHENTICATION_FAILED", actor = user, detail = "Invalid password")
    if (attempts >= config.maxFailedLoginAttempts) {
        val until = Instant.now().plusSeconds(config.lockoutDurationSeconds)
        userRepository.updateLockedUntil(user.id, until)
        logger.warn("User $username locked until $until after $attempts failed attempts")
    }
    return null
}
```

Also update the `audit()` helper to accept `targetUsername` and `detail` directly:

```kotlin
private fun audit(
    action: String,
    actor: User? = null,
    target: User? = null,
    detail: String? = null,
    targetUsername: String? = null,
) {
    auditRepository?.log(
        AuditEntry(
            actorId = actor?.id?.toString(),
            actorUsername = actor?.username,
            targetId = target?.id?.toString(),
            targetUsername = targetUsername ?: target?.username,
            action = action,
            detail = detail,
        )
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/.../SecurityService.kt platform-security/src/test/.../SecurityServiceTest.kt
git commit -m "feat(security): persist authentication failures to audit table"
```

---

### Task 3: Add audit logging for API key operations

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/ApiKeyService.kt`
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt:42-44` (wiring)
- Test: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `SecurityServiceTest`:

```kotlin
@Test
fun `createApiKey logs API_KEY_CREATED to audit`() {
    service.createApiKey(testUser.id, "my-key")

    verify {
        auditRepository.log(match {
            it.action == "API_KEY_CREATED" && it.actorId == testUser.id.toString()
        })
    }
}

@Test
fun `deleteApiKey logs API_KEY_DELETED to audit`() {
    service.deleteApiKey(testUser.id, 42L)

    verify {
        auditRepository.log(match {
            it.action == "API_KEY_DELETED" && it.actorId == testUser.id.toString() && it.detail == "keyId=42"
        })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: Tests FAIL — `createApiKey` and `deleteApiKey` do not call `audit()`.

- [ ] **Step 3: Write minimal implementation**

In `SecurityService.kt`, wrap the delegated calls to add audit logging:

```kotlin
fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse {
    val result = apiKeyService.createApiKey(userId, name)
    val user = userRepository.findById(userId)
    audit("API_KEY_CREATED", actor = user, detail = "name=$name")
    return result
}

fun deleteApiKey(userId: UUID, keyId: Long) {
    apiKeyService.deleteApiKey(userId, keyId)
    val user = userRepository.findById(userId)
    audit("API_KEY_DELETED", actor = user, detail = "keyId=$keyId")
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-security test -Dtest="SecurityServiceTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/.../SecurityService.kt platform-security/src/test/.../SecurityServiceTest.kt
git commit -m "feat(security): add audit logging for API key operations"
```

---

### Task 4: Add SameSite/Secure flags to preference cookies

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:280-307`
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SessionSecurityIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `SessionSecurityIntegrationTest` (or a new test class if preferred):

```kotlin
@Test
fun `preference cookies have SameSite and Secure flags`() {
    val response = app(Request(GET, "/").cookie(sessionFor(regularUser)).query("theme", "dark"))

    val themeCookie = response.cookies().find { it.name == WebContext.THEME_COOKIE }
    assertNotNull(themeCookie)
    assertEquals(SameSite.Strict, themeCookie.sameSite)
    assertTrue(themeCookie.secure)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-web test -Dtest="SessionSecurityIntegrationTest" -q`
Expected: Test FAILS — preference cookies have no `SameSite` or `Secure` flags.

- [ ] **Step 3: Write minimal implementation**

In `Filters.kt`, update the four preference cookie constructors in `stateFilter()` to include `sameSite` and `secure`:

```kotlin
val langCookie =
    request
        .query("lang")
        ?.takeIf { it in setOf("en", "fr") }
        ?.let {
            Cookie(WebContext.LANG_COOKIE, it, maxAge = cookieMaxAge, path = "/", sameSite = SameSite.Strict, secure = true)
        }
val themeCookie =
    request
        .query("theme")
        ?.takeIf { v -> ThemeCatalog.isValidTheme(v) }
        ?.let {
            Cookie(WebContext.THEME_COOKIE, it, maxAge = cookieMaxAge, path = "/", sameSite = SameSite.Strict, secure = true)
        }
val layoutCookie =
    request
        .query("layout")
        ?.takeIf { it in setOf("nice", "cozy", "compact") }
        ?.let {
            Cookie(WebContext.LAYOUT_COOKIE, it, maxAge = cookieMaxAge, path = "/", sameSite = SameSite.Strict, secure = true)
        }
val shellCookie =
    request
        .query("shell")
        ?.takeIf { it in setOf("sidebar", "topbar") }
        ?.let {
            Cookie(WebContext.SHELL_COOKIE, it, maxAge = cookieMaxAge, path = "/", sameSite = SameSite.Strict, secure = true)
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-web test -Dtest="SessionSecurityIntegrationTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/.../Filters.kt platform-web/src/test/.../SessionSecurityIntegrationTest.kt
git commit -m "fix(security): add SameSite=Strict and Secure flags to preference cookies"
```

---

### Task 5: Harden CSP — add base-uri, form-action, remove ws: in production

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:41-47`
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt` (if CSP is configurable)
- Test: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SessionSecurityIntegrationTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `security headers include base-uri and form-action in CSP`() {
    val response = app(Request(GET, "/").cookie(sessionFor(regularUser)))

    val csp = response.header("Content-Security-Policy")
    assertNotNull(csp)
    assertTrue(csp.contains("base-uri 'self'"), "CSP should contain base-uri 'self'")
    assertTrue(csp.contains("form-action 'self'"), "CSP should contain form-action 'self'")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-web test -Dtest="SessionSecurityIntegrationTest" -q`
Expected: Test FAILS — CSP does not contain `base-uri` or `form-action`.

- [ ] **Step 3: Write minimal implementation**

In `Filters.kt`, update the `DEFAULT_CSP_POLICY` constant:

```kotlin
private const val DEFAULT_CSP_POLICY =
    "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "font-src 'self'; " +
        "connect-src 'self' wss:; " +
        "img-src 'self' data:; " +
        "base-uri 'self'; " +
        "form-action 'self'"
```

Key changes:
- Added `base-uri 'self'` — prevents base-tag hijacking
- Added `form-action 'self'` — prevents form submission to external sites
- Removed `ws:` from `connect-src` — unencrypted WebSocket no longer allowed

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-web test -Dtest="SessionSecurityIntegrationTest" -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/.../Filters.kt platform-web/src/test/.../SessionSecurityIntegrationTest.kt
git commit -m "fix(security): harden CSP with base-uri, form-action, remove ws:"
```

---

### Task 6: Full build verification

- [ ] **Step 1: Run full reactor build (excluding desktop)**

Run: `mvn clean verify -T4 -pl "!platform-desktop,!platform-desktop-javafx"`
Expected: BUILD SUCCESS

- [ ] **Step 2: Fix any failures before proceeding**

If any tests fail, fix them and re-run before committing.
