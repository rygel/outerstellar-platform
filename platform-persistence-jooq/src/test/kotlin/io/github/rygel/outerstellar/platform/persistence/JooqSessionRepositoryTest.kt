package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.Session
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JooqSessionRepositoryTest : JooqTest() {

    private val repo by lazy { JooqSessionRepository(dsl) }
    private val userRepo by lazy { JooqUserRepository(dsl) }

    private fun createUser(): UUID {
        val id = UUID.randomUUID()
        userRepo.save(
            User(
                id = id,
                username = "user_${id.toString().take(6)}",
                email = "${id.toString().take(6)}@example.com",
                passwordHash = "hash",
                role = UserRole.USER,
            )
        )
        return id
    }

    private fun hashToken(token: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `save and findByTokenHash round-trips`() {
        val userId = createUser()
        val rawToken = "oss_test_token_${UUID.randomUUID()}"
        val tokenHash = hashToken(rawToken)
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
        val tokenHash = hashToken("expired_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().minus(1, ChronoUnit.HOURS))
        repo.save(session)
        assertNull(repo.findByTokenHash(tokenHash))
    }

    @Test
    fun `updateExpiresAt extends session`() {
        val userId = createUser()
        val tokenHash = hashToken("extend_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES))
        repo.save(session)
        val newExpiry = Instant.now().plus(60, ChronoUnit.MINUTES)
        repo.updateExpiresAt(tokenHash, newExpiry)
        val found = repo.findByTokenHash(tokenHash)!!
        assertTrue(found.expiresAt.isAfter(Instant.now().plus(30, ChronoUnit.MINUTES)))
    }

    @Test
    fun `deleteByTokenHash removes session`() {
        val userId = createUser()
        val tokenHash = hashToken("delete_token")
        val session =
            Session(tokenHash = tokenHash, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES))
        repo.save(session)
        repo.deleteByTokenHash(tokenHash)
        assertNull(repo.findByTokenHash(tokenHash))
    }

    @Test
    fun `deleteByUserId removes all sessions for user`() {
        val userId = createUser()
        val hash1 = hashToken("token1")
        val hash2 = hashToken("token2")
        repo.save(Session(tokenHash = hash1, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)))
        repo.save(Session(tokenHash = hash2, userId = userId, expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES)))
        repo.deleteByUserId(userId)
        assertNull(repo.findByTokenHash(hash1))
        assertNull(repo.findByTokenHash(hash2))
    }

    @Test
    fun `deleteExpired removes only expired sessions`() {
        val userId = createUser()
        val expiredHash = hashToken("expired")
        val activeHash = hashToken("active")
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
