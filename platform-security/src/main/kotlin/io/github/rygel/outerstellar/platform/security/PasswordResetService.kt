package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.security.SecureRandom
import java.time.Instant
import org.slf4j.LoggerFactory

class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val resetRepository: PasswordResetRepository? = null,
    private val auditRepository: AuditRepository? = null,
    private val sessionRepository: SessionRepository? = null,
    private val emailService: io.github.rygel.outerstellar.platform.service.EmailService? = null,
    private val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)

    private fun sanitize(value: String): String = value.take(MAX_LOG_ID_LENGTH).replace('\n', ' ').replace('\r', ' ')

    fun requestPasswordReset(email: String): String? {
        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.info("Password reset requested for unknown email {}", sanitize(email))
            return null
        }

        val tokenValue = generateResetToken()
        val resetToken =
            PasswordResetToken(
                userId = user.id,
                token = TokenHashing.hash(tokenValue),
                expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS),
            )
        resetRepository?.save(resetToken)
        logger.info("Password reset token generated for user {}", sanitize(user.username))
        val resetLink = "$appBaseUrl/auth/reset/$tokenValue"
        emailService?.send(
            to = user.email,
            subject = "Password Reset Request",
            body = "Use this link to reset your password:\n$resetLink\n\nThis link expires in 1 hour.",
        )
        audit("PASSWORD_RESET_REQUESTED", actor = user)
        return tokenValue
    }

    fun resetPassword(token: String, newPassword: String) {
        val repository = resetRepository ?: throw IllegalArgumentException("Invalid reset token")
        val resetToken =
            repository.findByToken(TokenHashing.hash(token)) ?: throw IllegalArgumentException("Invalid reset token")

        if (resetToken.used) {
            throw IllegalArgumentException("Reset token has already been used")
        }
        if (resetToken.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Reset token has expired")
        }
        val normalized = newPassword.trim()
        validatePassword(normalized)?.let { throw WeakPasswordException(it) }

        val user =
            userRepository.findById(resetToken.userId) ?: throw UserNotFoundException(resetToken.userId.toString())

        val updated = user.copy(passwordHash = passwordEncoder.encode(normalized))
        userRepository.save(updated)
        repository.markUsed(resetToken.token)
        sessionRepository?.deleteByUserId(user.id)
        logger.info("Password reset completed for user {}", sanitize(user.username))
        audit("PASSWORD_RESET_COMPLETED", actor = user)
    }

    private fun audit(action: String, actor: User? = null, target: User? = null, detail: String? = null) {
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
        private const val MAX_LOG_ID_LENGTH = 80
        private const val RESET_TOKEN_TTL_SECONDS = 3600L
        private const val RESET_TOKEN_BYTES = 32
        private val SECURE_RANDOM = SecureRandom()

        private fun generateResetToken(): String {
            val bytes = ByteArray(RESET_TOKEN_BYTES)
            SECURE_RANDOM.nextBytes(bytes)
            return "prt_" + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
