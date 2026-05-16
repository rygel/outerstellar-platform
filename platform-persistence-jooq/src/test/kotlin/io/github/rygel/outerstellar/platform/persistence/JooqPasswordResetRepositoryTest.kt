package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.User
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JooqPasswordResetRepositoryTest : JooqTest() {

    private val repo by lazy { JooqPasswordResetRepository(dsl) }
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

    private fun token(userId: UUID, rawToken: String = UUID.randomUUID().toString()) =
        PasswordResetToken(userId = userId, token = rawToken, expiresAt = Instant.now().plusSeconds(3600), used = false)

    @Test
    fun `save and findByToken round-trips`() {
        val userId = createUser()
        val t = token(userId, "reset-abc")
        repo.save(t)
        val found = repo.findByToken("reset-abc")!!
        assertEquals(userId, found.userId)
        assertEquals("reset-abc", found.token)
        assertFalse(found.used)
        assertNotNull(found.expiresAt)
    }

    @Test
    fun `findByToken returns null for unknown token`() {
        assertNull(repo.findByToken("nonexistent"))
    }

    @Test
    fun `markUsed sets used flag`() {
        val userId = createUser()
        val t = token(userId, "mark-used-token")
        repo.save(t)
        assertFalse(repo.findByToken("mark-used-token")!!.used)
        repo.markUsed("mark-used-token")
        assertTrue(repo.findByToken("mark-used-token")!!.used)
    }

    @Test
    fun `multiple tokens can exist for same user`() {
        val userId = createUser()
        repo.save(token(userId, "token-1"))
        repo.save(token(userId, "token-2"))
        assertNotNull(repo.findByToken("token-1"))
        assertNotNull(repo.findByToken("token-2"))
    }
}
