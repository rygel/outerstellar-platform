package dev.outerstellar.starter.security

import dev.outerstellar.starter.model.ApiKey
import dev.outerstellar.starter.model.ApiKeySummary
import dev.outerstellar.starter.model.AuditEntry
import dev.outerstellar.starter.model.CreateApiKeyResponse
import dev.outerstellar.starter.model.InsufficientPermissionException
import dev.outerstellar.starter.model.PasswordResetToken
import dev.outerstellar.starter.model.UserNotFoundException
import dev.outerstellar.starter.model.UserSummary
import dev.outerstellar.starter.model.UsernameAlreadyExistsException
import dev.outerstellar.starter.model.WeakPasswordException
import dev.outerstellar.starter.persistence.AuditRepository
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val resetRepository: PasswordResetRepository? = null,
    private val apiKeyRepository: ApiKeyRepository? = null,
    private val emailService: dev.outerstellar.starter.service.EmailService? = null,
    private val oauthRepository: OAuthRepository? = null,
    private val appBaseUrl: String = "http://localhost:8080",
    private val sessionRepository: SessionRepository? = null,
    private val sessionTimeoutSeconds: Long = 1800L,
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)

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
            passwordEncoder.matches(password, user.passwordHash) -> {
                logger.info("Authentication successful for user $username")
                user
            }
            else -> {
                logger.warn("Authentication failed: Invalid password for user $username")
                null
            }
        }
    }

    fun register(username: String, password: String): User {
        require(username.isNotBlank()) { "Username is required" }
        if (password.length < MIN_PASSWORD_LENGTH)
            throw WeakPasswordException("Password must be at least $MIN_PASSWORD_LENGTH characters")
        if (userRepository.findByUsername(username) != null)
            throw UsernameAlreadyExistsException(username)

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
            throw WeakPasswordException(
                "New password must be at least $MIN_PASSWORD_LENGTH characters"
            )
        }

        val updated = user.copy(passwordHash = passwordEncoder.encode(newPassword))
        userRepository.save(updated)
        logger.info("Password changed for user {}", user.username)
        audit("PASSWORD_CHANGED", actor = user)
    }

    fun listUsers(): List<UserSummary> {
        return userRepository.findAll().map { it.toSummary() }
    }

    fun setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own enabled status")
        }
        val target =
            userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateEnabled(targetId, enabled)
        logger.info("User {} enabled set to {} by admin {}", target.username, enabled, adminId)
        val admin = userRepository.findById(adminId)
        val action = if (enabled) "USER_ENABLED" else "USER_DISABLED"
        audit(action, actor = admin, target = target)
    }

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val target =
            userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateRole(targetId, role)
        logger.info("User {} role set to {} by admin {}", target.username, role, adminId)
        val admin = userRepository.findById(adminId)
        audit(
            "USER_ROLE_CHANGED",
            actor = admin,
            target = target,
            detail = "from ${target.role} to $role",
        )
    }

    fun requestPasswordReset(email: String): String? {
        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.info("Password reset requested for unknown email {}", email)
            return null
        }

        val tokenValue = UUID.randomUUID().toString()
        val resetToken =
            PasswordResetToken(
                userId = user.id,
                token = tokenValue,
                expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS),
            )
        resetRepository?.save(resetToken)
        logger.info("Password reset token generated for user {}", user.username)
        val resetLink = "$appBaseUrl/auth/reset?token=$tokenValue"
        emailService?.send(
            to = user.email,
            subject = "Password Reset Request",
            body =
                "Use this link to reset your password:\n$resetLink\n\nThis link expires in 1 hour.",
        )
        audit("PASSWORD_RESET_REQUESTED", actor = user)
        return tokenValue
    }

    fun resetPassword(token: String, newPassword: String) {
        val resetToken =
            resetRepository?.findByToken(token)
                ?: throw IllegalArgumentException("Invalid reset token")

        if (resetToken.used) {
            throw IllegalArgumentException("Reset token has already been used")
        }
        if (resetToken.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Reset token has expired")
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            throw WeakPasswordException(
                "New password must be at least $MIN_PASSWORD_LENGTH characters"
            )
        }

        val user =
            userRepository.findById(resetToken.userId)
                ?: throw UserNotFoundException(resetToken.userId.toString())

        val updated = user.copy(passwordHash = passwordEncoder.encode(newPassword))
        userRepository.save(updated)
        resetRepository.markUsed(token)
        logger.info("Password reset completed for user {}", user.username)
        audit("PASSWORD_RESET_COMPLETED", actor = user)
    }

    fun getAuditLog(limit: Int = 50): List<AuditEntry> {
        return auditRepository?.findRecent(limit) ?: emptyList()
    }

    fun updateProfile(
        userId: UUID,
        newEmail: String,
        newUsername: String? = null,
        newAvatarUrl: String? = null,
    ) {
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
            userRepository.updateAvatarUrl(userId, newAvatarUrl?.takeIf { it.isNotBlank() })
        }
        userRepository.save(user.copy(email = newEmail))
        logger.info("Profile updated for user {}", user.username)
    }

    fun deleteAccount(userId: UUID) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (user.role == UserRole.ADMIN) {
            val adminCount = userRepository.findAll().count { it.role == UserRole.ADMIN }
            if (adminCount <= 1) {
                throw InsufficientPermissionException(
                    "Cannot delete the only remaining admin account"
                )
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

    fun createApiKey(userId: UUID, name: String): CreateApiKeyResponse {
        require(name.isNotBlank()) { "API key name is required" }
        val rawKey = "osk_" + generateRandomHex(API_KEY_HEX_LENGTH)
        val keyPrefix = rawKey.take(API_KEY_PREFIX_LENGTH)
        val keyHash = hashToken(rawKey)

        val apiKey = ApiKey(userId = userId, keyHash = keyHash, keyPrefix = keyPrefix, name = name)
        apiKeyRepository?.save(apiKey)
        logger.info("API key created for user {}", userId)
        return CreateApiKeyResponse(key = rawKey, name = name, keyPrefix = keyPrefix)
    }

    fun listApiKeys(userId: UUID): List<ApiKeySummary> {
        return apiKeyRepository?.findByUserId(userId)?.map { key ->
            ApiKeySummary(
                id = key.id,
                keyPrefix = key.keyPrefix,
                name = key.name,
                enabled = key.enabled,
                createdAt = key.createdAt.toString(),
                lastUsedAt = key.lastUsedAt?.toString(),
            )
        } ?: emptyList()
    }

    fun deleteApiKey(userId: UUID, keyId: Long) {
        apiKeyRepository?.delete(keyId, userId)
        logger.info("API key {} deleted for user {}", keyId, userId)
    }

    /**
     * Find an existing user linked to an OAuth provider identity, or create a new one.
     *
     * If [oauthRepository] is not configured this throws [IllegalStateException].
     */
    fun findOrCreateOAuthUser(providerName: String, oauthSubject: String, email: String?): User {
        val repo = oauthRepository ?: error("OAuthRepository is not configured")

        val existing = repo.findByProviderSubject(providerName, oauthSubject)
        if (existing != null) {
            return userRepository.findById(existing.userId)
                ?: error("OAuth user record found but linked user missing: ${existing.userId}")
        }

        // Derive a username from the email or generate a random one
        val baseUsername =
            email?.substringBefore('@')?.filter { it.isLetterOrDigit() }?.take(30)
                ?: providerName + "_" + UUID.randomUUID().toString().take(8)
        val username = ensureUniqueUsername(baseUsername)

        val user =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = email ?: "$username@$providerName.oauth",
                passwordHash = passwordEncoder.encode(UUID.randomUUID().toString()),
                role = UserRole.USER,
            )
        userRepository.save(user)

        repo.save(
            OAuthConnection(
                id = 0L,
                userId = user.id,
                provider = providerName,
                subject = oauthSubject,
                email = email,
            )
        )
        logger.info("Created new user {} via OAuth provider {}", username, providerName)
        audit("OAUTH_USER_CREATED", actor = user, detail = "provider=$providerName")
        return user
    }

    private fun ensureUniqueUsername(base: String): String {
        if (userRepository.findByUsername(base) == null) return base
        var i = 2
        while (userRepository.findByUsername("$base$i") != null) i++
        return "$base$i"
    }

    fun authenticateApiKey(rawKey: String): User? {
        val keyHash = hashToken(rawKey)
        val apiKey = apiKeyRepository?.findByKeyHash(keyHash) ?: return null
        if (!apiKey.enabled) return null

        val user = userRepository.findById(apiKey.userId)
        if (user != null && user.enabled) {
            apiKeyRepository.updateLastUsed(apiKey.id)
            return user
        }
        return null
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
                userRepository.updateLastActivity(user.id)
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

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashToken(key: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun audit(
        action: String,
        actor: User? = null,
        target: User? = null,
        detail: String? = null,
    ) {
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
        private const val RESET_TOKEN_TTL_SECONDS = 3600L
        private const val API_KEY_HEX_LENGTH = 32
        private const val API_KEY_PREFIX_LENGTH = 8
        private const val SESSION_TOKEN_HEX_LENGTH = 48
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

private fun User.toSummary() =
    UserSummary(
        id = id.toString(),
        username = username,
        email = email,
        role = role.name,
        enabled = enabled,
    )
