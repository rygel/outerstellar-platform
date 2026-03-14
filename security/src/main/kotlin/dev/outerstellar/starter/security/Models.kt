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
