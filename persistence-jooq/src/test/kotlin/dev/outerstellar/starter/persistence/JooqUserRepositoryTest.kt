package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JooqUserRepositoryTest : H2JooqTest() {

    private val repo by lazy { JooqUserRepository(dsl) }

    private fun user(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
        enabled: Boolean = true,
    ) =
        User(
            id = UUID.randomUUID(),
            username = username,
            email = "$username@example.com",
            passwordHash = "hash",
            role = role,
            enabled = enabled,
        )

    @Test
    fun `save and findById round-trips correctly`() {
        val u = user("alice")
        repo.save(u)
        val found = repo.findById(u.id)!!
        assertEquals(u.id, found.id)
        assertEquals("alice", found.username)
        assertEquals("alice@example.com", found.email)
        assertEquals(UserRole.USER, found.role)
        assertTrue(found.enabled)
    }

    @Test
    fun `findByUsername returns user`() {
        repo.save(user("bob"))
        assertNotNull(repo.findByUsername("bob"))
        assertNull(repo.findByUsername("nobody"))
    }

    @Test
    fun `findByEmail returns user`() {
        val u = user("carol")
        repo.save(u)
        assertNotNull(repo.findByEmail("carol@example.com"))
        assertNull(repo.findByEmail("ghost@example.com"))
    }

    @Test
    fun `findById returns null for unknown id`() {
        assertNull(repo.findById(UUID.randomUUID()))
    }

    @Test
    fun `save is idempotent via MERGE`() {
        val u = user("dave")
        repo.save(u)
        val updated = u.copy(email = "dave2@example.com")
        repo.save(updated)
        val found = repo.findById(u.id)!!
        assertEquals("dave2@example.com", found.email)
    }

    @Test
    fun `findAll returns all users ordered by username`() {
        repo.save(user("zara"))
        repo.save(user("alice"))
        repo.save(user("mike"))
        val all = repo.findAll()
        assertEquals(3, all.size)
        assertEquals("alice", all[0].username)
        assertEquals("mike", all[1].username)
        assertEquals("zara", all[2].username)
    }

    @Test
    fun `updateRole changes role`() {
        val u = user("eve", role = UserRole.USER)
        repo.save(u)
        repo.updateRole(u.id, UserRole.ADMIN)
        assertEquals(UserRole.ADMIN, repo.findById(u.id)!!.role)
    }

    @Test
    fun `updateEnabled disables and re-enables user`() {
        val u = user("frank", enabled = true)
        repo.save(u)
        repo.updateEnabled(u.id, false)
        assertFalse(repo.findById(u.id)!!.enabled)
        repo.updateEnabled(u.id, true)
        assertTrue(repo.findById(u.id)!!.enabled)
    }

    @Test
    fun `updateLastActivity sets timestamp`() {
        val u = user("grace")
        repo.save(u)
        // Reset to a known past time so we can verify the update
        dsl.execute(
            "UPDATE users SET last_activity_at = '2000-01-01 00:00:00' WHERE id = '${u.id}'"
        )
        repo.updateLastActivity(u.id)
        val updated = repo.findById(u.id)!!.lastActivityAt
        assertNotNull(updated)
        assertTrue(updated!! > Instant.parse("2000-01-02T00:00:00Z"))
    }

    @Test
    fun `seedAdminUser creates admin if missing`() {
        repo.seedAdminUser("hashed")
        val admin = repo.findByUsername("admin")!!
        assertEquals(UserRole.ADMIN, admin.role)
        assertEquals("hashed", admin.passwordHash)
    }

    @Test
    fun `seedAdminUser is idempotent`() {
        repo.seedAdminUser("hash1")
        repo.seedAdminUser("hash2")
        val admins = repo.findAll().filter { it.username == "admin" }
        assertEquals(1, admins.size)
        assertEquals("hash1", admins[0].passwordHash)
    }

    @Test
    fun `deleteById removes user`() {
        val u = user("henry")
        repo.save(u)
        assertNotNull(repo.findById(u.id))
        repo.deleteById(u.id)
        assertNull(repo.findById(u.id))
    }

    @Test
    fun `deleteById on unknown id is a no-op`() {
        repo.deleteById(UUID.randomUUID()) // should not throw
    }

    @Test
    fun `updateUsername changes the username`() {
        val u = user("ivan")
        repo.save(u)
        repo.updateUsername(u.id, "ivan_updated")
        val found = repo.findById(u.id)!!
        assertEquals("ivan_updated", found.username)
        assertNotNull(repo.findByUsername("ivan_updated"))
        assertNull(repo.findByUsername("ivan"))
    }

    @Test
    fun `updateAvatarUrl sets and clears avatar`() {
        val u = user("julia")
        repo.save(u)

        repo.updateAvatarUrl(u.id, "https://example.com/avatar.png")
        assertEquals("https://example.com/avatar.png", repo.findById(u.id)!!.avatarUrl)

        repo.updateAvatarUrl(u.id, null)
        assertNull(repo.findById(u.id)!!.avatarUrl)
    }

    @Test
    fun `updateNotificationPreferences persists email and push flags`() {
        val u = user("karen")
        repo.save(u)

        // defaults should be true
        assertTrue(repo.findById(u.id)!!.emailNotificationsEnabled)
        assertTrue(repo.findById(u.id)!!.pushNotificationsEnabled)

        repo.updateNotificationPreferences(u.id, emailEnabled = false, pushEnabled = false)
        val updated = repo.findById(u.id)!!
        assertFalse(updated.emailNotificationsEnabled)
        assertFalse(updated.pushNotificationsEnabled)

        repo.updateNotificationPreferences(u.id, emailEnabled = true, pushEnabled = false)
        val partial = repo.findById(u.id)!!
        assertTrue(partial.emailNotificationsEnabled)
        assertFalse(partial.pushNotificationsEnabled)
    }
}
