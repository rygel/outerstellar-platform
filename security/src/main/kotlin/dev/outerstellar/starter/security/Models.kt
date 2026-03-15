package dev.outerstellar.starter.security

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

interface AuditRepository {
    fun log(entry: dev.outerstellar.starter.model.AuditEntry)

    fun findRecent(limit: Int = 50): List<dev.outerstellar.starter.model.AuditEntry>
}

interface PasswordResetRepository {
    fun save(token: dev.outerstellar.starter.model.PasswordResetToken)

    fun findByToken(token: String): dev.outerstellar.starter.model.PasswordResetToken?

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

interface ApiKeyRepository {
    fun save(apiKey: dev.outerstellar.starter.model.ApiKey)

    fun findByKeyHash(keyHash: String): dev.outerstellar.starter.model.ApiKey?

    fun findByUserId(userId: UUID): List<dev.outerstellar.starter.model.ApiKey>

    fun delete(id: Long, userId: UUID)

    fun updateLastUsed(id: Long)
}
