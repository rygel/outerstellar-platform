@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import io.github.rygel.outerstellar.platform.sync.engine.SessionLifecycle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AdminModuleTest {

    private lateinit var adminClient: AdminClient
    private lateinit var analytics: AnalyticsService
    private lateinit var lifecycle: SessionLifecycle
    private lateinit var module: AdminModuleImpl

    private var authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")

    @BeforeEach
    fun setUp() {
        adminClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        lifecycle = mockk(relaxed = true)
        every { lifecycle.authState } answers { authState }
        module = AdminModuleImpl(adminClient = adminClient, analytics = analytics, lifecycle = lifecycle)
    }

    @Test
    fun `loadUsers success updates state`() {
        val users = listOf(UserSummary("1", "alice", "a@b.c", UserRole.USER, true))
        every { adminClient.listUsers() } returns users

        module.loadUsers()

        assertEquals(1, module.adminState.adminUsers.size)
        assertEquals("alice", module.adminState.adminUsers[0].username)
    }

    @Test
    fun `loadUsers session expired clears state`() {
        every { adminClient.listUsers() } throws SessionExpiredException()

        module.loadUsers()

        verify { lifecycle.onSessionExpired() }
    }

    @Test
    fun `setUserEnabled success`() {
        every { adminClient.setUserEnabled("1", false) } returns Unit
        every { adminClient.listUsers() } returns emptyList()

        val result = module.setUserEnabled("1", false)

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_enabled_changed", mapOf("userId" to "1", "enabled" to false)) }
    }

    @Test
    fun `setUserEnabled session expired`() {
        every { adminClient.setUserEnabled("1", true) } throws SessionExpiredException()

        val result = module.setUserEnabled("1", true)

        assertTrue(result.isFailure)
        verify { lifecycle.onSessionExpired() }
    }

    @Test
    fun `setUserRole success`() {
        every { adminClient.setUserRole("1", "ADMIN") } returns Unit
        every { adminClient.listUsers() } returns emptyList()

        val result = module.setUserRole("1", "ADMIN")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_role_changed", mapOf("userId" to "1", "role" to "ADMIN")) }
    }

    @Test
    fun `setUserRole failure`() {
        every { adminClient.setUserRole("1", "ADMIN") } throws RuntimeException("Fail")

        val result = module.setUserRole("1", "ADMIN")

        assertTrue(result.isFailure)
    }
}
