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
    private val appBaseUrl: String? = null,
    private val sessionRepository: SessionRepository? = null,
    private val sessionTimeoutSeconds: Long = 900L,
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
            user == null -> null
            !user.enabled -> {
                logger.warn("Authentication failed for username {}", username)
                null
            }
            passwordEncoder.matches(password, user.passwordHash) -> {
                logger.info("Authentication successful for user $username")
                user
            }
            else -> {
                logger.warn("Authentication failed for username {}", username)
                null
            }
        }
    }

    fun register(username: String, password: String): User {
        require(username.isNotBlank()) { "Username is required" }
        if (password.length < MIN_PASSWORD_LENGTH)
            throw WeakPasswordException("Password must be at least $MIN_PASSWORD_LENGTH characters")
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
        return userRepository.findPage(DEFAULT_USER_LIST_LIMIT, 0).map { it.toSummary() }
    }

    fun listUsers(limit: Int, offset: Int): List<UserSummary> =
        userRepository.findPage(limit.coerceIn(1, MAX_PAGE_LIMIT), offset.coerceAtLeast(0)).map { it.toSummary() }

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

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        if (target.role == UserRole.ADMIN && role != UserRole.ADMIN) {
            val adminCount = userRepository.countByRole(UserRole.ADMIN)
            if (adminCount <= 1) {
                throw InsufficientPermissionException("Cannot demote the only remaining admin account")
            }
        }
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
        val normalizedEmail = newEmail.trim()
        if (normalizedEmail != user.email) {
            if (!isValidEmail(normalizedEmail)) {
                throw IllegalArgumentException("Invalid email address: $newEmail")
            }
            val existing = userRepository.findByEmail(normalizedEmail)
            if (existing != null && existing.id != userId) {
                throw UsernameAlreadyExistsException(normalizedEmail)
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
                    } catch (_: Exception) {
                        null
                    }
                if (host != null && isForbiddenAvatarHost(host)) {
                    throw IllegalArgumentException("Avatar URL must not point to private or internal addresses")
                }
            }
            userRepository.updateAvatarUrl(userId, sanitizedUrl)
        }
        userRepository.save(user.copy(email = normalizedEmail))
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
        val salt = generateRandomHex(TOKEN_SALT_HEX_LENGTH)
        val secret = generateRandomHex(SESSION_TOKEN_SECRET_HEX_LENGTH)
        val rawToken = "oss_${salt}_$secret"
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

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashToken(key: String): String {
        parseSaltedToken("oss_", key)?.let { (salt, secret) ->
            return sha256("$salt:$secret")
        }
        // Backward-compatible path for previously issued tokens.
        return sha256(key)
    }

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseSaltedToken(prefix: String, token: String): Pair<String, String>? {
        if (!token.startsWith(prefix)) return null
        val payload = token.removePrefix(prefix)
        val separator = payload.indexOf('_')
        if (separator <= 0 || separator == payload.lastIndex) return null
        val salt = payload.substring(0, separator)
        val secret = payload.substring(separator + 1)
        if (!HEX_REGEX.matches(salt) || !HEX_REGEX.matches(secret)) return null
        if (salt.length != TOKEN_SALT_HEX_LENGTH || secret.isBlank()) return null
        return salt to secret
    }

    private fun isValidEmail(email: String): Boolean {
        if (email.length > MAX_EMAIL_LENGTH || email.contains('\r') || email.contains('\n')) {
            return false
        }
        if (!EMAIL_REGEX.matches(email)) {
            return false
        }
        val parts = email.split('@')
        if (parts.size != 2) return false
        val local = parts[0]
        val domain = parts[1]
        if (local.length > MAX_EMAIL_LOCAL_LENGTH) return false
        if (domain.length > MAX_EMAIL_DOMAIN_LENGTH) return false
        if (domain.startsWith('.') || domain.endsWith('.')) return false
        val labels = domain.split('.')
        if (labels.size < 2) return false
        return labels.all { label ->
            label.isNotBlank() && label.length <= 63 && !label.startsWith('-') && !label.endsWith('-')
        }
    }

    private fun isForbiddenAvatarHost(host: String): Boolean {
        if (PRIVATE_HOST_PATTERNS.any { it.matches(host) }) return true
        return isPrivateIpv4(host) || isPrivateIpv6(host)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val match = IPV4_REGEX.matchEntire(host) ?: return false
        val octets = match.groupValues.drop(1).map { it.toInt() }
        val first = octets[0]
        val second = octets[1]
        return when {
            first == 0 -> true
            first == 10 -> true
            first == 127 -> true
            first == 169 && second == 254 -> true
            first == 172 && second in 16..31 -> true
            first == 192 && second == 168 -> true
            first == 100 && second in 64..127 -> true
            first == 198 && (second == 18 || second == 19) -> true
            first >= 224 -> true
            else -> false
        }
    }

    private fun isPrivateIpv6(host: String): Boolean {
        val normalized = host.trim('[', ']').lowercase()
        return normalized == "::1" ||
            normalized == "::" ||
            normalized.startsWith("fe80:") ||
            normalized.startsWith("fc") ||
            normalized.startsWith("fd") ||
            normalized.startsWith("2001:db8:")
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
        private const val TOKEN_SALT_HEX_LENGTH = 16
        private const val SESSION_TOKEN_SECRET_HEX_LENGTH = 48
        private const val DEFAULT_USER_LIST_LIMIT = 100
        private const val MAX_PAGE_LIMIT = 200
        private const val MAX_URL_LENGTH = 2048
        private const val MAX_EMAIL_LENGTH = 254
        private const val MAX_EMAIL_LOCAL_LENGTH = 64
        private const val MAX_EMAIL_DOMAIN_LENGTH = 253
        private val HEX_REGEX = Regex("^[0-9a-f]+$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$")
        private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        private val PRIVATE_HOST_PATTERNS =
            listOf(
                Regex("^localhost$"),
                Regex("^127\\..*"),
                Regex("^10\\..*"),
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\..*"),
                Regex("^192\\.168\\..*"),
                Regex("^169\\.254\\..*"),
                Regex("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\..*"),
                Regex("^198\\.(18|19)\\..*"),
                Regex("^0\\..*"),
                Regex("^::1$"),
                Regex("^::$"),
                Regex("^fe80:.*"),
                Regex("^(fc|fd).*"),
                Regex(".*\\.local$"),
                Regex(".*\\.internal$"),
            )
    }
}

private fun User.toSummary() =
    UserSummary(id = id.toString(), username = username, email = email, role = role.name, enabled = enabled)
