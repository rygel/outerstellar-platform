package dev.outerstellar.platform.security

import java.time.Instant
import java.util.UUID

enum class UserRole {
    USER,
    ADMIN,
}

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val enabled: Boolean = true,
    val lastActivityAt: Instant? = null,
    val avatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
)

interface UserRepository {
    fun findById(id: UUID): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?

    fun save(user: User)

    fun seedAdminUser(passwordHash: String)

    fun findAll(): List<User>

    fun updateRole(userId: UUID, role: UserRole)

    fun updateEnabled(userId: UUID, enabled: Boolean)

    fun updateLastActivity(userId: UUID)

    fun deleteById(userId: UUID)

    fun updateUsername(userId: UUID, newUsername: String)

    fun updateAvatarUrl(userId: UUID, avatarUrl: String?)

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean)
}

interface PasswordResetRepository {
    fun save(token: dev.outerstellar.platform.model.PasswordResetToken)

    fun findByToken(token: String): dev.outerstellar.platform.model.PasswordResetToken?

    fun markUsed(token: String)
}

data class DeviceToken(
    val id: Long,
    val userId: UUID,
    val platform: String,
    val token: String,
    val appBundle: String?,
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
    fun save(apiKey: dev.outerstellar.platform.model.ApiKey)

    fun findByKeyHash(keyHash: String): dev.outerstellar.platform.model.ApiKey?

    fun findByUserId(userId: UUID): List<dev.outerstellar.platform.model.ApiKey>

    fun delete(id: Long, userId: UUID)

    fun updateLastUsed(id: Long)
}
