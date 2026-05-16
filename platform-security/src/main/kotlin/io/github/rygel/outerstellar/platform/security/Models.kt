package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.UserRole
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val enabled: Boolean = true,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val lastActivityAt: Instant? = null,
    val avatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val language: String? = null,
    val theme: String? = null,
    val layout: String? = null,
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val totpBackupCodes: String? = null,
)

interface LockoutRepository {
    fun incrementFailedLoginAttempts(userId: UUID): Int

    fun resetFailedLoginAttempts(userId: UUID)

    fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)
}

interface UserRepository : LockoutRepository {
    fun findById(id: UUID): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?

    fun save(user: User)

    fun seedAdminUser(passwordHash: String)

    fun findAll(): List<User>

    fun findPage(limit: Int, offset: Int): List<User>

    fun countAll(): Long

    fun countByRole(role: UserRole): Long

    fun updateRole(userId: UUID, role: UserRole)

    fun updateEnabled(userId: UUID, enabled: Boolean)

    fun updateLastActivity(userId: UUID)

    fun deleteById(userId: UUID)

    fun updateUsername(userId: UUID, newUsername: String)

    fun updateAvatarUrl(userId: UUID, avatarUrl: String?)

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean)

    fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?)

    fun countUsersSince(cutoff: LocalDateTime): Long

    fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>?

    fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?)

    fun enableTotp(userId: UUID)
}

interface PasswordResetRepository {
    fun save(token: io.github.rygel.outerstellar.platform.model.PasswordResetToken)

    fun findByToken(token: String): io.github.rygel.outerstellar.platform.model.PasswordResetToken?

    fun markUsed(token: String)
}

data class DeviceToken(val id: Long, val userId: UUID, val platform: String, val token: String, val appBundle: String?)

data class SecurityConfig(
    val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    val sessionTimeoutSeconds: Long = 1800L,
    val maxFailedLoginAttempts: Int = 10,
    val lockoutDurationSeconds: Long = 900,
)

interface DeviceTokenRepository {
    /** Register or update a device token. Upserts by token value. */
    fun upsert(deviceToken: DeviceToken)

    /** Remove a specific device token (e.g. when user logs out on that device). */
    fun delete(token: String)

    /** Find all active tokens for a user (for sending push notifications). */
    fun findByUserId(userId: UUID): List<DeviceToken>

    /** Remove all tokens for a user (e.g. account deletion). */
    fun deleteAllForUser(userId: UUID)
}

data class Session(
    val id: Long = 0,
    val tokenHash: String,
    val userId: UUID,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant,
)

interface SessionRepository {
    fun save(session: Session)

    /** Find an active (non-expired) session by token hash. */
    fun findByTokenHash(tokenHash: String): Session?

    /** Find a session by token hash regardless of expiry. */
    fun findByTokenHashIncludingExpired(tokenHash: String): Session?

    fun updateExpiresAt(tokenHash: String, expiresAt: Instant)

    fun deleteByTokenHash(tokenHash: String)

    fun deleteByUserId(userId: UUID)

    fun deleteExpired()
}

sealed class SessionLookup {
    data class Active(val user: User) : SessionLookup()

    data object Expired : SessionLookup()

    data object NotFound : SessionLookup()
}

interface ApiKeyRepository {
    fun save(apiKey: io.github.rygel.outerstellar.platform.model.ApiKey)

    fun findByKeyHash(keyHash: String): io.github.rygel.outerstellar.platform.model.ApiKey?

    fun findByUserId(userId: UUID): List<io.github.rygel.outerstellar.platform.model.ApiKey>

    fun delete(id: Long, userId: UUID)

    fun updateLastUsed(id: Long)
}
