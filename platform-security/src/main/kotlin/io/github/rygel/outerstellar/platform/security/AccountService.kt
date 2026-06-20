package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
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
        auditRepository?.logAction("PASSWORD_CHANGED", actor = user)
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
        try {
            userRepository.save(updated)
        } catch (e: Exception) {
            // Concurrent profile update raced past the findByEmail/findByUsername check and hit the
            // DB unique constraint. Translate to a user-facing UsernameAlreadyExistsException instead
            // of a raw 500.
            val msg = e.message ?: ""
            if (msg.contains("23505") || msg.contains("duplicate key") || msg.contains("unique constraint")) {
                throw UsernameAlreadyExistsException(resolvedUsername)
            }
            throw e
        }
        logger.info("Profile updated for user {}", sanitize(updated.username))
    }

    fun deleteAccount(userId: UUID, currentPassword: String) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) {
            throw WeakPasswordException("Current password is incorrect")
        }
        deleteAccountInternal(userId)
    }

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean) {
        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())
        userRepository.updateNotificationPreferences(userId, emailEnabled, pushEnabled)
        logger.info("Notification preferences updated for user {}", sanitize(user.username))
        auditRepository?.logAction("NOTIFICATION_PREFERENCES_UPDATED", actor = user)
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
        auditRepository?.logAction("ACCOUNT_DELETED", actor = user)
    }

    companion object {
        private const val MAX_USERNAME_LENGTH = 50
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
