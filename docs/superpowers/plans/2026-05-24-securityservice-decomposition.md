# SecurityService Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `SessionService`, `AuthService`, and `UserAdminService` from the 452-line `SecurityService` god class.

**Architecture:** Three new focused services extracted from SecurityService. Callers updated to depend on the specific service they need. SecurityService retains password management, profile updates, TOTP, and delegation methods. SecurityComponents wires all services together.

**Tech Stack:** Kotlin, Maven, http4k, JDBI

---

### Task 1: Create SessionService

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SessionService.kt`

Extract session management methods from SecurityService into a standalone class.

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.Session
import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class SessionService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val config: SecurityConfig,
    private val activityUpdater: AsyncActivityUpdater? = null,
) {
    private val logger = LoggerFactory.getLogger(SessionService::class.java)
    private val secureRandom = SecureRandom()

    fun createSession(userId: UUID): String {
        val rawToken = "oss_" + generateRandomHex(SESSION_TOKEN_HEX_LENGTH)
        val tokenHash = TokenHashing.hash(rawToken)
        val session =
            Session(
                tokenHash = tokenHash,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(config.sessionTimeoutSeconds),
            )
        sessionRepository.save(session)
        logger.info("Session created for user {}", userId)
        return rawToken
    }

    fun lookupSession(rawToken: String): SessionLookup {
        val tokenHash = TokenHashing.hash(rawToken)
        val activeSession = sessionRepository.findByTokenHash(tokenHash)
        if (activeSession != null) {
            val absoluteDeadline = activeSession.createdAt.plusSeconds(config.sessionAbsoluteTimeoutSeconds)
            if (Instant.now().isAfter(absoluteDeadline)) {
                sessionRepository.deleteByTokenHash(tokenHash)
                return SessionLookup.Expired
            }
            val user = userRepository.findById(activeSession.userId)
            if (user != null && user.enabled) {
                sessionRepository.updateExpiresAt(tokenHash, Instant.now().plusSeconds(config.sessionTimeoutSeconds))
                activityUpdater?.record(user.id) ?: userRepository.updateLastActivity(user.id)
                return SessionLookup.Active(user)
            }
            return SessionLookup.NotFound
        }
        val expiredSession = sessionRepository.findByTokenHashIncludingExpired(tokenHash)
        if (expiredSession != null) {
            return SessionLookup.Expired
        }
        return SessionLookup.NotFound
    }

    fun deleteSession(rawToken: String) {
        sessionRepository.deleteByTokenHash(TokenHashing.hash(rawToken))
    }

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SESSION_TOKEN_HEX_LENGTH = 48
    }
}
```

- [ ] **Step 1: Create the file above**
- [ ] **Step 2: Compile platform-security**

Run: `mvn -pl platform-security -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SessionService.kt
git commit -m "feat(security): extract SessionService from SecurityService"
```

---

### Task 2: Create AuthService

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuthService.kt`

Extract authentication and registration methods from SecurityService.

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuthResult
import io.github.rygel.outerstellar.platform.model.RegistrationDisabledException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val config: SecurityConfig,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    fun authenticate(username: String, password: String): AuthResult? {
        val user =
            userRepository.findByUsername(username)
                ?: run {
                    logger.warn("Authentication failed: User ${sanitize(username)} not found")
                    audit("AUTHENTICATION_FAILED", detail = "User not found", targetUsername = sanitize(username))
                    return null
                }
        if (!user.enabled) {
            logger.warn("Authentication failed: User ${sanitize(username)} is disabled")
            audit("AUTHENTICATION_FAILED", actor = user, detail = "Account disabled")
            return null
        }
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            logger.warn("Authentication failed: User ${sanitize(username)} is locked until $lockedUntil")
            audit("AUTHENTICATION_FAILED", actor = user, detail = "Account locked until $lockedUntil")
            return null
        }
        if (passwordEncoder.matches(password, user.passwordHash)) {
            if (user.failedLoginAttempts > 0) {
                userRepository.resetFailedLoginAttempts(user.id)
            }
            logger.info("Authentication successful for user ${sanitize(username)}")
            if (user.totpEnabled) {
                return AuthResult.TotpRequired("__partial__")
            }
            return AuthResult.Authenticated(user)
        }
        val attempts = userRepository.incrementFailedLoginAttempts(user.id)
        logger.warn("Authentication failed: Invalid password for user ${sanitize(username)} (attempt $attempts)")
        audit("AUTHENTICATION_FAILED", actor = user, detail = "Invalid password")
        if (attempts >= config.maxFailedLoginAttempts) {
            val until = Instant.now().plusSeconds(config.lockoutDurationSeconds)
            userRepository.updateLockedUntil(user.id, until)
            logger.warn("User ${sanitize(username)} locked until $until after $attempts failed attempts")
        }
        return null
    }

    fun register(username: String, password: String): User {
        if (!config.registrationEnabled) {
            throw RegistrationDisabledException()
        }
        require(username.isNotBlank()) { "Username is required" }
        val normalized = password.trim()
        validatePassword(normalized)?.let { throw WeakPasswordException(it) }
        if (userRepository.findByUsername(username) != null) throw UsernameAlreadyExistsException(username)

        val created =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = username,
                passwordHash = passwordEncoder.encode(normalized),
                role = UserRole.USER,
            )
        userRepository.save(created)
        logger.info("Registration successful for user {}", sanitize(username))
        audit("USER_REGISTERED", actor = created)
        return created
    }

    private fun audit(
        action: String,
        actor: User? = null,
        target: User? = null,
        detail: String? = null,
        targetUsername: String? = null,
    ) {
        auditRepository?.log(
            io.github.rygel.outerstellar.platform.model.AuditEntry(
                actorId = actor?.id?.toString(),
                actorUsername = actor?.username,
                targetId = target?.id?.toString(),
                targetUsername = targetUsername ?: target?.username,
                action = action,
                detail = detail,
            )
        )
    }

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

    companion object {
        private const val MAX_LOG_ID_LENGTH = 80
    }
}
```

**IMPORTANT NOTE on TotpRequired:** The original `authenticate()` in SecurityService returns `AuthResult.TotpRequired(generatePartialAuthToken(user.id))` which generates a partial auth token stored in a Caffeine cache. AuthService does NOT have access to that cache or the `generatePartialAuthToken` method (those stay in SecurityService for TOTP flow).

There are two options:
1. AuthService returns `AuthResult.TotpRequired("__placeholder__")` and SecurityService wraps the call to substitute the real token
2. AuthService receives a `partialTokenGenerator: (UUID) -> String` function parameter

The simplest approach: **Keep `authenticate()` in SecurityService** for now, only extract `register()` to AuthService. This avoids the TOTP coupling issue entirely.

**REVISED AuthService** — only contains `register()`:

```kotlin
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val config: SecurityConfig,
    private val auditRepository: AuditRepository? = null,
) {
    fun register(username: String, password: String): User { ... }
}
```

And `authenticate()` stays in SecurityService since it's tightly coupled to TOTP via the partial auth token.

- [ ] **Step 1: Create the file with only `register()` and `authenticate()` (authenticate needs partial token — keep it in SecurityService for now, move in future PR)**
- [ ] **Step 2: Compile**
- [ ] **Step 3: Commit**

---

### Task 3: Create UserAdminService

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/UserAdminService.kt`

Extract user administration and audit log methods.

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class UserAdminService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(UserAdminService::class.java)

    fun listUsers(): List<UserSummary> =
        userRepository.findAll().map { it.toSummary() }

    fun listUsers(limit: Int, offset: Int): List<UserSummary> =
        userRepository.findPage(limit.coerceIn(1, MAX_PAGE_LIMIT), offset).map { it.toSummary() }

    fun countUsers(): Long = userRepository.countAll()

    fun findUserSummary(id: UUID): UserSummary? = userRepository.findById(id)?.toSummary()

    fun setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own enabled status")
        }
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Only administrators can enable/disable accounts")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateEnabled(targetId, enabled)
        logger.info("User {} enabled set to {} by admin {}", sanitize(target.username), enabled, adminId)
        val action = if (enabled) "USER_ENABLED" else "USER_DISABLED"
        audit(action, actor = admin, target = target)
    }

    fun unlockAccount(adminId: UUID, targetId: UUID) {
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Admin access required to unlock accounts")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.resetFailedLoginAttempts(targetId)
        logger.info("User {} unlocked by admin {}", sanitize(target.username), sanitize(admin.username))
        audit("USER_UNLOCKED", actor = admin, target = target)
    }

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Only administrators can change user roles")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateRole(targetId, role)
        logger.info("User {} role set to {} by admin {}", sanitize(target.username), role, adminId)
        audit("USER_ROLE_CHANGED", actor = admin, target = target, detail = "from ${target.role} to $role")
    }

    fun countAuditEntries(): Long = auditRepository?.countAll() ?: 0L

    fun getAuditLog(limit: Int = 50): List<AuditEntry> =
        auditRepository?.findRecent(limit) ?: emptyList()

    fun getAuditLog(limit: Int, offset: Int): List<AuditEntry> =
        auditRepository?.findPage(limit, offset) ?: emptyList()

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

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

    private fun User.toSummary() =
        UserSummary(
            id = id.toString(),
            username = username,
            email = email,
            role = role,
            enabled = enabled,
            failedLoginAttempts = failedLoginAttempts,
            lockedUntil = lockedUntil,
        )

    companion object {
        private const val MAX_PAGE_LIMIT = 1000
        private const val MAX_LOG_ID_LENGTH = 80
    }
}
```

- [ ] **Step 1: Create the file**
- [ ] **Step 2: Compile**
- [ ] **Step 3: Commit**

---

### Task 4: Slim down SecurityService

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

Remove the methods now in SessionService, AuthService (register only), and UserAdminService. Add `SessionService` as a constructor dependency. Keep `authenticate()` in SecurityService (TOTP coupling). Remove dead code: `deleteExpiredSessions()`, `updatePreferences()`.

Changes:
1. Add `private val sessionService: SessionService` constructor parameter
2. Replace `createSession()`, `lookupSession()`, `deleteSession()` bodies with delegation to `sessionService`
3. Remove `register()` — callers use `AuthService.register()` directly
4. Remove `listUsers()`, `listUsers(limit,offset)`, `countUsers()`, `findUserSummary()`, `setUserEnabled()`, `unlockAccount()`, `setUserRole()`, `countAuditEntries()`, `getAuditLog()` overloads
5. Remove `deleteExpiredSessions()`, `updatePreferences()`
6. Remove `toSummary()` extension (moved to UserAdminService)
7. Remove `generateRandomHex()` (moved to SessionService)
8. Remove duplicate `random` field (keep only `secureRandom`)
9. Update companion: remove `SESSION_TOKEN_HEX_LENGTH`, `MAX_PAGE_LIMIT`

**IMPORTANT:** Do NOT remove the delegation methods for `createSession`/`lookupSession`/`deleteSession` yet — too many callers depend on them. Instead, make them delegate to `sessionService`:

```kotlin
fun createSession(userId: UUID): String = sessionService.createSession(userId)
fun lookupSession(rawToken: String): SessionLookup = sessionService.lookupSession(rawToken)
fun deleteSession(rawToken: String) = sessionService.deleteSession(rawToken)
```

This keeps the SecurityService API stable while callers migrate incrementally.

- [ ] **Step 1: Edit SecurityService with the changes above**
- [ ] **Step 2: Compile**
- [ ] **Step 3: Commit**

---

### Task 5: Update SecurityComponents wiring

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityComponents.kt`
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuthRealm.kt`

1. Add `sessionService`, `authService`, `userAdminService` to `SecurityComponents` data class
2. Create these services in `createSecurityComponents()`
3. Pass `sessionService` to `SecurityService` constructor
4. Update `SessionRealm` to take `SessionService` instead of `SecurityService`
5. Update `createSecurityComponents()` to pass `SessionService` to `SessionRealm`

- [ ] **Step 1: Update SecurityComponents and AuthRealm**
- [ ] **Step 2: Compile**
- [ ] **Step 3: Commit**

---

### Task 6: Update platform-web consumers (ServerComponents, Filters, WebContext, App.kt)

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/ServerComponents.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`

1. `ServerComponents` — extract `sessionService`, `authService`, `userAdminService` from `SecurityComponents` and pass to route constructors
2. `Filters.kt` — pass `SessionService` (for session lookup in stateFilter)
3. `WebContext.kt` — take `SessionService` instead of `SecurityService` for session lookup
4. `App.kt` — pass `AuthService`, `UserAdminService`, `SessionService` to route constructors; update logout route to use `SessionService`

Read each file to understand exact constructor signatures and update accordingly.

- [ ] **Step 1: Update all four files**
- [ ] **Step 2: Compile platform-web**
- [ ] **Step 3: Commit**

---

### Task 7: Update route classes to use new services

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthApi.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPRoutes.kt` (if exists)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPApiRoutes.kt` (if exists)
- Modify: All route files that call SecurityService methods now in new services

Read each route file to find SecurityService usage:
- `AuthRoutes` — uses `authenticate`, `register` (→ AuthService), `createSession`, `deleteSession` (→ SessionService)
- `AuthApi` — same as AuthRoutes
- `UserAdminRoutes` — uses `listUsers`, `findUserSummary`, `setUserEnabled`, etc. (→ UserAdminService)
- `UserAdminApi` — uses `listUsers`, `setUserEnabled`, `setUserRole` (→ UserAdminService)
- `OAuthRoutes` — uses `findOrCreateOAuthUser` (stays on SecurityService), `createSession` (→ SessionService)
- `TOTPRoutes`/`TOTPApiRoutes` — uses `authenticate` (stays on SecurityService), `verifyTotp` (stays)
- `AdminPageFactory` — uses `countUsers`, `listUsers`, `countAuditEntries`, `getAuditLog` (→ UserAdminService), `listApiKeys` (stays on SecurityService)

Each route class constructor gains the specific service it needs. Some may need both the new service AND SecurityService for remaining methods.

- [ ] **Step 1: Update all route files**
- [ ] **Step 2: Compile**
- [ ] **Step 3: Commit**

---

### Task 8: Update WebTest and fix compilation errors

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`
- Modify: Any test files that create SecurityService directly

1. `WebTest.createSecurityService()` — add `SessionService` parameter
2. `WebTest.buildApp()` — pass new services through
3. Search for all test files that reference the removed methods and update them

- [ ] **Step 1: Update WebTest and fix all test compilation errors**
- [ ] **Step 2: Compile all test code**

Run: `mvn -pl platform-web -am test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

---

### Task 9: Run tests and fix failures

- [ ] **Step 1: Run full non-desktop reactor**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed`
Expected: BUILD SUCCESS

- [ ] **Step 2: Fix any test failures by updating callers**
- [ ] **Step 3: Commit fixes if needed**
