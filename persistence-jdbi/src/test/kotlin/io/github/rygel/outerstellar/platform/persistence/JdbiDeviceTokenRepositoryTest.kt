package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.DeviceToken
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiDeviceTokenRepositoryTest : H2JdbiTest() {

    private val repo by lazy { JdbiDeviceTokenRepository(jdbi) }
    private val userRepo by lazy { JdbiUserRepository(jdbi) }

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

    private fun token(
        userId: UUID,
        token: String = "tok_${UUID.randomUUID()}",
        platform: String = "fcm",
        appBundle: String? = "com.example.app",
    ) =
        DeviceToken(
            id = 0,
            userId = userId,
            platform = platform,
            token = token,
            appBundle = appBundle,
        )

    @Test
    fun `upsert and findByUserId round-trips`() {
        val userId = createUser()
        repo.upsert(token(userId, token = "fcm-token-abc", platform = "fcm"))
        val tokens = repo.findByUserId(userId)
        assertEquals(1, tokens.size)
        assertEquals(userId, tokens[0].userId)
        assertEquals("fcm", tokens[0].platform)
        assertEquals("fcm-token-abc", tokens[0].token)
        assertEquals("com.example.app", tokens[0].appBundle)
        assertNotNull(tokens[0].id)
    }

    @Test
    fun `upsert with same token updates instead of inserting duplicate`() {
        val userId = createUser()
        repo.upsert(token(userId, token = "stable-token", platform = "fcm"))
        repo.upsert(token(userId, token = "stable-token", platform = "apns"))
        val tokens = repo.findByUserId(userId)
        assertEquals(1, tokens.size)
        assertEquals("apns", tokens[0].platform) // updated
    }

    @Test
    fun `upsert appBundle can be null`() {
        val userId = createUser()
        repo.upsert(token(userId, token = "no-bundle-tok", appBundle = null))
        val found = repo.findByUserId(userId)[0]
        assertNull(found.appBundle)
    }

    @Test
    fun `delete removes token`() {
        val userId = createUser()
        val tok = "delete-me-token"
        repo.upsert(token(userId, token = tok))
        repo.delete(tok)
        assertTrue(repo.findByUserId(userId).isEmpty())
    }

    @Test
    fun `delete unknown token is a no-op`() {
        repo.delete("does-not-exist") // must not throw
    }

    @Test
    fun `findByUserId returns empty for unknown user`() {
        assertTrue(repo.findByUserId(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `findByUserId only returns own tokens`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.upsert(token(user1, token = "tok-u1"))
        repo.upsert(token(user2, token = "tok-u2"))
        val result = repo.findByUserId(user1)
        assertEquals(1, result.size)
        assertEquals(user1, result[0].userId)
    }

    @Test
    fun `deleteAllForUser removes all tokens for that user`() {
        val userId = createUser()
        repo.upsert(token(userId, token = "tok-a"))
        repo.upsert(token(userId, token = "tok-b"))
        repo.deleteAllForUser(userId)
        assertTrue(repo.findByUserId(userId).isEmpty())
    }

    @Test
    fun `deleteAllForUser does not affect other users`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.upsert(token(user1, token = "tok-keep"))
        repo.upsert(token(user2, token = "tok-delete"))
        repo.deleteAllForUser(user2)
        assertEquals(1, repo.findByUserId(user1).size)
    }
}
