package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.UserRole
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CachingUserRepositoryTest {

    private val id = UUID.randomUUID()
    private val user = User(id, "alice", "alice@example.com", "hash", UserRole.USER)

    private fun delegate(vararg results: User?) =
        mockk<UserRepository> {
            every { findById(id) } returnsMany results.toList()
            every { save(any()) } just runs
            every { updateRole(any(), any()) } just runs
            every { updateEnabled(any(), any()) } just runs
            every { deleteById(any()) } just runs
            every { updateUsername(any(), any()) } just runs
            every { updateAvatarUrl(any(), any()) } just runs
            every { updateNotificationPreferences(any(), any(), any()) } just runs
        }

    @Test
    fun `findById returns cached result on second call`() {
        val delegate = delegate(user, user)
        val caching = CachingUserRepository(delegate)

        caching.findById(id)
        caching.findById(id)

        verify(exactly = 1) { delegate.findById(id) }
    }

    @Test
    fun `findById returns null when delegate returns null`() {
        val delegate = delegate(null)
        val caching = CachingUserRepository(delegate)

        assertNull(caching.findById(id))
    }

    @Test
    fun `save invalidates cache so next findById re-fetches`() {
        val updated = user.copy(username = "bob")
        val delegate = delegate(user, updated)
        val caching = CachingUserRepository(delegate)

        assertEquals("alice", caching.findById(id)?.username)
        caching.save(updated)
        assertEquals("bob", caching.findById(id)?.username)

        verify(exactly = 2) { delegate.findById(id) }
    }

    @Test
    fun `updateRole invalidates cache`() {
        val delegate = delegate(user, user.copy(role = UserRole.ADMIN))
        val caching = CachingUserRepository(delegate)

        caching.findById(id)
        caching.updateRole(id, UserRole.ADMIN)
        caching.findById(id)

        verify(exactly = 2) { delegate.findById(id) }
    }

    @Test
    fun `updateEnabled invalidates cache`() {
        val delegate = delegate(user, user.copy(enabled = false))
        val caching = CachingUserRepository(delegate)

        caching.findById(id)
        caching.updateEnabled(id, false)
        caching.findById(id)

        verify(exactly = 2) { delegate.findById(id) }
    }

    @Test
    fun `deleteById invalidates cache`() {
        val delegate = delegate(user, null)
        val caching = CachingUserRepository(delegate)

        caching.findById(id)
        caching.deleteById(id)
        caching.findById(id)

        verify(exactly = 2) { delegate.findById(id) }
    }

    @Test
    fun `different user IDs are cached independently`() {
        val id2 = UUID.randomUUID()
        val user2 = user.copy(id = id2, username = "bob")
        val delegate =
            mockk<UserRepository> {
                every { findById(id) } returns user
                every { findById(id2) } returns user2
            }
        val caching = CachingUserRepository(delegate)

        caching.findById(id)
        caching.findById(id)
        caching.findById(id2)
        caching.findById(id2)

        verify(exactly = 1) { delegate.findById(id) }
        verify(exactly = 1) { delegate.findById(id2) }
    }
}
