# AuthService + AccountService Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract `AuthService` (authentication + TOTP) and `AccountService` (account management) from `SecurityService`, shrinking it to API key + OAuth + password reset delegation.

**Architecture:** Three services replace the monolithic SecurityService. AuthService owns authenticate/register/TOTP methods plus partialAuthStore. AccountService owns changePassword/updateProfile/deleteAccount. SecurityService keeps API key, OAuth, and password reset delegation. SecurityComponents wires all three.

**Tech Stack:** Kotlin, http4k, JDBI, Caffeine cache

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-security/.../security/AuthService.kt` | Authentication + TOTP flow |
| Create | `platform-security/.../security/AccountService.kt` | Account self-service |
| Modify | `platform-security/.../security/SecurityService.kt` | Remove extracted methods, keep delegation |
| Modify | `platform-security/.../security/SecurityComponents.kt` | Wire AuthService + AccountService |
| Modify | `platform-web/.../web/AuthRoutes.kt` | Use authService + accountService + securityService |
| Modify | `platform-web/.../web/AuthApi.kt` | Use authService + accountService + securityService |
| Modify | `platform-web/.../web/TOTPRoutes.kt` | Use authService instead of securityService |
| Modify | `platform-web/.../web/TOTPApiRoutes.kt` | Use authService instead of securityService |
| Modify | `platform-web/.../App.kt` | Wire new services, add to AppContext |
| Modify | `platform-web/.../ServerComponents.kt` | Pass new services |
| Move tests | `platform-security/.../AuthServiceTest.kt` | Tests for auth methods |
| Move tests | `platform-security/.../AccountServiceTest.kt` | Tests for account methods |
| Modify | `platform-security/.../SecurityServiceTest.kt` | Keep only API key tests |
| Modify | `platform-security/.../SecurityServiceTotpTest.kt` | Move to AuthServiceTotpTest |

---

### Task 1: Create AuthService

Extract authentication + TOTP methods from SecurityService into a new AuthService class.

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuthService.kt`

- [ ] **Step 1: Create AuthService.kt**

Extract from SecurityService.kt: the `authenticate`, `register`, `verifyTotp`, `enableTotp`, `disableTotp` methods, plus `partialAuthStore`, `totpService`, `secureRandom`, `sanitize()`, `PartialAuth` data class, `MAX_LOG_ID_LENGTH`, and the `audit()` helper.

```kotlin
package io.github.rygel.outerstellar.platform.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.RegistrationDisabledException
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

data class PartialAuth(val userId: UUID, val attemptCount: Int = 0)

class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val config: SecurityConfig = SecurityConfig(),
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = SecureRandom()
    private val totpService = TOTPService()
    private val partialAuthStore: Cache<String, PartialAuth> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

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
                return AuthResult.TotpRequired(generatePartialAuthToken(user.id))
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

    fun verifyTotp(partialToken: String, code: String, sessionService: SessionService): TotpVerifyResponse {
        val partial =
            partialAuthStore.asMap().compute(partialToken) { _, existing ->
                if (existing == null) return@compute null
                if (existing.attemptCount >= 4) null else existing.copy(attemptCount = existing.attemptCount + 1)
            } ?: return TotpVerifyResponse("expired")

        val user = userRepository.findById(partial.userId) ?: return TotpVerifyResponse("expired")
        val secret = user.totpSecret ?: return TotpVerifyResponse("expired")

        if (totpService.verifyCode(secret, code)) {
            partialAuthStore.invalidate(partialToken)
            val token = sessionService.createSession(user.id)
            return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
        }

        val backupCodes = user.totpBackupCodes
        if (backupCodes != null) {
            val updatedCodes = totpService.verifyBackupCode(code, backupCodes)
            if (updatedCodes != null) {
                userRepository.updateTotpSecret(user.id, user.totpSecret, updatedCodes.ifEmpty { null })
                partialAuthStore.invalidate(partialToken)
                val token = sessionService.createSession(user.id)
                return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
            }
        }

        return TotpVerifyResponse("invalid_code")
    }

    fun enableTotp(userId: UUID, secret: String, backupCodes: String) {
        userRepository.updateTotpSecret(userId, secret, backupCodes)
        userRepository.enableTotp(userId)
    }

    fun disableTotp(userId: UUID) {
        userRepository.updateTotpSecret(userId, null, null)
        userRepository.disableTotp(userId)
    }

    private fun generatePartialAuthToken(userId: UUID): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = "pt_" + bytes.joinToString("") { "%02x".format(it) }
        partialAuthStore.put(token, PartialAuth(userId = userId))
        return token
    }

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

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
            ),
        )
    }

    companion object {
        private const val MAX_LOG_ID_LENGTH = 80
    }
}
```

Note: `validatePassword` is a top-level function in `PasswordValidation.kt` in platform-security — it's already available without import.

- [ ] **Step 2: Compile to verify AuthService**

```powershell
mvn clean compile -T4 -pl platform-security "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS (AuthService compiles, no callers yet)

---

### Task 2: Create AccountService

Extract account management methods from SecurityService.

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AccountService.kt`

- [ ] **Step 1: Create AccountService.kt**

Extract from SecurityService.kt: `changePassword`, `updateProfile`, `updateNotificationPreferences`, `deleteAccount` (both overloads), plus `sanitize()`, `audit()`, `EMAIL_REGEX`, `MAX_USERNAME_LENGTH`, `MAX_LOG_ID_LENGTH`.

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.UrlValidator
import java.util.UUID
import org.slf4j.LoggerFactory

class AccountService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val sessionRepository: SessionRepository? = null,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(AccountService::class.java)

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())

        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw WeakPasswordException("Current password is incorrect")
        }
        val normalized = newPassword.trim()
        validatePassword(normalized)?.let { throw WeakPasswordException(it) }

        val updated = user.copy(passwordHash = passwordEncoder.encode(normalized))
        userRepository.save(updated)
        sessionRepository?.deleteByUserId(userId)
        logger.info("Password changed for user {}", sanitize(user.username))
        audit("PASSWORD_CHANGED", actor = user)
    }

    fun updateProfile(userId: UUID, newEmail: String, newUsername: String? = null, newAvatarUrl: String? = null) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (newEmail != user.email) {
            if (!EMAIL_REGEX.matches(newEmail)) {
                throw IllegalArgumentException("Invalid email address: $newEmail")
            }
            val existing = userRepository.findByEmail(newEmail)
            if (existing != null && existing.id != userId) {
                throw UsernameAlreadyExistsException(newEmail)
            }
        }
        val resolvedUsername = newUsername ?: user.username
        if (resolvedUsername != user.username) {
            require(resolvedUsername.isNotBlank()) { "Username cannot be blank" }
            require(resolvedUsername.length <= MAX_USERNAME_LENGTH) {
                "Username cannot exceed $MAX_USERNAME_LENGTH characters"
            }
            if (userRepository.findByUsername(resolvedUsername) != null) {
                throw UsernameAlreadyExistsException(resolvedUsername)
            }
        }
        val sanitizedUrl = newAvatarUrl?.takeIf { it.isNotBlank() }
        if (sanitizedUrl != null && sanitizedUrl != user.avatarUrl) {
            UrlValidator.validate(sanitizedUrl)
        }
        val updated = user.copy(email = newEmail, username = resolvedUsername, avatarUrl = sanitizedUrl)
        userRepository.save(updated)
        logger.info("Profile updated for user {}", sanitize(updated.username))
    }

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        userRepository.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
        logger.info("Notification preferences updated for user {}", sanitize(user.username))
        audit("NOTIFICATION_PREFERENCES_UPDATED", actor = user)
    }

    fun deleteAccount(userId: UUID, currentPassword: String) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw WeakPasswordException("Current password is incorrect")
        }
        deleteAccountInternal(userId)
    }

    private fun deleteAccountInternal(userId: UUID) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (user.role == UserRole.ADMIN) {
            val adminCount = userRepository.countByRole(UserRole.ADMIN)
            if (adminCount <= 1) {
                throw InsufficientPermissionException("Cannot delete the only remaining admin account")
            }
        }
        userRepository.deleteById(userId)
        logger.info("Account deleted for user {}", sanitize(user.username))
        audit("ACCOUNT_DELETED", actor = user)
    }

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

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
            ),
        )
    }

    companion object {
        private const val MAX_USERNAME_LENGTH = 50
        private const val MAX_LOG_ID_LENGTH = 80
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
```

Note: The original SecurityService had a `deleteAccount(userId: UUID)` private overload and a `deleteAccount(userId: UUID, currentPassword: String)` public overload. AccountService uses `deleteAccountInternal` for the private one.

- [ ] **Step 2: Compile to verify AccountService**

```powershell
mvn clean compile -T4 -pl platform-security "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

---

### Task 3: Shrink SecurityService

Remove extracted methods from SecurityService, keeping only API key + OAuth + password reset delegation.

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

- [ ] **Step 1: Rewrite SecurityService.kt**

Remove: `authenticate`, `register`, `verifyTotp`, `enableTotp`, `disableTotp`, `changePassword`, `updateProfile`, `updateNotificationPreferences`, `deleteAccount` (both overloads), `generatePartialAuthToken`, `sanitize`, `audit`, `partialAuthStore`, `totpService`, `secureRandom`, `PartialAuth` data class, `EMAIL_REGEX`, `MAX_USERNAME_LENGTH`, `MAX_LOG_ID_LENGTH`.

Remove unused imports: `Cache`, `Caffeine`, `TotpVerifyResponse`, `RegistrationDisabledException`, `UserNotFoundException`, `WeakPasswordException`, `InsufficientPermissionException`, `SecureRandom`, `Instant`, `UUID`, `TimeUnit`.

Keep: `createApiKey`, `authenticateApiKey`, `listApiKeys`, `deleteApiKey`, `findOrCreateOAuthUser`, `requestPasswordReset`, `resetPassword`, lazy services, `validatePassword` usage (if any).

The class becomes:

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val resetRepository: PasswordResetRepository? = null,
    private val apiKeyRepository: ApiKeyRepository? = null,
    private val emailService: io.github.rygel.outerstellar.platform.service.EmailService? = null,
    private val oauthRepository: OAuthRepository? = null,
    private val config: SecurityConfig = SecurityConfig(),
    private val sessionRepository: SessionRepository? = null,
    private val activityUpdater: AsyncActivityUpdater? = null,
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)

    private val passwordResetService by lazy {
        PasswordResetService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            resetRepository = resetRepository,
            auditRepository = auditRepository,
            sessionRepository = sessionRepository,
            emailService = emailService,
            appBaseUrl = config.appBaseUrl,
        )
    }

    private val apiKeyService by lazy {
        ApiKeyService(userRepository = userRepository, apiKeyRepository = apiKeyRepository)
    }

    private val oauthService by lazy {
        OAuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            oauthRepository = oauthRepository,
            auditRepository = auditRepository,
        )
    }

    fun requestPasswordReset(email: String): String? = passwordResetService.requestPasswordReset(email)

    fun resetPassword(token: String, newPassword: String) = passwordResetService.resetPassword(token, newPassword)

    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse {
        val result = apiKeyService.createApiKey(userId, name)
        val user = userRepository.findById(userId)
        audit("API_KEY_CREATED", actor = user, detail = "name=$name")
        return result
    }

    fun authenticateApiKey(rawKey: String): User? = apiKeyService.authenticateApiKey(rawKey)

    fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyService.deleteApiKey(userId, keyId)
        val user = userRepository.findById(userId)
        audit("API_KEY_DELETED", actor = user, detail = "keyId=$keyId")
    }

    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)

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
            ),
        )
    }
}
```

IMPORTANT: Also remove the `validatePassword` import if SecurityService no longer uses it directly. Check if `resetPassword` uses `validatePassword` through `PasswordResetService` — if so, it's not needed here.

Also remove the `companion object` with `MAX_USERNAME_LENGTH`, `MAX_LOG_ID_LENGTH`, `EMAIL_REGEX` if they're no longer in SecurityService.

- [ ] **Step 2: Move PartialAuth out of SecurityService**

`PartialAuth` was a top-level data class in SecurityService.kt. It's now defined in AuthService.kt. Make sure SecurityService.kt no longer defines it. If any other file in platform-security references `PartialAuth`, check they import from AuthService.

- [ ] **Step 3: Compile**

```powershell
mvn clean compile -T4 -pl platform-security "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

---

### Task 4: Update SecurityComponents wiring

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityComponents.kt`

- [ ] **Step 1: Add authService and accountService to SecurityComponents**

Add `val authService: AuthService` and `val accountService: AccountService` to the `SecurityComponents` data class.

Update `createSecurityComponents` to create and wire them:

```kotlin
class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val securityService: SecurityService,
    val authService: AuthService,
    val accountService: AccountService,
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)
```

In `createSecurityComponents`, create authService and accountService:

```kotlin
val authService =
    AuthService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        auditRepository = auditRepository,
        config = securityConfig,
    )
val accountService =
    AccountService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        sessionRepository = sessionRepository,
        auditRepository = auditRepository,
    )
```

Return them in `SecurityComponents(...)`.

- [ ] **Step 2: Compile**

```powershell
mvn clean compile -T4 -pl platform-security,platform-web -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Note: platform-web will fail to compile because route constructors still take `securityService` but now call methods that don't exist on it. That's expected — we fix this in Task 5.

---

### Task 5: Update route constructors in platform-web

Update all route files to accept the specific services they need.

**Files:**
- Modify: `platform-web/src/main/kotlin/.../web/AuthRoutes.kt`
- Modify: `platform-web/src/main/kotlin/.../web/AuthApi.kt`
- Modify: `platform-web/src/main/kotlin/.../web/TOTPRoutes.kt`
- Modify: `platform-web/src/main/kotlin/.../web/TOTPApiRoutes.kt`

- [ ] **Step 1: Update AuthRoutes.kt**

AuthRoutes currently takes `securityService: SecurityService`. It calls:
- `securityService.authenticate()` → `authService.authenticate()`
- `securityService.register()` → `authService.register()`
- `securityService.changePassword()` → `accountService.changePassword()`
- `securityService.updateProfile()` → `accountService.updateProfile()`
- `securityService.updateNotificationPreferences()` → `accountService.updateNotificationPreferences()`
- `securityService.deleteAccount()` → `accountService.deleteAccount()`
- `securityService.requestPasswordReset()` → `securityService.requestPasswordReset()` (stays)
- `securityService.resetPassword()` → `securityService.resetPassword()` (stays)
- `securityService.createApiKey()` → `securityService.createApiKey()` (stays)
- `securityService.deleteApiKey()` → `securityService.deleteApiKey()` (stays)

Change constructor to:
```kotlin
class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val authService: AuthService,
    private val accountService: AccountService,
    private val securityService: SecurityService,
    private val sessionService: SessionService,
    private val sessionCookieSecure: Boolean,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val appConfig: AppConfig,
) : ServerRoutes
```

Then replace method calls in the body:
- `securityService.authenticate(` → `authService.authenticate(`
- `securityService.register(` → `authService.register(`
- `securityService.changePassword(` → `accountService.changePassword(`
- `securityService.updateProfile(` → `accountService.updateProfile(`
- `securityService.updateNotificationPreferences(` → `accountService.updateNotificationPreferences(`
- `securityService.deleteAccount(` → `accountService.deleteAccount(`
- `securityService.requestPasswordReset(` stays as `securityService.requestPasswordReset(`
- `securityService.resetPassword(` stays as `securityService.resetPassword(`
- `securityService.createApiKey(` stays as `securityService.createApiKey(`
- `securityService.deleteApiKey(` stays as `securityService.deleteApiKey(`

Add imports:
```kotlin
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.AccountService
```

- [ ] **Step 2: Update AuthApi.kt**

AuthApi currently takes `securityService: SecurityService`. It calls:
- `securityService.authenticate()` → `authService.authenticate()`
- `securityService.register()` → `authService.register()`
- `securityService.changePassword()` → `accountService.changePassword()`
- `securityService.updateProfile()` → `accountService.updateProfile()`
- `securityService.updateNotificationPreferences()` → `accountService.updateNotificationPreferences()`
- `securityService.deleteAccount()` → `accountService.deleteAccount()`
- `securityService.requestPasswordReset()` → `securityService.requestPasswordReset()` (stays)
- `securityService.resetPassword()` → `securityService.resetPassword()` (stays)
- `securityService.createApiKey()` → `securityService.createApiKey()` (stays)
- `securityService.listApiKeys()` → `securityService.listApiKeys()` (stays)
- `securityService.deleteApiKey()` → `securityService.deleteApiKey()` (stays)

Change constructor to:
```kotlin
class AuthApi(
    private val authService: AuthService,
    private val accountService: AccountService,
    private val securityService: SecurityService,
    private val sessionService: SessionService,
    private val appConfig: AppConfig,
) : ServerRoutes
```

Replace method calls the same way as AuthRoutes.

- [ ] **Step 3: Update TOTPRoutes.kt**

TOTPRoutes currently takes `securityService: SecurityService`. It calls:
- `securityService.authenticate()` → `authService.authenticate()`
- `securityService.verifyTotp()` → `authService.verifyTotp()`
- `securityService.enableTotp()` → `authService.enableTotp()`
- `securityService.disableTotp()` → `authService.disableTotp()`

Change constructor to:
```kotlin
class TOTPRoutes(
    private val authService: AuthService,
    private val renderer: TemplateRenderer,
    private val sessionCookieSecure: Boolean,
    private val totpService: TOTPService,
    private val sessionService: SessionService,
)
```

Replace all `securityService.authenticate/verifyTotp/enableTotp/disableTotp` with `authService.authenticate/verifyTotp/enableTotp/disableTotp`.

Remove `SecurityService` import, add `AuthService` import.

- [ ] **Step 4: Update TOTPApiRoutes.kt**

Same pattern as TOTPRoutes. Change constructor to:
```kotlin
class TOTPApiRoutes(
    private val authService: AuthService,
    private val totpService: TOTPService,
    private val sessionService: SessionService,
)
```

Replace all `securityService.` calls with `authService.`.

---

### Task 6: Update App.kt wiring

**Files:**
- Modify: `platform-web/src/main/kotlin/.../App.kt`

- [ ] **Step 1: Add authService and accountService to AppContext**

Add fields to `AppContext`:
```kotlin
val authService: AuthService,
val accountService: AccountService,
```

Add imports:
```kotlin
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.AccountService
```

- [ ] **Step 2: Add authService and accountService parameters to app() function**

Add to `app()` parameters:
```kotlin
authService: AuthService,
accountService: AccountService,
```

Pass them to `AppContext(...)`.

- [ ] **Step 3: Update route construction in buildUiRoutes**

Pass `authService` and `accountService` to `AuthRoutes(...)`:
```kotlin
routes += AuthRoutes(pageFactory, jteRenderer, authService, accountService, securityService, sessionCookieSecure, analytics, ctx.config).routes
```

- [ ] **Step 4: Update route construction in buildApiRoutes**

Pass `authService` and `accountService` to `AuthApi(...)`:
```kotlin
routes += AuthApi(authService, accountService, securityService, ctx.sessionService, ctx.config).routes
```

- [ ] **Step 5: Update TOTP route construction**

Pass `authService` instead of `securityService` to TOTPRoutes and TOTPApiRoutes:
```kotlin
routes += TOTPRoutes(authService, jteRenderer, sessionCookieSecure, ctx.totpService, ctx.sessionService).routes
routes += TOTPApiRoutes(authService, ctx.totpService, ctx.sessionService).routes
```

- [ ] **Step 6: Compile platform-web**

```powershell
mvn clean compile -T4 -pl platform-web -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

---

### Task 7: Update ServerComponents wiring

**Files:**
- Modify: `platform-web/src/main/kotlin/.../ServerComponents.kt`

- [ ] **Step 1: Pass authService and accountService through**

In `createServerComponents`, `security` is a `SecurityComponents` which now has `authService` and `accountService` fields.

Pass them to `createWebComponents(...)` and `app(...)`:

In `createWebComponents(...)`: add `authService: AuthService` and `accountService: AccountService` parameters if needed by WebFactory.

In `app(...)`: pass `authService = security.authService, accountService = security.accountService`.

Check `WebFactory.kt` and `WebComponents` to see if `securityService` is passed through there. If so, add `authService` and `accountService` alongside it.

- [ ] **Step 2: Compile**

```powershell
mvn clean compile -T4 -pl platform-web -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

---

### Task 8: Migrate tests

**Files:**
- Create: `platform-security/src/test/kotlin/.../security/AuthServiceTest.kt`
- Create: `platform-security/src/test/kotlin/.../security/AuthServiceTotpTest.kt`
- Create: `platform-security/src/test/kotlin/.../security/AccountServiceTest.kt`
- Modify: `platform-security/src/test/kotlin/.../security/SecurityServiceTest.kt`
- Delete: `platform-security/src/test/kotlin/.../security/SecurityServiceTotpTest.kt`

- [ ] **Step 1: Create AuthServiceTest.kt**

Move the following tests from `SecurityServiceTest.kt` to `AuthServiceTest.kt`:
- All `authenticate` tests (13 tests)
- All `register` tests (6 tests)

Change the service constructor from `SecurityService(...)` to `AuthService(...)`.

The test class structure should follow the existing pattern in `SecurityServiceTest.kt` — same `@TestInstance(PER_CLASS)`, same mock setup with `InMemoryUserRepository`, `BCryptPasswordEncoder`, `InMemoryAuditRepository`, `InMemoryApiKeyRepository`.

- [ ] **Step 2: Create AuthServiceTotpTest.kt**

Move the entire contents of `SecurityServiceTotpTest.kt` to `AuthServiceTotpTest.kt`.

Change `SecurityService(...)` to `AuthService(...)`.

Remove the `sessionRepository` parameter from the constructor since `AuthService` doesn't take it.

Delete `SecurityServiceTotpTest.kt`.

- [ ] **Step 3: Create AccountServiceTest.kt**

Move the following tests from `SecurityServiceTest.kt` to `AccountServiceTest.kt`:
- All `changePassword` tests (6 tests)
- All `updateProfile` tests (6 tests)
- All `deleteAccount` tests (4 tests)
- All `updateNotificationPreferences` tests (2 tests)

Change the service constructor from `SecurityService(...)` to `AccountService(...)`.

- [ ] **Step 4: Shrink SecurityServiceTest.kt**

Keep only the API key tests in `SecurityServiceTest.kt`:
- `createApiKey` tests (3 tests)
- `deleteApiKey` test (1 test)
- `authenticateApiKey` tests (4 tests)

- [ ] **Step 5: Update platform-web test files**

Search for test files that call `securityService.authenticate/register/verifyTotp/enableTotp/disableTotp/changePassword/updateProfile/updateNotificationPreferences/deleteAccount` and update to use the appropriate service.

```powershell
Get-ChildItem -Recurse "platform-web/src/test" -Filter "*.kt" | Select-String "securityService\.(authenticate|register|verifyTotp|enableTotp|disableTotp|changePassword|updateProfile|updateNotificationPreferences|deleteAccount)"
```

Update any matches to use `authService` or `accountService`.

Also update `WebTest.kt` if it creates route instances that now need `authService`/`accountService` parameters.

- [ ] **Step 6: Run full tests**

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

Expected: BUILD SUCCESS, all tests pass

---

### Task 9: Commit

- [ ] **Step 1: Commit**

```bash
git add -A && git commit -m "refactor: extract AuthService + AccountService from SecurityService

- AuthService: authenticate, register, verifyTotp, enableTotp, disableTotp
- AccountService: changePassword, updateProfile, updateNotificationPreferences, deleteAccount
- SecurityService shrinks to API key + OAuth + password reset delegation
- SecurityComponents wires all three services
- Route constructors updated to accept specific services
- Tests migrated to AuthServiceTest, AccountServiceTest, AuthServiceTotpTest"
```
