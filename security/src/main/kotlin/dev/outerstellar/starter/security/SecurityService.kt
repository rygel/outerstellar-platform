package dev.outerstellar.starter.security

import dev.outerstellar.starter.model.UsernameAlreadyExistsException
import dev.outerstellar.starter.model.WeakPasswordException
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
        if (password.length < 8) throw WeakPasswordException("Password must be at least 8 characters")
        if (userRepository.findByUsername(username) != null) throw UsernameAlreadyExistsException(username)

        val created =
            User(
                id = java.util.UUID.randomUUID(),
                username = username,
                email = username,
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        userRepository.save(created)
        logger.info("Registration successful for user {}", username)
        return created
    }
}
