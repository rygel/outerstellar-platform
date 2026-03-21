package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val resetRepository: PasswordResetRepository? = null,
    private val auditRepository: AuditRepository? = null,
    private val emailService: io.github.rygel.outerstellar.platform.service.EmailService? = null,
    private val appBaseUrl: String = "http://localhost:8080",
) {
    private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)

    fun requestPasswordReset(email: String): String? {
        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.info("Password reset requested for unknown email {}", email)
            return null
        }

        val tokenValue = UUID.randomUUID().toString()
        val resetToken =
            PasswordResetToken(
                userId = user.id,
                token = tokenValue,
                expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS),
            )
        resetRepository?.save(resetToken)
        logger.info("Password reset token generated for user {}", user.username)
        val resetLink = "$appBaseUrl/auth/reset?token=$tokenValue"
        emailService?.send(
            to = user.email,
            subject = "Password Reset Request",
            body =
                "Use this link to reset your password:\n$resetLink\n\nThis link expires in 1 hour.",
        )
        audit("PASSWORD_RESET_REQUESTED", actor = user)
        return tokenValue
    }

    fun resetPassword(token: String, newPassword: String) {
        val resetToken =
            resetRepository?.findByToken(token)
                ?: throw IllegalArgumentException("Invalid reset token")

        if (resetToken.used) {
            throw IllegalArgumentException("Reset token has already been used")
        }
        if (resetToken.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Reset token has expired")
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            throw WeakPasswordException(
                "New password must be at least $MIN_PASSWORD_LENGTH characters"
            )
        }

        val user =
            userRepository.findById(resetToken.userId)
                ?: throw UserNotFoundException(resetToken.userId.toString())

        val updated = user.copy(passwordHash = passwordEncoder.encode(newPassword))
        userRepository.save(updated)
        resetRepository.markUsed(token)
        logger.info("Password reset completed for user {}", user.username)
        audit("PASSWORD_RESET_COMPLETED", actor = user)
    }

    private fun audit(
        action: String,
        actor: User? = null,
        target: User? = null,
        detail: String? = null,
    ) {
        auditRepository?.log(
            AuditEntry(
                actorId = actor?.id?.toString(),
                actorUsername = actor?.username,
                targetId = target?.id?.toString(),
                targetUsername = target?.username,
                action = action,
                detail = detail,
            )
        )
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val RESET_TOKEN_TTL_SECONDS = 3600L
    }
}
