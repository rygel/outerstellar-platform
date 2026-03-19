package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.ApiKey
import dev.outerstellar.platform.security.User
import dev.outerstellar.platform.security.UserRole
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiApiKeyRepositoryTest : H2JdbiTest() {

    private val repo by lazy { JdbiApiKeyRepository(jdbi) }
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

    private fun apiKey(userId: UUID, name: String = "My Key") =
        ApiKey(
            userId = userId,
            keyHash = JdbiApiKeyRepository.hashKey("raw-key-${UUID.randomUUID()}"),
            keyPrefix = "sk_test",
            name = name,
            enabled = true,
            createdAt = Instant.now(),
        )

    @Test
    fun `save and findByKeyHash round-trips`() {
        val userId = createUser()
        val rawKey = "raw-test-key"
        val hash = JdbiApiKeyRepository.hashKey(rawKey)
        val key =
            ApiKey(
                userId = userId,
                keyHash = hash,
                keyPrefix = "sk_",
                name = "Test Key",
                enabled = true,
                createdAt = Instant.now(),
            )
        repo.save(key)
        val found = repo.findByKeyHash(hash)!!
        assertEquals(userId, found.userId)
        assertEquals("Test Key", found.name)
        assertTrue(found.enabled)
        assertNotNull(found.id) // auto-generated
    }

    @Test
    fun `findByKeyHash returns null for unknown hash`() {
        assertNull(repo.findByKeyHash("nonexistent"))
    }

    @Test
    fun `findByUserId returns all keys for user`() {
        val userId = createUser()
        repo.save(apiKey(userId, "Key 1"))
        repo.save(apiKey(userId, "Key 2"))
        val keys = repo.findByUserId(userId)
        assertEquals(2, keys.size)
    }

    @Test
    fun `findByUserId returns empty for unknown user`() {
        assertTrue(repo.findByUserId(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `findByUserId only returns own keys`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(apiKey(user1, "U1 Key"))
        repo.save(apiKey(user2, "U2 Key"))
        assertEquals(1, repo.findByUserId(user1).size)
        assertEquals("U1 Key", repo.findByUserId(user1)[0].name)
    }

    @Test
    fun `delete removes key`() {
        val userId = createUser()
        val key = apiKey(userId, "Delete Me")
        repo.save(key)
        val saved = repo.findByUserId(userId)[0]
        repo.delete(saved.id, userId)
        assertTrue(repo.findByUserId(userId).isEmpty())
    }

    @Test
    fun `delete does not affect other user keys`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(apiKey(user1, "U1 Key"))
        val saved = repo.findByUserId(user1)[0]
        repo.delete(saved.id, user2) // wrong user
        assertEquals(1, repo.findByUserId(user1).size)
    }

    @Test
    fun `updateLastUsed sets last_used_at`() {
        val userId = createUser()
        val key = apiKey(userId)
        repo.save(key)
        val saved = repo.findByUserId(userId)[0]
        assertNull(saved.lastUsedAt)
        repo.updateLastUsed(saved.id)
        assertNotNull(repo.findByUserId(userId)[0].lastUsedAt)
    }

    @Test
    fun `hashKey produces consistent SHA-256 hex`() {
        val hash1 = JdbiApiKeyRepository.hashKey("test-key")
        val hash2 = JdbiApiKeyRepository.hashKey("test-key")
        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length) // SHA-256 = 32 bytes = 64 hex chars
        assertFalse(hash1.contains("test-key"))
    }
}
