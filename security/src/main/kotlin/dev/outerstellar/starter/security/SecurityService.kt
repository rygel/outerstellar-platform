package dev.outerstellar.starter.security

import dev.outerstellar.starter.model.InsufficientPermissionException
import dev.outerstellar.starter.model.UserNotFoundException
import dev.outerstellar.starter.model.UserSummary
import dev.outerstellar.starter.model.UsernameAlreadyExistsException
import dev.outerstellar.starter.model.WeakPasswordException
import java.util.UUID
import org.slf4j.LoggerFactory

class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
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
    }

    fun setUserRole(adminId: UUID, targetId: UUID, role: UserRole) {
        if (adminId == targetId) {
            throw InsufficientPermissionException("Cannot change your own role")
        }
        val target =
            userRepository.findById(targetId) ?: throw UserNotFoundException(targetId.toString())
        userRepository.updateRole(targetId, role)
        logger.info("User {} role set to {} by admin {}", target.username, role, adminId)
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
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
