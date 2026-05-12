@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.mockk.every
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineAdminTest : DesktopSyncEngineTestBase() {

    @Test
    fun `loadUsers success updates state`() {
        val users = listOf(UserSummary("1", "alice", "a@b.c", UserRole.USER, true))
        every { syncService.listUsers() } returns users

        engine.loadUsers()

        assertEquals(1, engine.state.adminUsers.size)
        assertEquals("alice", engine.state.adminUsers[0].username)
    }

    @Test
    fun `loadUsers session expired fires handleSessionExpired`() {
        every { syncService.listUsers() } throws SessionExpiredException()

        engine.loadUsers()

        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `setUserEnabled success`() {
        stubLoggedIn()
        every { syncService.setUserEnabled("1", false) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val result = engine.setUserEnabled("1", false)

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_enabled_changed", mapOf("userId" to "1", "enabled" to false)) }
    }

    @Test
    fun `setUserEnabled session expired`() {
        every { syncService.setUserEnabled("1", true) } throws SessionExpiredException()

        val result = engine.setUserEnabled("1", true)

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `setUserRole success`() {
        stubLoggedIn()
        every { syncService.setUserRole("1", "ADMIN") } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val result = engine.setUserRole("1", "ADMIN")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_role_changed", mapOf("userId" to "1", "role" to "ADMIN")) }
    }

    @Test
    fun `setUserRole failure`() {
        every { syncService.setUserRole("1", "ADMIN") } throws RuntimeException("Fail")

        val result = engine.setUserRole("1", "ADMIN")

        assertTrue(result.isFailure)
    }
}
