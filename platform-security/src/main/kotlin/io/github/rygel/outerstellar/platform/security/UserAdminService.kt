package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class UserAdminService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(UserAdminService::class.java)

    fun listUsers(): List<UserSummary> = userRepository.findAll().map { it.toSummary() }

    fun listUsers(limit: Int, offset: Int): List<UserSummary> =
        userRepository.findPage(limit.coerceIn(1, MAX_PAGE_LIMIT), offset).map { it.toSummary() }

    fun countUsers(): Long = userRepository.countAll()

    fun findUserSummary(id: UUID): UserSummary? = userRepository.findById(id)?.toSummary()

    fun setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own enabled status")
        }
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Only administrators can enable/disable accounts")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateEnabled(targetId, enabled)
        logger.info("User {} enabled set to {} by admin {}", sanitize(target.username), enabled, adminId)
        val action = if (enabled) "USER_ENABLED" else "USER_DISABLED"
        auditRepository?.logAction(action, actor = admin, target = target)
    }

    fun unlockAccount(adminId: UUID, targetId: UUID) {
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Admin access required to unlock accounts")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.resetFailedLoginAttempts(targetId)
        logger.info("User {} unlocked by admin {}", sanitize(target.username), sanitize(admin.username))
        auditRepository?.logAction("USER_UNLOCKED", actor = admin, target = target)
    }

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val admin = userRepository.findById(adminId) ?: throw UserNotFoundException(adminId.toString())
        if (admin.role != UserRole.ADMIN) {
            throw InsufficientPermissionException("Only administrators can change user roles")
        }
        val target = userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateRole(targetId, role)
        logger.info("User {} role set to {} by admin {}", sanitize(target.username), role, adminId)
        auditRepository?.logAction(
            "USER_ROLE_CHANGED",
            actor = admin,
            target = target,
            detail = "from ${target.role} to $role",
        )
    }

    fun countAuditEntries(): Long = auditRepository?.countAll() ?: 0L

    fun getAuditLog(limit: Int = 50): List<AuditEntry> = auditRepository?.findRecent(limit) ?: emptyList()

    fun getAuditLog(limit: Int, offset: Int): List<AuditEntry> = auditRepository?.findPage(limit, offset) ?: emptyList()

    private fun User.toSummary() =
        UserSummary(
            id = id.toString(),
            username = username,
            email = email,
            role = role,
            enabled = enabled,
            failedLoginAttempts = failedLoginAttempts,
            lockedUntil = lockedUntil,
        )

    companion object {
        private const val MAX_PAGE_LIMIT = 1000
    }
}
