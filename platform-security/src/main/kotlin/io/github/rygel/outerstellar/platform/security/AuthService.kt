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
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
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
    private val totpService: TOTPService,
    private val totpSecretEncryption: TotpSecretEncryption,
    private val transactionManager: TransactionManager? = null,
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = SecureRandom()
    private val partialAuthStore: Cache<String, PartialAuth> =
        Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10_000).build()

    /**
     * A dummy password hash used to keep the not-found login path's timing equal to the bad-password path (issue #505).
     * Computed once via the injected encoder so its cost factor matches the deployment's real password hashes; the
     * value is constant but never used to authenticate anyone.
     */
    private val dummyPasswordHash: String by lazy { passwordEncoder.encode("timing-mitigation-dummy") }

    fun authenticate(username: String, password: String): AuthResult? {
        val user =
            userRepository.findByUsername(username)
                ?: run {
                    // Timing-oracle mitigation (issue #505): a non-existent account returns immediately
                    // and never invokes the (deliberately slow) BCrypt check, so it responds far faster
                    // than an existing account with a wrong password — a reliable enumeration signal.
                    // Run a dummy BCrypt verify against a precomputed hash so the not-found path takes
                    // the same ~50-150ms as the bad-password path. The result is discarded. Standard
                    // mitigation (Spring Security, Django, etc.).
                    passwordEncoder.matches(password, dummyPasswordHash)
                    logger.warn("Authentication failed: User ${sanitize(username)} not found")
                    auditRepository?.logAction(
                        "AUTHENTICATION_FAILED",
                        detail = "Invalid credentials",
                        targetUsername = sanitize(username),
                    )
                    return null
                }
        if (!verifyCredentials(user, password)) return null
        return if (user.totpEnabled) {
            AuthResult.TotpRequired(generatePartialAuthToken(user.id))
        } else {
            AuthResult.Authenticated(user)
        }
    }

    fun reauthenticate(userId: UUID, password: String): Boolean {
        val user = userRepository.findById(userId) ?: return false
        return verifyCredentials(user, password)
    }

    fun register(username: String, password: String): User {
        if (!config.registrationEnabled) {
            throw RegistrationDisabledException()
        }
        require(username.isNotBlank()) { "Username is required" }
        require(username.length <= MAX_USERNAME_LENGTH) { "Username cannot exceed $MAX_USERNAME_LENGTH characters" }
        require(EMAIL_REGEX.matches(username)) { "Username must be a valid email address" }
        validatePassword(password)?.let { throw WeakPasswordException(it) }
        if (userRepository.findByUsername(username) != null) throw UsernameAlreadyExistsException(username)

        val created =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = username,
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        userRepository.save(created)
        logger.info("Registration successful for user {}", sanitize(username))
        auditRepository?.logAction("USER_REGISTERED", actor = created)
        return created
    }

    @Suppress("ReturnCount")
    fun verifyTotp(partialToken: String, code: String, sessionService: SessionService): TotpVerifyResponse {
        val partial =
            partialAuthStore.asMap().compute(partialToken) { _, existing ->
                if (existing == null) return@compute null
                if (existing.attemptCount >= 4) null else existing.copy(attemptCount = existing.attemptCount + 1)
            } ?: return TotpVerifyResponse("expired")

        val user = userRepository.findById(partial.userId) ?: return TotpVerifyResponse("expired")
        val (secret, storedSecret) = resolveTotpSecret(user) ?: return TotpVerifyResponse("expired")

        if (totpService.verifyCode(secret, code)) {
            partialAuthStore.invalidate(partialToken)
            userRepository.resetFailedTotpAttempts(user.id)
            val token = sessionService.createSession(user.id)
            return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
        }

        val backupCodes = user.totpBackupCodes
        if (backupCodes != null) {
            val updatedCodes = totpService.verifyBackupCode(code, backupCodes)
            if (updatedCodes != null) {
                userRepository.updateTotpSecret(user.id, storedSecret, updatedCodes.ifEmpty { null })
                partialAuthStore.invalidate(partialToken)
                userRepository.resetFailedTotpAttempts(user.id)
                val token = sessionService.createSession(user.id)
                return TotpVerifyResponse("success", token = token, username = user.username, role = user.role.name)
            }
        }

        // Per-user TOTP failure tracking (issue #510): the partial-token cap was resettable by
        // re-authenticating, allowing indefinite TOTP brute-force. Increment a per-user counter
        // independent of the partial-token lifecycle, and lock the account once it reaches the
        // configured threshold (reusing the existing lockout infrastructure).
        val attempts = userRepository.incrementFailedTotpAttempts(user.id)
        if (attempts >= config.maxFailedLoginAttempts) {
            val until = Instant.now().plusSeconds(config.lockoutDurationSeconds)
            userRepository.updateLockedUntil(user.id, until)
            partialAuthStore.invalidate(partialToken)
            logger.warn("TOTP verification locked user ${sanitize(user.username)} after $attempts failed attempts")
            return TotpVerifyResponse("locked")
        }
        return TotpVerifyResponse("invalid_code")
    }

    fun enableTotp(userId: UUID, secret: String, backupCodes: String) {
        val encryptedSecret = totpSecretEncryption.encrypt(secret)
        // Both writes must be atomic — a partial failure leaves 2FA in an inconsistent state
        // (secret stored but not enabled, or enabled with no secret).
        transactionManager?.inTransaction {
            userRepository.updateTotpSecret(userId, encryptedSecret, backupCodes)
            userRepository.enableTotp(userId)
        }
            ?: run {
                userRepository.updateTotpSecret(userId, encryptedSecret, backupCodes)
                userRepository.enableTotp(userId)
            }
    }

    fun disableTotp(userId: UUID) {
        transactionManager?.inTransaction {
            userRepository.updateTotpSecret(userId, null, null)
            userRepository.disableTotp(userId)
        }
            ?: run {
                userRepository.updateTotpSecret(userId, null, null)
                userRepository.disableTotp(userId)
            }
    }

    private fun generatePartialAuthToken(userId: UUID): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = "pt_" + bytes.joinToString("") { "%02x".format(it) }
        partialAuthStore.put(token, PartialAuth(userId = userId))
        return token
    }

    private fun resolveTotpSecret(user: User): Pair<String, String>? {
        val storedSecret = user.totpSecret ?: return null
        if (totpSecretEncryption.isEncrypted(storedSecret)) {
            return totpSecretEncryption.decrypt(storedSecret) to storedSecret
        }

        logger.warn("Migrating legacy plaintext TOTP secret for user {}", user.id)
        val encryptedSecret = totpSecretEncryption.encrypt(storedSecret)
        userRepository.updateTotpSecret(user.id, encryptedSecret, user.totpBackupCodes)
        return storedSecret to encryptedSecret
    }

    private fun verifyCredentials(user: User, password: String): Boolean {
        if (!user.enabled) {
            logger.warn("Authentication failed: User ${sanitize(user.username)} is disabled")
            auditRepository?.logAction("AUTHENTICATION_FAILED", actor = user, detail = "Invalid credentials")
            return false
        }
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            logger.warn("Authentication failed: User ${sanitize(user.username)} is locked until $lockedUntil")
            auditRepository?.logAction("AUTHENTICATION_FAILED", actor = user, detail = "Invalid credentials")
            return false
        }
        if (passwordEncoder.matches(password, user.passwordHash)) {
            if (user.failedLoginAttempts > 0) userRepository.resetFailedLoginAttempts(user.id)
            logger.info("Authentication successful for user ${sanitize(user.username)}")
            return true
        }
        val attempts = userRepository.incrementFailedLoginAttempts(user.id)
        logger.warn("Authentication failed: Invalid password for user ${sanitize(user.username)} (attempt $attempts)")
        auditRepository?.logAction("AUTHENTICATION_FAILED", actor = user, detail = "Invalid credentials")
        if (attempts >= config.maxFailedLoginAttempts) {
            val until = Instant.now().plusSeconds(config.lockoutDurationSeconds)
            userRepository.updateLockedUntil(user.id, until)
            logger.warn("User ${sanitize(user.username)} locked until $until after $attempts failed attempts")
        }
        return false
    }

    companion object {
        private const val MAX_USERNAME_LENGTH = 50
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
