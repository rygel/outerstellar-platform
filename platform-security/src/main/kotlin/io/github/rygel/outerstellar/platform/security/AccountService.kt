package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.UrlValidator
import java.util.UUID
import org.slf4j.LoggerFactory

class AccountService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val sessionRepository: SessionRepository? = null,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(AccountService::class.java)

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

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

    fun deleteAccount(userId: UUID, currentPassword: String) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw WeakPasswordException("Current password is incorrect")
        }
        deleteAccountInternal(userId)
    }

    private fun deleteAccountInternal(userId: UUID) {
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

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        userRepository.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
        logger.info("Notification preferences updated for user {}", sanitize(user.username))
        audit("NOTIFICATION_PREFERENCES_UPDATED", actor = user)
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
