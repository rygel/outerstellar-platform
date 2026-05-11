# Account Lockout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-account brute-force lockout protection with auto-unlock after 15 minutes and manual admin unlock.

**Architecture:** Add `failed_login_attempts` and `locked_until` columns to `plt_users`. Check lockout in `SecurityService.authenticate()` before password verification. Atomic increment on failure, reset on success. Admin unlock route + UI button.

**Tech Stack:** Kotlin 2.3, jOOQ, JDBI, Flyway, http4k, JTE templates

---

### Task 1: Database Migration + Config + User Model

**Files:**
- Create: `platform-persistence-jooq/src/main/resources/db/migration/V7__account_lockout.sql`
- Create: `platform-persistence-jdbi/src/main/resources/db/migration/V7__account_lockout.sql`
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt`
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/model/AuthModels.kt`

- [ ] **Step 1: Create V7 Flyway migration for jooq module**

`platform-persistence-jooq/src/main/resources/db/migration/V7__account_lockout.sql`:
```sql
ALTER TABLE plt_users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE plt_users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;
```

- [ ] **Step 2: Create identical V7 Flyway migration for jdbi module**

`platform-persistence-jdbi/src/main/resources/db/migration/V7__account_lockout.sql`:
```sql
ALTER TABLE plt_users ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE plt_users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;
```

- [ ] **Step 3: Add lockout config to AppConfig**

In `AppConfig.kt` (`platform-core/.../AppConfig.kt`), add before the `jwt:` field in the data class:
```kotlin
val maxFailedLoginAttempts: Int = 10,
val lockoutDurationSeconds: Long = 900,
```

In `buildFromYaml()`, add after the `jwt = buildJwtConfig(...)` line:
```kotlin
maxFailedLoginAttempts = yaml.int("maxFailedLoginAttempts", env, "MAX_FAILED_LOGIN_ATTEMPTS", 10),
lockoutDurationSeconds = yaml.long("lockoutDurationSeconds", env, "LOCKOUT_DURATION_SECONDS", 900),
```

- [ ] **Step 4: Add lockout fields to User model**

In `Models.kt` (`platform-security/.../security/Models.kt`), add to `User` data class:
```kotlin
val failedLoginAttempts: Int = 0,
val lockedUntil: Instant? = null,
```

- [ ] **Step 5: Add lockout fields to UserSummary**

In `AuthModels.kt` (`platform-core/.../model/AuthModels.kt`), add to `UserSummary`:
```kotlin
val failedLoginAttempts: Int = 0,
val lockedUntil: Instant? = null,
```

In `SecurityService.kt` (`platform-security/.../security/SecurityService.kt`), update `User.toSummary()` extension at the bottom:
```kotlin
private fun User.toSummary() =
    UserSummary(
        id = id.toString(),
        username = username,
        email = email,
        role = role.name,
        enabled = enabled,
        failedLoginAttempts = failedLoginAttempts,
        lockedUntil = lockedUntil,
    )
```

- [ ] **Step 6: Compile to verify**

```bash
mvn compile -pl platform-core,platform-security -am -q -Dspotless.check.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add platform-persistence-jooq/src/main/resources/db/migration/V7__account_lockout.sql \
       platform-persistence-jdbi/src/main/resources/db/migration/V7__account_lockout.sql \
       platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt \
       platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt \
       platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/AuthModels.kt
git commit -m "feat(auth): add account lockout migration, config, and model fields"
```

---

### Task 2: Repository Interface + Implementations

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt`
- Modify: `platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JooqUserRepository.kt`
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiUserRepository.kt`

- [ ] **Step 1: Add methods to UserRepository interface**

In `Models.kt` (`platform-security/.../security/Models.kt`), add to `UserRepository` interface after `updatePreferences`:
```kotlin
fun incrementFailedLoginAttempts(userId: UUID): Int
fun resetFailedLoginAttempts(userId: UUID)
fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)
```

- [ ] **Step 2: Implement in JooqUserRepository**

In `JooqUserRepository.kt` (`platform-persistence-jooq/.../JooqUserRepository.kt`), add after `updatePreferences`:
```kotlin
override fun incrementFailedLoginAttempts(userId: UUID): Int {
    return dsl.update(PLT_USERS)
        .set(PLT_USERS.FAILED_LOGIN_ATTEMPTS, PLT_USERS.FAILED_LOGIN_ATTEMPTS.plus(1))
        .where(PLT_USERS.ID.eq(userId))
        .returning(PLT_USERS.FAILED_LOGIN_ATTEMPTS)
        .fetchOne()
        ?.get(PLT_USERS.FAILED_LOGIN_ATTEMPTS) ?: 0
}

override fun resetFailedLoginAttempts(userId: UUID) {
    dsl.update(PLT_USERS)
        .set(PLT_USERS.FAILED_LOGIN_ATTEMPTS, 0)
        .set(PLT_USERS.LOCKED_UNTIL, null as Instant?)
        .where(PLT_USERS.ID.eq(userId))
        .execute()
}

override fun updateLockedUntil(userId: UUID, lockedUntil: Instant?) {
    dsl.update(PLT_USERS)
        .set(PLT_USERS.LOCKED_UNTIL, lockedUntil)
        .where(PLT_USERS.ID.eq(userId))
        .execute()
}
```

- [ ] **Step 3: Implement in JdbiUserRepository**

In `JdbiUserRepository.kt` (`platform-persistence-jdbi/.../JdbiUserRepository.kt`), add after `updatePreferences`:
```kotlin
override fun incrementFailedLoginAttempts(userId: UUID): Int {
    return jdbi.withHandle<Int, Exception> { handle ->
        handle.createQuery(
            """
            UPDATE plt_users
            SET failed_login_attempts = failed_login_attempts + 1
            WHERE id = :id
            RETURNING failed_login_attempts
            """.trimIndent()
        )
            .bind("id", userId)
            .mapTo(Int::class.java)
            .one()
    }
}

override fun resetFailedLoginAttempts(userId: UUID) {
    jdbi.useHandle<Exception> { handle ->
        handle.createUpdate(
            """
            UPDATE plt_users
            SET failed_login_attempts = 0, locked_until = NULL
            WHERE id = :id
            """.trimIndent()
        )
            .bind("id", userId)
            .execute()
    }
}

override fun updateLockedUntil(userId: UUID, lockedUntil: Instant?) {
    jdbi.useHandle<Exception> { handle ->
        handle.createUpdate("UPDATE plt_users SET locked_until = :lockedUntil WHERE id = :id")
            .bind("id", userId)
            .bind("lockedUntil", lockedUntil)
            .execute()
    }
}
```

- [ ] **Step 4: Compile to verify**

```bash
mvn compile -pl platform-security,platform-persistence-jooq,platform-persistence-jdbi -am -q -Dspotless.check.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt \
       platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JooqUserRepository.kt \
       platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiUserRepository.kt
git commit -m "feat(auth): add UserRepository lockout methods with jOOQ and JDBI implementations"
```

---

### Task 3: SecurityService authenticate() Lockout Logic + unlockAccount()

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

- [ ] **Step 1: Add lockout config parameters to SecurityService constructor**

Add after `sessionTimeoutSeconds` parameter:
```kotlin
private val maxFailedLoginAttempts: Int = 10,
private val lockoutDurationSeconds: Long = 900,
```

- [ ] **Step 2: Modify authenticate() to check lockout and track attempts**

Replace the `authenticate()` method:
```kotlin
fun authenticate(username: String, password: String): User? {
    val user = userRepository.findByUsername(username)

    return when {
        user == null -> {
            logger.warn("Authentication failed: User $username not found")
            null
        }
        !user.enabled -> {
            logger.warn("Authentication failed: User $username is disabled")
            null
        }
        user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now()) -> {
            logger.warn("Authentication failed: User $username is locked until ${user.lockedUntil}")
            null
        }
        passwordEncoder.matches(password, user.passwordHash) -> {
            if (user.failedLoginAttempts > 0) {
                userRepository.resetFailedLoginAttempts(user.id)
            }
            logger.info("Authentication successful for user $username")
            user
        }
        else -> {
            val attempts = userRepository.incrementFailedLoginAttempts(user.id)
            logger.warn("Authentication failed: Invalid password for user $username (attempt $attempts)")
            if (attempts >= maxFailedLoginAttempts) {
                val until = Instant.now().plusSeconds(lockoutDurationSeconds)
                userRepository.updateLockedUntil(user.id, until)
                logger.warn("User $username locked until $until after $attempts failed attempts")
            }
            null
        }
    }
}
```

- [ ] **Step 3: Add unlockAccount() method**

Add to `SecurityService` after `setUserEnabled()`:
```kotlin
fun unlockAccount(adminId: UUID, targetId: UUID) {
    val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
    if (admin.role != UserRole.ADMIN) {
        throw InsufficientPermissionException("Admin access required to unlock accounts")
    }
    val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
    userRepository.resetFailedLoginAttempts(targetId)
    logger.info("User {} unlocked by admin {}", target.username, admin.username)
    audit("USER_UNLOCKED", actor = admin, target = target)
}
```

The `UserIdNotFoundException`, `InsufficientPermissionException` already exist in the imports -- verify the imports at the top of `SecurityService.kt` include `io.github.rygel.outerstellar.platform.model.UserNotFoundException` and `io.github.rygel.outerstellar.platform.model.InsufficientPermissionException`.

- [ ] **Step 4: Compile to verify**

```bash
mvn compile -pl platform-security -am -q -Dspotless.check.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt
git commit -m "feat(auth): add lockout logic to authenticate() and unlockAccount() method"
```

---

### Task 4: SecurityService Unit Tests

**Files:**
- Modify: `platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt`

- [ ] **Step 1: Add test -- locked account returns null**

Add test methods to `SecurityServiceTest`:
```kotlin
@Test
fun `authenticate returns null for locked account`() {
    val lockedUser = defaultUser.copy(lockedUntil = Instant.now().plusSeconds(300))
    every { userRepository.findByUsername("alice") } returns lockedUser

    val result = svc.authenticate("alice", "irrelevant")

    assertNull(result, "Locked account should not authenticate")
}
```

- [ ] **Step 2: Add test -- locked account with expired lock succeeds**

```kotlin
@Test
fun `authenticate succeeds when lock has expired`() {
    val unlockedUser = defaultUser.copy(lockedUntil = Instant.now().minusSeconds(60))
    every { userRepository.findByUsername("alice") } returns unlockedUser
    every { passwordEncoder.matches("correct", unlockedUser.passwordHash) } returns true

    val result = svc.authenticate("alice", "correct")

    assertNotNull(result, "Account with expired lock should authenticate")
    verify { userRepository.resetFailedLoginAttempts(unlockedUser.id) }
}
```

- [ ] **Step 3: Add test -- failed attempt increments counter**

```kotlin
@Test
fun `authenticate increments failed attempts on wrong password`() {
    every { userRepository.findByUsername("alice") } returns defaultUser
    every { passwordEncoder.matches("wrong", defaultUser.passwordHash) } returns false
    every { userRepository.incrementFailedLoginAttempts(defaultUser.id) } returns 1

    val result = svc.authenticate("alice", "wrong")

    assertNull(result)
    verify { userRepository.incrementFailedLoginAttempts(defaultUser.id) }
}
```

- [ ] **Step 4: Add test -- threshold triggers lockout**

```kotlin
@Test
fun `authenticate locks account after threshold exceeded`() {
    every { userRepository.findByUsername("alice") } returns defaultUser
    every { passwordEncoder.matches("wrong", defaultUser.passwordHash) } returns false
    every { userRepository.incrementFailedLoginAttempts(defaultUser.id) } returns 10

    val result = svc.authenticate("alice", "wrong")

    assertNull(result)
    verify { userRepository.updateLockedUntil(eq(defaultUser.id), any()) }
}
```

- [ ] **Step 5: Add test -- successful login resets counter**

```kotlin
@Test
fun `authenticate resets failed attempts on success`() {
    val userWithAttempts = defaultUser.copy(failedLoginAttempts = 3)
    every { userRepository.findByUsername("alice") } returns userWithAttempts
    every { passwordEncoder.matches("correct", userWithAttempts.passwordHash) } returns true

    val result = svc.authenticate("alice", "correct")

    assertNotNull(result)
    verify { userRepository.resetFailedLoginAttempts(userWithAttempts.id) }
}
```

- [ ] **Step 6: Add test -- unlockAccount requires admin**

```kotlin
@Test
fun `unlockAccount throws for non-admin caller`() {
    every { userRepository.findById(adminUser.id) } returns adminUser.copy(role = UserRole.USER)

    assertThrows<InsufficientPermissionException> { svc.unlockAccount(adminUser.id, defaultUser.id) }
}
```

- [ ] **Step 7: Add test -- unlockAccount resets attempts and clears lock**

```kotlin
@Test
fun `unlockAccount resets failed attempts for target user`() {
    every { userRepository.findById(adminUser.id) } returns adminUser
    every { userRepository.findById(defaultUser.id) } returns defaultUser

    svc.unlockAccount(adminUser.id, defaultUser.id)

    verify { userRepository.resetFailedLoginAttempts(defaultUser.id) }
}
```

- [ ] **Step 8: Run tests to verify**

```bash
mvn test -pl platform-security -am -Dtest=SecurityServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.check.skip=true -Denforcer.skip=true -Ddetekt.skip=true -Dspotbugs.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```
Expected: All tests pass (including existing + 7 new)

- [ ] **Step 9: Commit**

```bash
git add platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTest.kt
git commit -m "test(auth): add account lockout unit tests"
```

---

### Task 5: Admin Unlock Route + UI

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/UserAdminRoutes.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminPageFactory.kt`

- [ ] **Step 1: Add locked status fields to UserAdminRow**

In `ViewModels.kt` (`platform-web/.../web/ViewModels.kt`), modify `UserAdminRow`:
```kotlin
data class UserAdminRow(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val enabled: Boolean,
    val toggleEnabledUrl: String,
    val toggleRoleUrl: String,
    val isSelf: Boolean,
    val isLocked: Boolean = false,
    val unlockUrl: String = "",
    val failedLoginAttempts: Int = 0,
)
```

- [ ] **Step 2: Build unlock URL and isLocked in AdminPageFactory**

In `AdminPageFactory.kt` (`platform-web/.../web/AdminPageFactory.kt`), update `buildUserAdminPage()` where `UserAdminRow` is created. Add after the `isSelf` line:
```kotlin
isLocked = user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now()),
unlockUrl = if (user.lockedUntil != null && user.lockedUntil.isAfter(Instant.now())) {
    "/admin/users/${user.id}/unlock"
} else "",
failedLoginAttempts = user.failedLoginAttempts,
```

Add the import at the top:
```kotlin
import java.time.Instant
```

- [ ] **Step 3: Add unlock route to UserAdminRoutes**

In `UserAdminRoutes.kt` (`platform-web/.../web/UserAdminRoutes.kt`), add a new route after the toggle-role route:
```kotlin
"/admin/users/{userId}/unlock" meta
    {
        summary = "Unlock a locked user account"
    } bindContract
    POST to
    { request: Request ->
        val ctx = request.webContext
        val admin = ctx.user ?: return@to Response(Status.UNAUTHORIZED)
        val userId = UUID.fromString(request.path("userId"))
        try {
            securityService.unlockAccount(admin.id, userId)
            Response(Status.FOUND).header("Location", "/admin/users")
        } catch (e: UserNotFoundException) {
            Response(Status.NOT_FOUND)
        } catch (e: InsufficientPermissionException) {
            Response(Status.FORBIDDEN)
        }
    },
```

Add the required imports at the top of the file:
```kotlin
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import java.util.UUID
```

- [ ] **Step 4: Compile to verify**

```bash
mvn compile -pl platform-web -am -q -Dspotless.check.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/UserAdminRoutes.kt \
       platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt \
       platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminPageFactory.kt
git commit -m "feat(auth): add admin unlock route and UI"
```

---

### Task 6: Regenerate jOOQ + Full Verify

**Files:**
- Regenerated: `platform-persistence-jooq/src/main/generated/jooq/**/*`

- [ ] **Step 1: Regenerate jOOQ sources**

```bash
./generate-jooq.ps1
```
Or:
```bash
mvn -pl platform-persistence-jooq -Pjooq-codegen generate-sources
```

- [ ] **Step 2: Verify generated sources compile**

```bash
mvn compile -pl platform-persistence-jooq -am -q -Dspotless.check.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Run full verify (excluding desktop)**

```bash
mvn clean verify -T4 -pl '!platform-desktop,!platform-desktop-javafx' -Denforcer.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit generated sources + migration**

```bash
git add platform-persistence-jooq/src/main/generated/jooq/
git commit -m "chore(jooq): regenerate after V7 account lockout migration"
```

- [ ] **Step 5: Push and create PR**

```bash
git push -u origin feat/account-lockout
gh pr create --base develop --title "feat(auth): add account lockout protection" --body "..."
```
