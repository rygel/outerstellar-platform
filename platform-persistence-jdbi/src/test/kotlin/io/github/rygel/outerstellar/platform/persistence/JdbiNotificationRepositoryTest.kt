package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiNotificationRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiNotificationRepository(jdbi) }
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

    private fun notification(userId: UUID, title: String = "Title", type: String = "info") =
        Notification(userId = userId, title = title, body = "Body for $title", type = type)

    @Test
    fun `save and findByUserId round-trips`() {
        val userId = createUser()
        val n = notification(userId, "Hello")
        repo.save(n)
        val results = repo.findByUserId(userId)
        assertEquals(1, results.size)
        assertEquals("Hello", results[0].title)
        assertEquals("Body for Hello", results[0].body)
        assertEquals("info", results[0].type)
        assertFalse(results[0].isRead)
    }

    @Test
    fun `findByUserId returns empty list for unknown user`() {
        assertTrue(repo.findByUserId(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `findByUserId respects limit`() {
        val userId = createUser()
        repeat(5) { i -> repo.save(notification(userId, "N$i")) }
        assertEquals(3, repo.findByUserId(userId, limit = 3).size)
    }

    @Test
    fun `findByUserId orders newest first`() {
        val userId = createUser()
        repo.save(notification(userId, "First").copy(createdAt = java.time.Instant.now().minusSeconds(10)))
        repo.save(notification(userId, "Second").copy(createdAt = java.time.Instant.now().minusSeconds(5)))
        repo.save(notification(userId, "Third"))
        val results = repo.findByUserId(userId)
        assertEquals("Third", results[0].title)
        assertEquals("First", results[2].title)
    }

    @Test
    fun `findByUserId only returns own notifications`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(notification(user1, "For user1"))
        repo.save(notification(user2, "For user2"))
        val results = repo.findByUserId(user1)
        assertEquals(1, results.size)
        assertEquals("For user1", results[0].title)
    }

    @Test
    fun `countUnread counts only unread`() {
        val userId = createUser()
        repo.save(notification(userId, "N1"))
        repo.save(notification(userId, "N2"))
        repo.save(notification(userId, "N3"))
        assertEquals(3, repo.countUnread(userId))
    }

    @Test
    fun `countUnread returns zero when all read`() {
        val userId = createUser()
        repo.save(notification(userId, "N1"))
        repo.markAllRead(userId)
        assertEquals(0, repo.countUnread(userId))
    }

    @Test
    fun `markRead sets read_at for the specific notification`() {
        val userId = createUser()
        val n = notification(userId, "ReadMe")
        repo.save(n)
        assertNull(repo.findByUserId(userId)[0].readAt)
        repo.markRead(n.id, userId)
        val updated = repo.findByUserId(userId)[0]
        assertNotNull(updated.readAt)
        assertTrue(updated.isRead)
    }

    @Test
    fun `markRead does not affect other user notifications`() {
        val user1 = createUser()
        val user2 = createUser()
        val n = notification(user1, "Private")
        repo.save(n)
        repo.markRead(n.id, user2) // wrong user — should have no effect
        assertFalse(repo.findByUserId(user1)[0].isRead)
    }

    @Test
    fun `markAllRead marks only unread notifications`() {
        val userId = createUser()
        val n1 = notification(userId, "N1")
        val n2 = notification(userId, "N2")
        repo.save(n1)
        repo.save(n2)
        assertEquals(2, repo.countUnread(userId))
        repo.markAllRead(userId)
        assertEquals(0, repo.countUnread(userId))
        repo.findByUserId(userId).forEach { assertTrue(it.isRead) }
    }

    @Test
    fun `markAllRead does not affect other users`() {
        val user1 = createUser()
        val user2 = createUser()
        repo.save(notification(user1, "U1"))
        repo.save(notification(user2, "U2"))
        repo.markAllRead(user1)
        assertEquals(0, repo.countUnread(user1))
        assertEquals(1, repo.countUnread(user2))
    }

    @Test
    fun `delete removes notification`() {
        val userId = createUser()
        val n = notification(userId)
        repo.save(n)
        assertEquals(1, repo.findByUserId(userId).size)
        repo.delete(n.id, userId)
        assertTrue(repo.findByUserId(userId).isEmpty())
    }

    @Test
    fun `delete does not affect other user notifications`() {
        val user1 = createUser()
        val user2 = createUser()
        val n = notification(user1)
        repo.save(n)
        repo.delete(n.id, user2) // wrong user
        assertEquals(1, repo.findByUserId(user1).size)
    }
}
