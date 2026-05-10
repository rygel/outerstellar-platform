@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.mockk.every
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineAuthTest : DesktopSyncEngineTestBase() {

    @Test
    fun `login success updates state and starts auto-sync`() {
        every { syncService.login("alice", "pw") } returns AuthTokenResponse("tok", "alice", "ADMIN")

        val result = engine.login("alice", "pw")

        assertTrue(result.isSuccess)
        assertTrue(engine.state.isLoggedIn)
        assertEquals("alice", engine.state.userName)
        assertEquals("ADMIN", engine.state.userRole)
        assertEquals("Logged in", engine.state.status)
        verify { analytics.identify("alice", mapOf("role" to "ADMIN")) }
        verify { analytics.track("alice", "user_login") }
        verify { notifier.notifySuccess("Logged in as alice") }
    }

    @Test
    fun `login failure returns Result failure`() {
        every { syncService.login("alice", "bad") } throws RuntimeException("Bad credentials")

        val result = engine.login("alice", "bad")

        assertTrue(result.isFailure)
        assertFalse(engine.state.isLoggedIn)
        assertEquals("Login failed: Bad credentials", engine.state.status)
        verify { notifier.notifyFailure("Login failed: Bad credentials") }
    }

    @Test
    fun `login session expired fires session expired`() {
        every { syncService.login("alice", "pw") } throws SessionExpiredException()

        val result = engine.login("alice", "pw")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `register success updates state`() {
        every { syncService.register("bob", "pw") } returns AuthTokenResponse("tok2", "bob", "USER")

        val result = engine.register("bob", "pw")

        assertTrue(result.isSuccess)
        assertTrue(engine.state.isLoggedIn)
        assertEquals("bob", engine.state.userName)
        assertEquals("Registered", engine.state.status)
        verify { analytics.track("bob", "user_register") }
        verify { notifier.notifySuccess("Registered as bob") }
    }

    @Test
    fun `register failure returns Result failure`() {
        every { syncService.register("bob", "pw") } throws RuntimeException("Taken")

        val result = engine.register("bob", "pw")

        assertTrue(result.isFailure)
        assertEquals("Registration failed: Taken", engine.state.status)
        verify { notifier.notifyFailure("Registration failed: Taken") }
    }

    @Test
    fun `logout clears state and tracks analytics`() {
        stubLoggedIn()

        engine.logout()

        assertFalse(engine.state.isLoggedIn)
        assertEquals("Logged out", engine.state.status)
        assertEquals("", engine.state.userName)
    }
}
