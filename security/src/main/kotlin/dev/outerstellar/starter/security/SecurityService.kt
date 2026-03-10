package dev.outerstellar.starter.security

import org.slf4j.LoggerFactory

class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(SecurityService::class.java)

    fun authenticate(username: String, password: String): User? {
        val user = userRepository.findByUsername(username)
        if (user == null) {
            logger.warn("Authentication failed: User $username not found")
            return null
        }

        if (!user.enabled) {
            logger.warn("Authentication failed: User $username is disabled")
            return null
        }

        return if (passwordEncoder.matches(password, user.passwordHash)) {
            logger.info("Authentication successful for user $username")
            user
        } else {
            logger.warn("Authentication failed: Invalid password for user $username")
            null
        }
    }
}
