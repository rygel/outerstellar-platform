package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import java.time.Instant
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
    private val appBaseUrl: String = "http://localhost:8080",
    private val sessionRepository: SessionRepository? = null,
    private val sessionTimeoutSeconds: Long = 1800L,
    private val maxFailedLoginAttempts: Int = 10,
    private val lockoutDurationSeconds: Long = 900,
    private val activityUpdater: AsyncActivityUpdater? = null,
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)
    private val secureRandom = java.security.SecureRandom()

    private val passwordResetService by lazy {
        PasswordResetService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            resetRepository = resetRepository,
            auditRepository = auditRepository,
            emailService = emailService,
            appBaseUrl = appBaseUrl,
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

    fun register(username: String, password: String): User {
        require(username.isNotBlank()) { "Username is required" }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw WeakPasswordException("Password must be at least $MIN_PASSWORD_LENGTH characters")
        }
        if (userRepository.findByUsername(username) != null) throw UsernameAlreadyExistsException(username)

        val created =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = username,
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        userRepository.save(created)
        logger.info("Registration successful for user {}", username)
        audit("USER_REGISTERED", actor = created)
        return created
    }

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
        logger.info("Password changed for user {}", user.username)
        audit("PASSWORD_CHANGED", actor = user)
    }

    fun listUsers(): List<UserSummary> {
        return userRepository.findAll().map { it.toSummary() }
    }

    fun listUsers(limit: Int, offset: Int): List<UserSummary> =
        userRepository.findPage(limit.coerceIn(1, MAX_PAGE_LIMIT), offset).map { it.toSummary() }

    fun countUsers(): Long = userRepository.countAll()

    fun setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own enabled status")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateEnabled(targetId, enabled)
        logger.info("User {} enabled set to {} by admin {}", target.username, enabled, adminId)
        val admin = userRepository.findById(adminId)
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
        logger.info("User {} unlocked by admin {}", target.username, admin.username)
        audit("USER_UNLOCKED", actor = admin, target = target)
    }

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateRole(targetId, role)
        logger.info("User {} role set to {} by admin {}", target.username, role, adminId)
        val admin = userRepository.findById(adminId)
        audit("USER_ROLE_CHANGED", actor = admin, target = target, detail = "from ${target.role} to $role")
    }

    fun countAuditEntries(): Long = auditRepository?.countAll() ?: 0L

    fun getAuditLog(limit: Int = 50): List<AuditEntry> {
        return auditRepository?.findRecent(limit) ?: emptyList()
    }

    fun getAuditLog(limit: Int, offset: Int): List<AuditEntry> = auditRepository?.findPage(limit, offset) ?: emptyList()

    // Delegated to PasswordResetService

    fun requestPasswordReset(email: String): String? = passwordResetService.requestPasswordReset(email)

    fun resetPassword(token: String, newPassword: String) = passwordResetService.resetPassword(token, newPassword)

    // Delegated to ApiKeyService

    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse = apiKeyService.createApiKey(userId, name)

    fun authenticateApiKey(rawKey: String): User? = apiKeyService.authenticateApiKey(rawKey)

    fun listApiKeys(userId: UUID): List<ApiKeySummary> = apiKeyService.listApiKeys(userId)

    fun deleteApiKey(userId: UUID, keyId: Long) = apiKeyService.deleteApiKey(userId, keyId)

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
        if (newUsername != null && newUsername != user.username) {
            require(newUsername.isNotBlank()) { "Username cannot be blank" }
            require(newUsername.length <= MAX_USERNAME_LENGTH) {
                "Username cannot exceed $MAX_USERNAME_LENGTH characters"
            }
            if (userRepository.findByUsername(newUsername) != null) {
                throw UsernameAlreadyExistsException(newUsername)
            }
            userRepository.updateUsername(userId, newUsername)
        }
        if (newAvatarUrl != user.avatarUrl) {
            val sanitizedUrl = newAvatarUrl?.takeIf { it.isNotBlank() }
            if (sanitizedUrl != null) {
                if (!sanitizedUrl.startsWith("https://") && !sanitizedUrl.startsWith("http://")) {
                    throw IllegalArgumentException("Avatar URL must use http or https scheme")
                }
                if (sanitizedUrl.length > MAX_URL_LENGTH) {
                    throw IllegalArgumentException("Avatar URL exceeds maximum length of $MAX_URL_LENGTH characters")
                }
                val host =
                    try {
                        java.net.URI(sanitizedUrl).host?.lowercase()
                    } catch (e: Exception) {
                        logger.warn("Failed to parse avatar URL for SSRF check: {}", e.message)
                        null
                    }
                if (host != null && PRIVATE_HOST_PATTERNS.any { it.matches(host) }) {
                    throw IllegalArgumentException("Avatar URL must not point to private or internal addresses")
                }
            }
            userRepository.updateAvatarUrl(userId, sanitizedUrl)
        }
        userRepository.save(user.copy(email = newEmail))
        logger.info("Profile updated for user {}", user.username)
    }

    fun deleteAccount(userId: UUID) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (user.role == UserRole.ADMIN) {
            val adminCount = userRepository.countByRole(UserRole.ADMIN)
            if (adminCount <= 1) {
                throw InsufficientPermissionException("Cannot delete the only remaining admin account")
            }
        }
        userRepository.deleteById(userId)
        logger.info("Account deleted for user {}", user.username)
        audit("ACCOUNT_DELETED", actor = user)
    }

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        userRepository.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
        logger.info("Notification preferences updated for user {}", user.username)
        audit("NOTIFICATION_PREFERENCES_UPDATED", actor = user)
    }

    fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?) {
        userRepository.updatePreferences(userId, language, theme, layout)
    }

    fun createSession(userId: UUID): String {
        val repo = sessionRepository ?: error("SessionRepository is not configured")
        val rawToken = "oss_" + generateRandomHex(SESSION_TOKEN_HEX_LENGTH)
        val tokenHash = hashToken(rawToken)
        val session =
            Session(
                tokenHash = tokenHash,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(sessionTimeoutSeconds),
            )
        repo.save(session)
        logger.info("Session created for user {}", userId)
        return rawToken
    }

    fun lookupSession(rawToken: String): SessionLookup {
        val repo = sessionRepository ?: return SessionLookup.NotFound
        val tokenHash = hashToken(rawToken)
        val activeSession = repo.findByTokenHash(tokenHash)
        if (activeSession != null) {
            val user = userRepository.findById(activeSession.userId)
            if (user != null && user.enabled) {
                // Extend session on activity
                repo.updateExpiresAt(tokenHash, Instant.now().plusSeconds(sessionTimeoutSeconds))
                activityUpdater?.record(user.id) ?: userRepository.updateLastActivity(user.id)
                return SessionLookup.Active(user)
            }
            return SessionLookup.NotFound
        }
        // Check if there's an expired session
        val expiredSession = repo.findByTokenHashIncludingExpired(tokenHash)
        if (expiredSession != null) {
            return SessionLookup.Expired
        }
        return SessionLookup.NotFound
    }

    fun deleteExpiredSessions() {
        sessionRepository?.deleteExpired()
    }

    fun deleteSession(rawToken: String) {
        val repo = sessionRepository ?: return
        repo.deleteByTokenHash(hashToken(rawToken))
    }

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashToken(key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun audit(action: String, actor: User? = null, target: User? = null, detail: String? = null) {
        auditRepository?.log(
            AuditEntry(
                actorId = actor?.id?.toString(),
                actorUsername = actor?.username,
                targetId = target?.id?.toString(),
                targetUsername = target?.username,
                action = action,
                detail = detail,
            )
        )
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_USERNAME_LENGTH = 50
        private const val SESSION_TOKEN_HEX_LENGTH = 48
        private const val MAX_PAGE_LIMIT = 1000
        private const val MAX_URL_LENGTH = 2048
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val PRIVATE_HOST_PATTERNS =
            listOf(
                Regex("^localhost$"),
                Regex("^127\\..*"),
                Regex("^10\\..*"),
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\..*"),
                Regex("^192\\.168\\..*"),
                Regex("^0\\..*"),
                Regex(".*\\.local$"),
                Regex(".*\\.internal$"),
            )
    }
}

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
