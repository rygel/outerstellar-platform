package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Session
import io.github.rygel.outerstellar.platform.security.TokenHashing
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val tokenHashing = TokenHashing("jdbi-session-repository-test-pepper-32-bytes")

class JdbiSessionRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiSessionRepository(jdbi) }

    @Test
    fun `save and findByTokenHash round-trips`() {
        val userId = createUser()
        val rawToken = "oss_test_token_${UUID.randomUUID()}"
        val tokenHash = tokenHashing.hash(rawToken)
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES))
        repo.save(session)
        val found = repo.findByTokenHash(tokenHash)!!
        assertEquals(userId, found.userId)
        assertEquals(tokenHash, found.tokenHash)
        assertNotNull(found.id)
    }

    @Test
    fun `findByTokenHash returns null for unknown hash`() {
        assertNull(repo.findByTokenHash("nonexistent"))
    }

    @Test
    fun `findByTokenHash returns null for expired session`() {
        val userId = createUser()
        val tokenHash = tokenHashing.hash("expired_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))
        repo.save(session)
        assertNull(repo.findByTokenHash(tokenHash))
    }

    @Test
    fun `updateExpiresAt extends session`() {
        val userId = createUser()
        val tokenHash = tokenHashing.hash("extend_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES))
        repo.save(session)
        val newExpiry = Instant.now().plus(60, ChronoUnit.MINUTES)
        repo.updateExpiresAt(tokenHash, newExpiry)
        val found = repo.findByTokenHash(tokenHash)!!
        // Compare with tolerance: new expiry should be ~60 min from now, at least > 50 min
        val diffMinutes = java.time.Duration.between(Instant.now(), found.expiresAt).toMinutes()
        assertTrue(diffMinutes >= 50, "Expected expiry >= 50 min from now, was $diffMinutes min")
    }

    @Test
    fun `deleteByTokenHash removes session`() {
        val userId = createUser()
        val tokenHash = tokenHashing.hash("delete_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES))
        repo.save(session)
        repo.deleteByTokenHash(tokenHash)
        assertNull(repo.findByTokenHash(tokenHash))
    }

    @Test
    fun `deleteByUserId removes all sessions for user`() {
        val userId = createUser()
        val hash1 = tokenHashing.hash("token1")
        val hash2 = tokenHashing.hash("token2")
        repo.save(Session(tokenHash = hash1, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)))
        repo.save(Session(tokenHash = hash2, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)))
        repo.deleteByUserId(userId)
        assertNull(repo.findByTokenHash(hash1))
        assertNull(repo.findByTokenHash(hash2))
    }

    @Test
    fun `deleteExpired removes only expired sessions`() {
        val userId = createUser()
        val expiredHash = tokenHashing.hash("expired")
        val activeHash = tokenHashing.hash("active")
        repo.save(
            Session(tokenHash = expiredHash, userId = userId, expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))
        )
        repo.save(
            Session(tokenHash = activeHash, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES))
        )
        repo.deleteExpired()
        assertNull(repo.findByTokenHash(expiredHash))
        assertNotNull(repo.findByTokenHash(activeHash))
    }
}
