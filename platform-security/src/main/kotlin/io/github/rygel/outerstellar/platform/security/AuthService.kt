package io.github.rygel.outerstellar.platform.security

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.rygel.outerstellar.platform.model.RegistrationDisabledException
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

data class PartialAuth(val userId: UUID, val attemptCount: Int = 0)

class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val config: SecurityConfig = SecurityConfig(),
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = SecureRandom()
    private val totpService = TOTPService()
    private val partialAuthStore: Cache<String, PartialAuth> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    fun authenticate(username: String, password: String): AuthResult? {
        val user =
            userRepository.findByUsername(username)
                ?: run {
                    logger.warn("Authentication failed: User ${sanitize(username)} not found")
                    auditRepository?.logAction(
                        "AUTHENTICATION_FAILED",
                        detail = "User not found",
                        targetUsername = sanitize(username),
                    )
                    return null
                }
        if (!user.enabled) {
            logger.warn("Authentication failed: User ${sanitize(username)} is disabled")
            auditRepository?.logAction("AUTHENTICATION_FAILED", actor = user, detail = "Account disabled")
            return null
        }
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            logger.warn("Authentication failed: User ${sanitize(username)} is locked until $lockedUntil")
            auditRepository?.logAction(
                "AUTHENTICATION_FAILED",
                actor = user,
                detail = "Account locked until $lockedUntil",
            )
            return null
        }
        if (passwordEncoder.matches(password, user.passwordHash)) {
            if (user.failedLoginAttempts > 0) {
                userRepository.resetFailedLoginAttempts(user.id)
            }
            logger.info("Authentication successful for user ${sanitize(username)}")
            if (user.totpEnabled) {
                return AuthResult.TotpRequired(generatePartialAuthToken(user.id))
            }
            return AuthResult.Authenticated(user)
        }
        val attempts = userRepository.incrementFailedLoginAttempts(user.id)
        logger.warn("Authentication failed: Invalid password for user ${sanitize(username)} (attempt $attempts)")
        auditRepository?.logAction("AUTHENTICATION_FAILED", actor = user, detail = "Invalid password")
        if (attempts >= config.maxFailedLoginAttempts) {
            val until = Instant.now().plusSeconds(config.lockoutDurationSeconds)
            userRepository.updateLockedUntil(user.id, until)
            logger.warn("User ${sanitize(username)} locked until $until after $attempts failed attempts")
        }
        return null
    }

    fun register(username: String, password: String): User {
        if (!config.registrationEnabled) {
            throw RegistrationDisabledException()
        }
        require(username.isNotBlank()) { "Username is required" }
        val normalized = password.trim()
        validatePassword(normalized)?.let { throw WeakPasswordException(it) }
        if (userRepository.findByUsername(username) != null) throw UsernameAlreadyExistsException(username)

        val created =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = username,
                passwordHash = passwordEncoder.encode(normalized),
                role = UserRole.USER,
            )
        userRepository.save(created)
        logger.info("Registration successful for user {}", sanitize(username))
        auditRepository?.logAction("USER_REGISTERED", actor = created)
        return created
    }

    fun verifyTotp(partialToken: String, code: String, sessionService: SessionService): TotpVerifyResponse {
        val partial =
            partialAuthStore.asMap().compute(partialToken) { _, existing ->
                if (existing == null) return@compute null
                if (existing.attemptCount >= 4) null else existing.copy(attemptCount = existing.attemptCount + 1)
            } ?: return TotpVerifyResponse("expired")

        val user = userRepository.findById(partial.userId) ?: return TotpVerifyResponse("expired")
        val secret = user.totpSecret ?: return TotpVerifyResponse("expired")

        if (totpService.verifyCode(secret, code)) {
            partialAuthStore.invalidate(partialToken)
            val token = sessionService.createSession(user.id)
            return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
        }

        val backupCodes = user.totpBackupCodes
        if (backupCodes != null) {
            val updatedCodes = totpService.verifyBackupCode(code, backupCodes)
            if (updatedCodes != null) {
                userRepository.updateTotpSecret(user.id, user.totpSecret, updatedCodes.ifEmpty { null })
                partialAuthStore.invalidate(partialToken)
                val token = sessionService.createSession(user.id)
                return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
            }
        }

        return TotpVerifyResponse("invalid_code")
    }

    fun enableTotp(userId: UUID, secret: String, backupCodes: String) {
        userRepository.updateTotpSecret(userId, secret, backupCodes)
        userRepository.enableTotp(userId)
    }

    fun disableTotp(userId: UUID) {
        userRepository.updateTotpSecret(userId, null, null)
        userRepository.disableTotp(userId)
    }

    private fun generatePartialAuthToken(userId: UUID): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = "pt_" + bytes.joinToString("") { "%02x".format(it) }
        partialAuthStore.put(token, PartialAuth(userId = userId))
        return token
    }
}
