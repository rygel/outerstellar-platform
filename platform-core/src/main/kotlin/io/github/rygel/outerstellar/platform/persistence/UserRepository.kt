package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

interface UserRepository {
    fun incrementFailedLoginAttempts(userId: UUID): Int

    fun resetFailedLoginAttempts(userId: UUID)

    fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)

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

    fun disableTotp(userId: UUID)
}
