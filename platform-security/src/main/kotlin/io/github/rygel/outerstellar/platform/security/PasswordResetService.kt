package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.model.UserNotFoundException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.service.EmailService
import java.security.SecureRandom
import java.time.Instant
import org.slf4j.LoggerFactory

class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val resetRepository: PasswordResetRepository? = null,
    private val auditRepository: AuditRepository? = null,
    private val sessionRepository: SessionRepository? = null,
    private val emailService: EmailService,
    private val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    private val tokenHashing: TokenHashing,
) {
    private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)

    fun requestPasswordReset(email: String): String? {
        val user = userRepository.findByEmail(email)
        if (user == null) {
            logger.info("Password reset requested for unknown email {}", sanitize(email))
            return null
        }

        val tokenValue = generateResetToken()
        // Invalidate any prior unused tokens for this user so only the newest reset email is valid —
        // prevents token hoarding where a leaked old reset email remains a live attack vector after
        // the user has already reset via a later email.
        resetRepository?.invalidateUnusedForUser(user.id)
        val resetToken =
            PasswordResetToken(
                userId = user.id,
                token = tokenHashing.hash(tokenValue),
                expiresAt = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS),
            )
        resetRepository?.save(resetToken)
        logger.info("Password reset token generated for user {}", sanitize(user.username))
        val resetLink = "$appBaseUrl/auth/reset/$tokenValue"
        emailService.send(
            to = user.email,
            subject = "Password Reset Request",
            body = "Use this link to reset your password:\n$resetLink\n\nThis link expires in 1 hour.",
        )
        auditRepository?.logAction("PASSWORD_RESET_REQUESTED", actor = user)
        return tokenValue
    }

    fun resetPassword(token: String, newPassword: String) {
        val repository = resetRepository ?: throw IllegalArgumentException("Invalid reset token")
        validatePassword(newPassword)?.let { throw WeakPasswordException(it) }
        // Atomic claim: a single UPDATE ... WHERE used = false AND expires_at > now() RETURNING user_id
        // marks the token used and returns the owner in one statement, so two concurrent reset requests
        // with the same token cannot both pass the guard (only one UPDATE matches). Replaces the old
        // findByToken + Java used/expiry check + markUsed which ran in separate transactions (TOCTOU).
        val userId =
            repository.claimToken(tokenHashing.hash(token)) ?: throw IllegalArgumentException("Invalid reset token")

        val user = userRepository.findById(userId) ?: throw UserNotFoundException(userId.toString())

        val updated = user.copy(passwordHash = passwordEncoder.encode(newPassword))
        userRepository.save(updated)
        sessionRepository?.deleteByUserId(user.id)
        logger.info("Password reset completed for user {}", sanitize(user.username))
        auditRepository?.logAction("PASSWORD_RESET_COMPLETED", actor = user)
    }

    companion object {
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
