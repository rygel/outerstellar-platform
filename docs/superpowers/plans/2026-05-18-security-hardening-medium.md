# Security Hardening (MEDIUM) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden session management (absolute max lifetime), add registration toggle, and enforce password complexity rules.

**Architecture:** Three independent changes to existing security infrastructure. Session absolute timeout adds a ceiling check in `lookupSession()`. Registration toggle is an `AppConfig` flag checked at route entry. Password complexity adds character-class checks to the existing `validatePassword()` function.

**Tech Stack:** Kotlin, http4k, Koin DI, jOOQ/JDBI, PostgreSQL, JUnit 5

**Spec:** `docs/superpowers/specs/2026-05-18-security-hardening-medium-design.md`

---

## File Structure

| Action | File | Responsibility |
|--------|------|---------------|
| Modify | `platform-core/.../AppConfig.kt` | Add `sessionAbsoluteTimeoutMinutes`, `registrationEnabled` fields |
| Modify | `platform-security/.../Models.kt` | Add `sessionAbsoluteTimeoutSeconds` to `SecurityConfig` |
| Modify | `platform-security/.../SecurityService.kt` | Absolute timeout check in `lookupSession()`, registration guard in `register()` |
| Modify | `platform-security/.../SecurityModule.kt` | Wire new config fields |
| Modify | `platform-security/.../PasswordValidation.kt` | Add complexity rules |
| Modify | `platform-web/.../AuthApi.kt` | Guard registration route with config flag |
| Modify | `platform-web/.../AuthRoutes.kt` | Guard web registration handler with config flag |
| Modify | `platform-security/.../SecurityServiceTest.kt` | Tests for absolute timeout, registration guard |
| Modify | `platform-security/.../PasswordValidationTest.kt` | Tests for new complexity rules |
| Modify | `platform-web/src/test/.../AuthApiTest.kt` or integration test | Tests for 403 on disabled registration |

**No Flyway migration needed** — `plt_sessions.created_at` already exists in the schema (V3) and is already read/written by both `JooqSessionRepository` and `JdbiSessionRepository`.

---

### Task 1: Password Complexity Rules

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/PasswordValidation.kt`
- Modify: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/PasswordValidationTest.kt`

- [ ] **Step 1: Write failing tests for new complexity rules**

Replace the entire test file with tests covering all new rules:

```kotlin
package io.github.rygel.outerstellar.platform.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PasswordValidationTest {

    @Test
    fun `returns null for valid complex password`() {
        assertNull(validatePassword("Abc123!@"))
    }

    @Test
    fun `returns null for exactly min length with all rules`() {
        assertNull(validatePassword("A1b!cdef"))
    }

    @Test
    fun `returns null for exactly max length`() {
        val pw = "A1!" + "a".repeat(125)
        assertNull(validatePassword(pw))
    }

    @Test
    fun `returns error for short password`() {
        val error = validatePassword("Ab1!")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }

    @Test
    fun `returns error for long password`() {
        val pw = "A1!" + "a".repeat(126)
        val error = validatePassword(pw)
        assertNotNull(error)
        assertEquals("Password must be at most 128 characters", error)
    }

    @Test
    fun `returns error when missing uppercase`() {
        val error = validatePassword("abcdefg1!")
        assertNotNull(error)
        assertEquals("Password must contain at least one uppercase letter", error)
    }

    @Test
    fun `returns error when missing lowercase`() {
        val error = validatePassword("ABCDEFG1!")
        assertNotNull(error)
        assertEquals("Password must contain at least one lowercase letter", error)
    }

    @Test
    fun `returns error when missing digit`() {
        val error = validatePassword("Abcdefg!")
        assertNotNull(error)
        assertEquals("Password must contain at least one digit", error)
    }

    @Test
    fun `returns error when missing special character`() {
        val error = validatePassword("Abcdefg1")
        assertNotNull(error)
        assertEquals("Password must contain at least one special character", error)
    }

    @Test
    fun `trims whitespace before validation`() {
        assertNull(validatePassword("  Abc123!@  "))
    }

    @Test
    fun `returns error for password that is short after trimming`() {
        val error = validatePassword("  Ab1  ")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }

    @Test
    fun `returns first failing rule`() {
        val error = validatePassword("abc")
        assertNotNull(error)
        assertEquals("Password must be at least 8 characters", error)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl platform-security test -Dtest=PasswordValidationTest`
Expected: FAIL — existing `validatePassword()` does not check character classes.

- [ ] **Step 3: Implement complexity rules in PasswordValidation.kt**

Replace the entire file:

```kotlin
package io.github.rygel.outerstellar.platform.security

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 128

fun validatePassword(password: String): String? {
    val trimmed = password.trim()
    if (trimmed.length < MIN_PASSWORD_LENGTH) {
        return "Password must be at least $MIN_PASSWORD_LENGTH characters"
    }
    if (trimmed.length > MAX_PASSWORD_LENGTH) {
        return "Password must be at most $MAX_PASSWORD_LENGTH characters"
    }
    if (trimmed.none { it.isUpperCase() }) {
        return "Password must contain at least one uppercase letter"
    }
    if (trimmed.none { it.isLowerCase() }) {
        return "Password must contain at least one lowercase letter"
    }
    if (trimmed.none { it.isDigit() }) {
        return "Password must contain at least one digit"
    }
    if (trimmed.none { !it.isLetterOrDigit() }) {
        return "Password must contain at least one special character"
    }
    return null
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl platform-security test -Dtest=PasswordValidationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/PasswordValidation.kt platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/PasswordValidationTest.kt
git commit -m "feat(security): enforce password complexity rules (uppercase, lowercase, digit, special)"
```

---

### Task 2: Registration Toggle — Config and Service

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

- [ ] **Step 1: Add `registrationEnabled` to AppConfig**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`:

Add a constant after the existing `DEFAULT_LOCKOUT_DURATION_SECONDS`:
```kotlin
private const val DEFAULT_REGISTRATION_ENABLED = true
```

Add field to the `AppConfig` data class (after `lockoutDurationSeconds` on line 79):
```kotlin
val registrationEnabled: Boolean = DEFAULT_REGISTRATION_ENABLED,
```

Add to `buildFromYaml()` (after `lockoutDurationSeconds` parsing around line 160):
```kotlin
registrationEnabled = yaml.bool("registrationEnabled", env, "REGISTRATION_ENABLED", DEFAULT_REGISTRATION_ENABLED),
```

- [ ] **Step 2: Add registration guard to SecurityService.register()**

In `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`, at the top of the `register()` method (around line 111), add a guard:

```kotlin
fun register(username: String, password: String): User {
    if (!config.registrationEnabled) {
        throw RegistrationDisabledException("Registration is disabled")
    }
    // ... existing logic unchanged
}
```

Add the exception class. Check if `RegistrationDisabledException` already exists — if not, add it alongside the other custom exceptions in the security package (near `UsernameAlreadyExistsException`, `WeakPasswordException`). Search for where those are defined and add:

```kotlin
class RegistrationDisabledException(message: String) : RuntimeException(message)
```

- [ ] **Step 3: Write test for registration disabled**

In `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`, add:

```kotlin
@Test
fun `register throws RegistrationDisabledException when registration is disabled`() {
    val disabledConfig = securityConfig.copy(registrationEnabled = false)
    val service = createServiceWithConfig(disabledConfig)
    assertFailsWith<RegistrationDisabledException> {
        service.register("newuser@test.com", "ValidP@ss1")
    }
}
```

Note: Check how `createServiceWithConfig` or equivalent helper is defined in the existing test file and follow the same pattern. The `SecurityConfig` data class needs a `registrationEnabled` field added (see next step).

- [ ] **Step 4: Add `registrationEnabled` to SecurityConfig**

In `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt`, add to the `SecurityConfig` data class (line 93-98):

```kotlin
data class SecurityConfig(
    val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    val sessionTimeoutSeconds: Long = 1800L,
    val maxFailedLoginAttempts: Int = 10,
    val lockoutDurationSeconds: Long = 900,
    val sessionAbsoluteTimeoutSeconds: Long = 86400L,
    val registrationEnabled: Boolean = true,
)
```

- [ ] **Step 5: Wire the new fields in SecurityModule**

In `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityModule.kt`, update the `SecurityConfig` construction (lines 20-25):

```kotlin
SecurityConfig(
    appBaseUrl = get<AppConfig>().appBaseUrl,
    sessionTimeoutSeconds = get<AppConfig>().sessionTimeoutMinutes.toLong() * 60,
    maxFailedLoginAttempts = get<AppConfig>().maxFailedLoginAttempts,
    lockoutDurationSeconds = get<AppConfig>().lockoutDurationSeconds,
    sessionAbsoluteTimeoutSeconds = get<AppConfig>().sessionAbsoluteTimeoutMinutes.toLong() * 60,
    registrationEnabled = get<AppConfig>().registrationEnabled,
),
```

- [ ] **Step 6: Add `sessionAbsoluteTimeoutMinutes` to AppConfig**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`:

Add a constant:
```kotlin
private const val DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES = 1440
```

Add field to `AppConfig` data class (after `sessionTimeoutMinutes`):
```kotlin
val sessionAbsoluteTimeoutMinutes: Int = DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES,
```

Add to `buildFromYaml()` after sessionTimeoutMinutes:
```kotlin
sessionAbsoluteTimeoutMinutes = yaml.int("sessionAbsoluteTimeoutMinutes", env, "SESSION_ABSOLUTE_TIMEOUT_MINUTES", DEFAULT_SESSION_ABSOLUTE_TIMEOUT_MINUTES),
```

- [ ] **Step 7: Run tests**

Run: `mvn -pl platform-core,platform-security test -T4`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityModule.kt platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt
git commit -m "feat(security): add registration toggle and absolute session timeout config"
```

---

### Task 3: Session Absolute Timeout Enforcement

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

- [ ] **Step 1: Write failing test for absolute timeout**

In `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`, add:

```kotlin
@Test
fun `lookupSession returns Expired when absolute timeout exceeded`() {
    val config = securityConfig.copy(
        sessionTimeoutSeconds = 3600,
        sessionAbsoluteTimeoutSeconds = 7200,
    )
    val service = createServiceWithConfig(config)
    val user = service.register("abs@test.com", "ValidP@ss1")
    val rawToken = service.createSession(user.id)

    // Manually set created_at to beyond the absolute timeout
    // The session was just created, so we need to simulate an old created_at.
    // Use the sessionRepository to find and re-save with old createdAt.
    val repo = service.sessionRepository!!
    val tokenHash = hashToken(rawToken)
    val session = repo.findByTokenHashIncludingExpired(tokenHash)!!
    val oldSession = session.copy(createdAt = Instant.now().minusSeconds(7201))
    repo.deleteByTokenHash(tokenHash)
    repo.save(oldSession)

    val result = service.lookupSession(rawToken)
    assertTrue(result is SessionLookup.Expired)
}
```

Note: Check if `hashToken` is accessible from the test (it may be private/internal). If not, use `sessionRepository` directly or make the test call the method differently. Adapt to the test file's existing patterns.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-security test -Dtest=SecurityServiceTest`
Expected: FAIL — `lookupSession` currently returns `Active` for sessions within the sliding window.

- [ ] **Step 3: Implement absolute timeout check in lookupSession()**

In `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`, in the `lookupSession()` method (around lines 317-337), add the absolute timeout check inside the `activeSession != null` block, right after the `user != null && user.enabled` check:

Replace the block starting at line 321:
```kotlin
if (activeSession != null) {
    val user = userRepository.findById(activeSession.userId)
    if (user != null && user.enabled) {
        // Extend session on activity
        repo.updateExpiresAt(tokenHash, Instant.now().plusSeconds(config.sessionTimeoutSeconds))
        activityUpdater?.record(user.id) ?: userRepository.updateLastActivity(user.id)
        return SessionLookup.Active(user)
    }
    return SessionLookup.NotFound
}
```

With:
```kotlin
if (activeSession != null) {
    val absoluteDeadline = activeSession.createdAt.plusSeconds(config.sessionAbsoluteTimeoutSeconds)
    if (Instant.now().isAfter(absoluteDeadline)) {
        repo.deleteByTokenHash(tokenHash)
        return SessionLookup.Expired
    }
    val user = userRepository.findById(activeSession.userId)
    if (user != null && user.enabled) {
        repo.updateExpiresAt(tokenHash, Instant.now().plusSeconds(config.sessionTimeoutSeconds))
        activityUpdater?.record(user.id) ?: userRepository.updateLastActivity(user.id)
        return SessionLookup.Active(user)
    }
    return SessionLookup.NotFound
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl platform-security test -Dtest=SecurityServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt
git commit -m "feat(security): enforce absolute session timeout ceiling"
```

---

### Task 4: Registration Toggle — Route Guards

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthApi.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt`

- [ ] **Step 1: Guard API registration route**

In `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthApi.kt`, the `AuthApi` constructor already receives `securityService: SecurityService`. Add `appConfig: AppConfig` as a constructor parameter (check how other config is injected — look at the constructor signature).

At the top of the register handler (around line 252), before the `try` block, add:

```kotlin
if (!appConfig.registrationEnabled) {
    return@bindContract Response(Status.FORBIDDEN).body("Registration is disabled")
}
```

Check how `AuthApi` is instantiated in `App.kt` — add `get<AppConfig>()` or `get()` to the Koin injection if needed.

- [ ] **Step 2: Guard web registration handler**

In `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt`, the `AuthRoutes` class needs access to `AppConfig`. Check its constructor — if it doesn't already have it, add `appConfig: AppConfig` as a parameter.

At the top of the `mode == "register"` block (around line 103), before the `try` block, add:

```kotlin
if (!appConfig.registrationEnabled) {
    return@bindContract renderer.render(
        AuthResultFragment(
            title = ctx.i18n.translate("web.auth.result.error.title"),
            message = "Registration is currently disabled",
            toneClass = "bg-error/10 border-error/30 text-error",
        )
    )
}
```

Check how `AuthRoutes` is instantiated in `App.kt` — add `get<AppConfig>()` to the Koin injection if needed.

- [ ] **Step 3: Run compilation check**

Run: `mvn -pl platform-web compile -DskipTests -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthApi.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "feat(security): guard registration routes with registrationEnabled toggle"
```

---

### Task 5: Update AGENTS.md Config Reference

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Add new config entries to the Core Config table**

In the Core Config section of `AGENTS.md`, add these rows to the table:

```markdown
| `registrationEnabled` | `REGISTRATION_ENABLED` | true | Enable or disable public user registration |
| `sessionAbsoluteTimeoutMinutes` | `SESSION_ABSOLUTE_TIMEOUT_MINUTES` | 1440 | Absolute max session lifetime in minutes (cannot be extended by sliding window) |
```

- [ ] **Step 2: Commit**

```bash
git add AGENTS.md
git commit -m "docs: add registration toggle and absolute session timeout to AGENTS.md config reference"
```

---

### Task 6: Full Build Verification and PR

- [ ] **Step 1: Run full reactor build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed`
Expected: BUILD SUCCESS

If detekt/spotbugs failures occur, fix them before proceeding.

- [ ] **Step 2: Push and create PR**

```bash
git push -u origin fix/security-hardening-medium
gh pr create --base develop --title "fix(security): harden session TTL, registration toggle, password complexity" --body "Implements MEDIUM-severity security audit findings:

1. **Session absolute timeout** — adds hard ceiling (default 24h) on session lifetime. Sessions can no longer slide forever.

2. **Registration toggle** — \`registrationEnabled\` config flag (default: true). When disabled, registration routes return 403 (API) or error message (web). Admin user creation unaffected.

3. **Password complexity** — enforces uppercase, lowercase, digit, and special character requirements in addition to existing length rules (8-128).

Spec: \`docs/superpowers/specs/2026-05-18-security-hardening-medium-design.md\`"
```
