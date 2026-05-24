package io.github.rygel.outerstellar.platform.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.RegistrationDisabledException
import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.UrlValidator
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

data class PartialAuth(val userId: UUID, val attemptCount: Int = 0)

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
    private val totpService: TOTPService = TOTPService(),
    private val sessionService: SessionService? = null,
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)
    private val secureRandom = SecureRandom()
    private val partialAuthStore: Cache<String, PartialAuth> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

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

    // Delegated to PasswordResetService

    fun requestPasswordReset(email: String): String? = passwordResetService.requestPasswordReset(email)

    fun resetPassword(token: String, newPassword: String) = passwordResetService.resetPassword(token, newPassword)

    // Delegated to ApiKeyService

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

    // Delegated to OAuthService

    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User =
        oauthService.findOrCreateOAuthUser(providerName, oauthSubject, email)

    // Session management

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

    private fun deleteAccount(userId: UUID) {
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

    fun deleteAccount(userId: UUID, currentPassword: String) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw WeakPasswordException("Current password is incorrect")
        }
        deleteAccount(userId)
    }

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        userRepository.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
        logger.info("Notification preferences updated for user {}", sanitize(user.username))
        audit("NOTIFICATION_PREFERENCES_UPDATED", actor = user)
    }

    fun createSession(userId: UUID): String =
        (sessionService ?: error("SessionService not configured")).createSession(userId)

    fun lookupSession(rawToken: String): SessionLookup =
        sessionService?.lookupSession(rawToken) ?: SessionLookup.NotFound

    fun deleteSession(rawToken: String) {
        sessionService?.deleteSession(rawToken)
    }

    private fun generatePartialAuthToken(userId: UUID): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = "pt_" + bytes.joinToString("") { "%02x".format(it) }
        partialAuthStore.put(token, PartialAuth(userId = userId))
        return token
    }

    fun verifyTotp(partialToken: String, code: String): TotpVerifyResponse {
        val partial =
            partialAuthStore.asMap().compute(partialToken) { _, existing ->
                if (existing == null) return@compute null
                if (existing.attemptCount >= 4) null else existing.copy(attemptCount = existing.attemptCount + 1)
            } ?: return TotpVerifyResponse("expired")

        val user = userRepository.findById(partial.userId) ?: return TotpVerifyResponse("expired")
        val secret = user.totpSecret ?: return TotpVerifyResponse("expired")

        if (totpService.verifyCode(secret, code)) {
            partialAuthStore.invalidate(partialToken)
            val token = createSession(user.id)
            return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
        }

        val backupCodes = user.totpBackupCodes
        if (backupCodes != null) {
            val updatedCodes = totpService.verifyBackupCode(code, backupCodes)
            if (updatedCodes != null) {
                userRepository.updateTotpSecret(user.id, user.totpSecret, updatedCodes.ifEmpty { null })
                partialAuthStore.invalidate(partialToken)
                val token = createSession(user.id)
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

    companion object {
        private const val MAX_USERNAME_LENGTH = 50
        private const val MAX_LOG_ID_LENGTH = 80
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
