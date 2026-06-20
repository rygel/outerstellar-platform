package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.Session
import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

class SessionService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val config: SecurityConfig,
    private val activityUpdater: AsyncActivityUpdater? = null,
    private val tokenHashing: TokenHashing = TokenHashing(TokenHashing.DEFAULT_PEPPER),
) {
    private val logger = LoggerFactory.getLogger(SessionService::class.java)
    private val secureRandom = SecureRandom()

    fun createSession(userId: UUID): String {
        val rawToken = "oss_" + generateRandomHex(SESSION_TOKEN_HEX_LENGTH)
        val tokenHash = tokenHashing.hash(rawToken)
        val session =
            Session(
                tokenHash = tokenHash,
                userId = userId,
                expiresAt = Instant.now().plusSeconds(config.sessionTimeoutSeconds),
            )
        sessionRepository.save(session)
        logger.info("Session created for user {}", userId)
        return rawToken
    }

    fun lookupSession(rawToken: String): SessionLookup {
        val tokenHash = tokenHashing.hash(rawToken)
        val activeSession = sessionRepository.findByTokenHash(tokenHash)
        if (activeSession != null) {
            val absoluteDeadline = activeSession.createdAt.plusSeconds(config.sessionAbsoluteTimeoutSeconds)
            if (Instant.now().isAfter(absoluteDeadline)) {
                sessionRepository.deleteByTokenHash(tokenHash)
                return SessionLookup.Expired
            }
            val user = userRepository.findById(activeSession.userId)
            if (user != null && user.enabled) {
                sessionRepository.updateExpiresAt(tokenHash, Instant.now().plusSeconds(config.sessionTimeoutSeconds))
                activityUpdater?.record(user.id) ?: userRepository.updateLastActivity(user.id)
                return SessionLookup.Active(user)
            }
            return SessionLookup.NotFound
        }
        val expiredSession = sessionRepository.findByTokenHashIncludingExpired(tokenHash)
        if (expiredSession != null) {
            return SessionLookup.Expired
        }
        return SessionLookup.NotFound
    }

    fun deleteSession(rawToken: String) {
        sessionRepository.deleteByTokenHash(tokenHashing.hash(rawToken))
    }

    private fun generateRandomHex(length: Int): String {
        val bytes = ByteArray(length / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val SESSION_TOKEN_HEX_LENGTH = 48
    }
}
