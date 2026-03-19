package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.security.OAuthConnection
import dev.outerstellar.platform.security.User
import dev.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiOAuthRepositoryTest : H2JdbiTest() {

    private val repo by lazy { JdbiOAuthRepository(jdbi) }
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

    private fun connection(
        userId: UUID,
        provider: String = "google",
        subject: String = "sub_${UUID.randomUUID().toString().take(8)}",
        email: String? = "oauth@example.com",
    ) = OAuthConnection(userId = userId, provider = provider, subject = subject, email = email)

    @Test
    fun `save and findByProviderSubject round-trips`() {
        val userId = createUser()
        val subject = "google-subject-001"
        repo.save(connection(userId, provider = "google", subject = subject))

        val found = repo.findByProviderSubject("google", subject)!!
        assertEquals(userId, found.userId)
        assertEquals("google", found.provider)
        assertEquals(subject, found.subject)
        assertEquals("oauth@example.com", found.email)
        assertNotNull(found.id)
    }

    @Test
    fun `findByProviderSubject returns null for unknown subject`() {
        assertNull(repo.findByProviderSubject("google", "nonexistent"))
    }

    @Test
    fun `findByProviderSubject is provider-scoped`() {
        val userId = createUser()
        repo.save(connection(userId, provider = "google", subject = "shared-sub"))
        assertNull(repo.findByProviderSubject("apple", "shared-sub"))
    }

    @Test
    fun `email field can be null`() {
        val userId = createUser()
        repo.save(connection(userId, subject = "no-email-sub", email = null))
        val found = repo.findByProviderSubject("google", "no-email-sub")!!
        assertNull(found.email)
    }

    @Test
    fun `findByUserId returns all connections for user`() {
        val userId = createUser()
        repo.save(connection(userId, provider = "google", subject = "g-sub"))
        repo.save(connection(userId, provider = "apple", subject = "a-sub"))
        assertEquals(2, repo.findByUserId(userId).size)
    }

    @Test
    fun `findByUserId returns empty for unknown user`() {
        assertTrue(repo.findByUserId(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `findByUserId only returns own connections`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(connection(user1, subject = "sub-1"))
        repo.save(connection(user2, subject = "sub-2"))
        val result = repo.findByUserId(user1)
        assertEquals(1, result.size)
        assertEquals(user1, result[0].userId)
    }

    @Test
    fun `delete removes connection`() {
        val userId = createUser()
        repo.save(connection(userId, subject = "delete-sub"))
        val saved = repo.findByUserId(userId)[0]
        repo.delete(saved.id, userId)
        assertTrue(repo.findByUserId(userId).isEmpty())
    }

    @Test
    fun `delete does not affect other user connections`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(connection(user1, subject = "u1-sub"))
        val saved = repo.findByUserId(user1)[0]
        repo.delete(saved.id, user2) // wrong userId — should be a no-op
        assertEquals(1, repo.findByUserId(user1).size)
    }
}
