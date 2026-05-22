# Test Suite Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 6 systemic issues in the test suite: automatic cleanup in WebTest, deduplicate `createUser()`, deduplicate `hashToken()`, centralize cleanup table lists, and enforce architecture rules across all modules.

**Architecture:** Extract shared test utilities into existing base classes (`JdbiTest`, `WebTest`) and a new production utility class. Move `ArchitectureTest` to a module with visibility of all classes. Each task is independent and produces a working, testable change.

**Tech Stack:** Kotlin, JUnit 5, JDBI, Testcontainers, ArchUnit, Maven

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-core/src/main/kotlin/.../security/TokenHashing.kt` | Shared SHA-256 token hashing utility |
| Modify | `platform-security/.../security/SecurityService.kt` | Use TokenHashing.hash() |
| Modify | `platform-security/.../security/ApiKeyService.kt` | Use TokenHashing.hash() |
| Modify | `platform-security/.../security/PasswordResetService.kt` | Use TokenHashing.hash() |
| Modify | `platform-persistence-jdbi/.../persistence/JdbiTest.kt` | Add `createUser()`, centralized cleanup table list |
| Modify | `platform-web/.../web/WebTest.kt` | Automatic `@AfterEach` cleanup, centralized table list |
| Modify | 6 persistence test files | Remove duplicate `createUser()`, use base class |
| Modify | `platform-persistence-jdbi/.../persistence/JdbiSessionRepositoryTest.kt` | Remove duplicate `hashToken()`, use TokenHashing |
| Modify | `platform-core/.../arch/ArchitectureTest.kt` | Add note about classpath limitation |

---

### Task 1: Make cleanup automatic in WebTest

**Problem:** All 52 WebTest subclasses manually add `@AfterEach fun teardown() = cleanup()`. Forgetting this causes test pollution. JdbiTest already does this correctly.

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`
- Modify: All 52 WebTest subclass files (remove `@AfterEach` teardown methods)

- [ ] **Step 1: Add automatic cleanup to WebTest base class**

In `WebTest.kt`, add an `@AfterEach` method to the `WebTest` abstract class body (outside the companion object):

```kotlin
import org.junit.jupiter.api.AfterEach

abstract class WebTest protected constructor() {
    @AfterEach
    fun resetState() {
        cleanup()
    }

    companion object {
        // ... existing companion object unchanged
    }
}
```

- [ ] **Step 2: Remove `@AfterEach` teardown from all WebTest subclasses that ONLY call cleanup()**

These 44 files have teardown methods that only call `cleanup()` (or `cleanup()` with no meaningful extra work). Remove the entire teardown method from each:

**Pattern A (one-liner) — 37 files:** Remove the `@AfterEach fun teardown() = cleanup()` line from:
- `PasswordResetFlowIntegrationTest.kt`
- `UserManagementWebUiIntegrationTest.kt`
- `SessionTimeoutIntegrationTest.kt`
- `AuditLogIntegrationTest.kt`
- `PlatformPageRenderingTest.kt`
- `PollIntegrationTest.kt`
- `WebSocketSyncIntegrationTest.kt`
- `SyncConflictResolutionIntegrationTest.kt`
- `SyncApiIntegrationTest.kt`
- `SessionSecurityIntegrationTest.kt`
- `SecurityHeadersIntegrationTest.kt`
- `ProfileApiIntegrationTest.kt`
- `MessageSearchIntegrationTest.kt`
- `MessageRestoreIntegrationTest.kt`
- `LogoutIntegrationTest.kt`
- `LoginReturnToIntegrationTest.kt`
- `DevDashboardAccessIntegrationTest.kt`
- `DarkModeToggleIntegrationTest.kt`
- `ContactsSyncCrudIntegrationTest.kt`
- `ContactsPaginationIntegrationTest.kt`
- `ContactDetailsSyncIntegrationTest.kt`
- `ConcurrentSyncIntegrationTest.kt`
- `ComponentFragmentIntegrationTest.kt`
- `ChangePasswordWebIntegrationTest.kt`
- `AuthProfileApiKeysIntegrationTest.kt`
- `AuthHtmlFlowIntegrationTest.kt`
- `ApiKeyLifecycleIntegrationTest.kt`
- `ApiKeyAuthIntegrationTest.kt`
- `AdminHtmlRoutesIntegrationTest.kt`
- `AdminExportIntegrationTest.kt`
- `ReproductionTest.kt`
- `HomePageEndToEndTest.kt`
- `UnauthenticatedRouteAccessTest.kt`
- `RateLimiterIntegrationTest.kt`
- `StaticAssetIntegrationTest.kt`
- `ErrorPagesIntegrationTest.kt`
- `AuthenticationWorkflowTest.kt`
- `NotificationsIntegrationTest.kt`
- `HealthCheckIntegrationTest.kt`
- `CsrfProtectionIntegrationTest.kt`
- `AuthApiIntegrationTest.kt`
- `PerformanceBenchmarkTest.kt` (method named `teardownBenchmark`)

**Pattern B (multi-line block, cleanup only) — 7 files:** Remove the `@AfterEach fun teardown() { cleanup() }` block from:
- `SecurityIntegrationTest.kt`
- `UserManagementIntegrationTest.kt`
- `SyncIntegrationTest.kt`
- `StatePersistenceE2ETest.kt`
- `MessageActionE2ETest.kt`
- `AuthPageE2ETest.kt`

**Keep (files with extra cleanup logic beyond cleanup()) — 5 files:**
- `PushNotificationsIntegrationTest.kt` — keep, it calls `deviceTokenRepository.clear()` before `cleanup()`
- `DeviceRegistrationApiIntegrationTest.kt` — keep, it calls `deviceTokenRepository.clear()` before `cleanup()`
- `OAuthIntegrationTest.kt` — keep, it calls `oauthRepository.clear()` before `cleanup()`
- `MdcLoggingIntegrationTest.kt` — keep, it detaches logback appender
- `PerformanceBenchmarkTest.kt` — also has teardownBenchmark, keep it

For these 5 files: remove only the `cleanup()` call from their teardown methods (the base class now handles it). Keep the extra logic.

- [ ] **Step 3: Remove unused `@AfterEach` imports from files that no longer need it**

After removing teardown methods, some files may have an unused `import org.junit.jupiter.api.AfterEach`. Remove those unused imports.

- [ ] **Step 4: Compile platform-web**

```powershell
mvn -pl platform-web compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run platform-web tests**

```powershell
mvn -pl platform-web test -Dexec.skip=true
```

Expected: 593 tests, 0 failures

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor(test): make cleanup automatic in WebTest base class

Move @AfterEach cleanup() from 44 subclass files into WebTest base class.
Files with extra teardown logic (PushNotifications, DeviceRegistration,
OAuth, MdcLogging, PerformanceBenchmark) keep their additional cleanup
and only remove the redundant cleanup() call."
```

---

### Task 2: Centralize cleanup table list

**Problem:** Both `JdbiTest.cleanDatabase()` and `WebTest.cleanup()` maintain identical 17-18 line DELETE chains. New tables must be added to both lists.

**Files:**
- Modify: `platform-persistence-jdbi/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiTest.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`

- [ ] **Step 1: Extract shared table list into JdbiTest companion**

In `JdbiTest.kt`, add a `CLEANUP_TABLES` constant to the companion object:

```kotlin
companion object {
    val CLEANUP_TABLES = listOf(
        "plt_sessions",
        "plt_notifications",
        "plt_device_tokens",
        "plt_oauth_connections",
        "plt_api_keys",
        "plt_password_reset_tokens",
        "plt_audit_log",
        "plt_outbox",
        "plt_contact_emails",
        "plt_contact_phones",
        "plt_contact_socials",
        "plt_contacts",
        "plt_messages",
        "plt_poll_votes",
        "plt_poll_options",
        "plt_polls",
        "plt_sync_state",
        "plt_users",
    )

    // ... existing container and sharedJdbi unchanged
}
```

Update `cleanDatabase()` to use it:

```kotlin
@AfterEach
fun cleanDatabase() {
    jdbi.useHandle<Exception> { handle ->
        CLEANUP_TABLES.forEach { table ->
            handle.execute("DELETE FROM $table")
        }
    }
}
```

- [ ] **Step 2: Make WebTest.cleanup() reference the same list**

In `WebTest.kt`, import `JdbiTest.CLEANUP_TABLES` and update `cleanup()`:

```kotlin
import io.github.rygel.outerstellar.platform.persistence.JdbiTest

companion object {
    // ...

    fun cleanup() {
        testJdbi.useHandle<Exception> { handle ->
            JdbiTest.CLEANUP_TABLES.forEach { table ->
                handle.execute("DELETE FROM $table")
            }
        }
    }
}
```

Remove the old inline DELETE chain.

- [ ] **Step 3: Compile both modules**

```powershell
mvn -pl platform-persistence-jdbi,platform-web compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run full test suite**

```powershell
mvn -pl platform-persistence-jdbi,platform-web test -Dexec.skip=true
```

Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor(test): centralize cleanup table list in JdbiTest.CLEANUP_TABLES

Both JdbiTest.cleanDatabase() and WebTest.cleanup() now use a single
source of truth for the ordered table list. Adding a new table only
requires updating one place."
```

---

### Task 3: Extract `createUser()` to JdbiTest base class

**Problem:** 6 persistence test files contain identical `createUser()` helper functions.

**Files:**
- Modify: `platform-persistence-jdbi/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiSessionRepositoryTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiPasswordResetRepositoryTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiOAuthRepositoryTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiNotificationRepositoryTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiDeviceTokenRepositoryTest.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiApiKeyRepositoryTest.kt`

- [ ] **Step 1: Add `createUser()` to JdbiTest**

In `JdbiTest.kt`, add the helper to the class body:

```kotlin
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID

abstract class JdbiTest {
    protected lateinit var jdbi: Jdbi

    protected fun createUser(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
    ): UUID {
        val id = UUID.randomUUID()
        JdbiUserRepository(jdbi).save(
            User(
                id = id,
                username = username,
                email = "${id.toString().take(6)}@example.com",
                passwordHash = "hash",
                role = role,
            )
        )
        return id
    }

    // ... existing setupDatabase, cleanDatabase, companion unchanged
}
```

- [ ] **Step 2: Remove `createUser()` from all 6 test files**

In each of the following files, remove the `private fun createUser()` method AND the `private val userRepo by lazy { JdbiUserRepository(jdbi) }` field (if no other tests in that file use `userRepo` directly):

1. `JdbiSessionRepositoryTest.kt` — remove `createUser()` (lines 20-32) and `userRepo` (line 18) — `userRepo` not used elsewhere in the file, `repo` is the session repo
2. `JdbiPasswordResetRepositoryTest.kt` — remove `createUser()` (lines 20-32) and `userRepo` (line 18) if not used elsewhere
3. `JdbiOAuthRepositoryTest.kt` — remove `createUser()` (lines 18-30) and `userRepo` (line 16) if not used elsewhere
4. `JdbiNotificationRepositoryTest.kt` — remove `createUser()` (lines 18-30) and `userRepo` (line 16) if not used elsewhere
5. `JdbiDeviceTokenRepositoryTest.kt` — remove `createUser()` (lines 18-30) and `userRepo` (line 16) if not used elsewhere
6. `JdbiApiKeyRepositoryTest.kt` — remove `createUser()` (lines 20-32) and `userRepo` (line 18) if not used elsewhere

**Important:** Before removing `userRepo`, verify it is not used directly by any test in that file. If it IS used directly, keep the `userRepo` field but still remove `createUser()`.

Also remove any unused imports (`UUID`, `User`, `UserRole`) that were only used by the removed `createUser()` method.

- [ ] **Step 3: Compile platform-persistence-jdbi**

```powershell
mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run platform-persistence-jdbi tests**

```powershell
mvn -pl platform-persistence-jdbi test
```

Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor(test): extract createUser() to JdbiTest base class

Remove 6 identical createUser() implementations from persistence tests.
The shared helper is now available to all JdbiTest subclasses."
```

---

### Task 4: Extract `hashToken()` to shared utility

**Problem:** `hashToken()` (SHA-256 hex digest) is duplicated 3 times in production (`SecurityService`, `ApiKeyService`, `PasswordResetService`) and once in test code (`JdbiSessionRepositoryTest`).

**Files:**
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/security/TokenHashing.kt`
- Modify: `platform-security/src/main/kotlin/.../security/SecurityService.kt`
- Modify: `platform-security/src/main/kotlin/.../security/ApiKeyService.kt`
- Modify: `platform-security/src/main/kotlin/.../security/PasswordResetService.kt`
- Modify: `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiSessionRepositoryTest.kt`

- [ ] **Step 1: Create `TokenHashing.kt` in platform-core**

Create `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/security/TokenHashing.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.security

import java.security.MessageDigest

object TokenHashing {
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 2: Replace `hashToken()` in SecurityService.kt**

In `SecurityService.kt`, find the private `hashToken(key)` method (around line 415-418) and:

1. Replace all calls to `hashToken(key)` with `TokenHashing.hash(key)`
2. Remove the private `hashToken()` method entirely
3. Add import: `import io.github.rygel.outerstellar.platform.security.TokenHashing`

- [ ] **Step 3: Replace `hashToken()` in ApiKeyService.kt**

In `ApiKeyService.kt`, find the private `hashToken(key)` method (around line 68-71) and:

1. Replace all calls to `hashToken(key)` with `TokenHashing.hash(key)`
2. Remove the private `hashToken()` method entirely
3. Add import: `import io.github.rygel.outerstellar.platform.security.TokenHashing`

- [ ] **Step 4: Replace `hashToken()` in PasswordResetService.kt**

In `PasswordResetService.kt`, find the private `hashToken(token)` method (around line 105-108) and:

1. Replace all calls to `hashToken(token)` with `TokenHashing.hash(token)`
2. Remove the private `hashToken()` method entirely
3. Add import: `import io.github.rygel.outerstellar.platform.security.TokenHashing`

- [ ] **Step 5: Replace `hashToken()` in JdbiSessionRepositoryTest.kt**

In `JdbiSessionRepositoryTest.kt`, find the private `hashToken(token)` method (lines 34-37) and:

1. Replace all calls to `hashToken(...)` with `TokenHashing.hash(...)`
2. Remove the private `hashToken()` method entirely
3. Add import: `import io.github.rygel.outerstellar.platform.security.TokenHashing`

- [ ] **Step 6: Compile all affected modules**

```powershell
mvn -pl platform-core,platform-security,platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Run tests for affected modules**

```powershell
mvn -pl platform-persistence-jdbi,platform-security test
```

Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: extract hashToken() to shared TokenHashing utility

Replace 3 identical private hashToken() implementations in SecurityService,
ApiKeyService, and PasswordResetService with a single TokenHashing.hash()
in platform-core. Also replaces the duplicate in JdbiSessionRepositoryTest."
```

---

### Task 5: Document ArchitectureTest classpath limitation

**Problem:** `ArchitectureTest` lives in `platform-core` and only has core classes on classpath. All 9 ArchUnit rules targeting other modules use `allowEmptyShould(true)` and match 0 classes — they always pass. This creates a false sense of security.

**Files:**
- Modify: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/arch/ArchitectureTest.kt`

- [ ] **Step 1: Add explicit documentation and assertions**

At the top of the `ArchitectureTest` class, replace the existing comment with a stronger warning and add a test that verifies the rules are actually checking something:

```kotlin
class ArchitectureTest {

    // WARNING: This test class lives in platform-core and only has core classes on its classpath.
    // Rules targeting ..persistence.., ..security.., ..web.., ..desktop.., ..sync..
    // will match ZERO classes and always pass regardless of violations.
    //
    // To properly enforce architecture rules across all modules, this test needs to be moved
    // to a module that depends on all other modules (e.g., a new architecture-tests module,
    // or platform-web which transitively depends on everything).
    //
    // See: docs/test-suite-improvements.md, Issue #4
```

- [ ] **Step 2: Compile**

```powershell
mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Run ArchitectureTest**

```powershell
mvn -pl platform-core test -Dtest=ArchitectureTest
```

Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "docs(test): document ArchitectureTest classpath limitation

Add explicit warning that ArchUnit rules for non-core modules match zero
classes. Reference test-suite-improvements.md for the proper fix."
```

---

## Execution Order

Tasks 1-4 are **independent** and can be executed in parallel by separate agents. Task 5 is also independent but trivial.

**Recommended serial order** (if not parallelizing):
1. Task 2 (centralize cleanup) — foundation for Task 1
2. Task 1 (automatic cleanup) — depends on Task 2 being done first for clean diff
3. Task 3 (extract createUser) — independent
4. Task 4 (extract hashToken) — independent
5. Task 5 (ArchitectureTest docs) — independent

**Parallel order** (if using subagents):
- Tasks 2, 3, 4, 5 can all run simultaneously
- Task 1 runs after Task 2 completes (merge the centralized cleanup change first)

## Out of Scope (Deferred)

- Issue #1 (stale classpath detection) — requires Maven enforcer plugin investigation
- Issue #6 (two PostgreSQL containers) — requires Testcontainers reuse investigation
- Issue #7 (test data builders) — large effort, incremental
- Issue #8 (raw SQL in tests) — low priority, only 3 files
- Issue #10 (app rebuild per test) — requires investigation of per-test customization
