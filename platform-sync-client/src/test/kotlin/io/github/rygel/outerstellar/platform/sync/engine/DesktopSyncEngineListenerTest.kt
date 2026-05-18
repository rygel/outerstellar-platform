@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineListenerTest : DesktopSyncEngineTestBase() {

    @Test
    fun `connectivity observer updates isOnline state`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        connectivityObserver?.invoke(false)

        assertFalse(engine.state.isOnline)
        verify { listener.onStateChanged(engine.state) }

        connectivityObserver?.invoke(true)

        assertTrue(engine.state.isOnline)
    }

    @Test
    fun `addListener receives onStateChanged`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.login("a", "b") } returns AuthTokenResponse("t", "a", "USER")
        engine.login("a", "b")

        verify(atLeast = 1) { listener.onStateChanged(any()) }
    }

    @Test
    fun `onSessionExpired fired on session expiry`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.listNotifications() } throws SessionExpiredException()
        engine.loadNotifications()

        verify { listener.onSessionExpired() }
        verify { notifier.notifyFailure("Session expired. Please log in again.") }
    }

    @Test
    fun `removeListener stops receiving events`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        engine.removeListener(listener)

        every { syncService.login("a", "b") } returns AuthTokenResponse("t", "a", "USER")
        engine.login("a", "b")

        verify(exactly = 0) { listener.onStateChanged(any()) }
    }

    @Test
    fun `shutdown clears listeners and stops auto-sync`() {
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        engine.startAutoSync()

        engine.shutdown()

        assertTrue((engine as DesktopSyncEngine).listeners.isEmpty())
        verify { connectivityChecker.stop() }
    }

    @Test
    fun `requestPasswordReset success`() {
        val result = engine.requestPasswordReset("a@b.c")

        assertTrue(result.isSuccess)
        verify { syncService.requestPasswordReset("a@b.c") }
        verify { notifier.notifySuccess("Password reset email sent") }
    }

    @Test
    fun `requestPasswordReset failure`() {
        every { syncService.requestPasswordReset(any()) } throws RuntimeException("Fail")

        val result = engine.requestPasswordReset("a@b.c")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset request failed: Fail") }
    }

    @Test
    fun `resetPassword success`() {
        val result = engine.resetPassword("token123", "newpass")

        assertTrue(result.isSuccess)
        verify { syncService.resetPassword("token123", "newpass") }
        verify { notifier.notifySuccess("Password has been reset") }
    }

    @Test
    fun `resetPassword failure`() {
        every { syncService.resetPassword(any(), any()) } throws RuntimeException("Expired")

        val result = engine.resetPassword("tok", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset failed: Expired") }
    }

    @Test
    fun `resolveConflict success`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.MINE) } returns Unit
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        engine.resolveConflict("s1", ConflictStrategy.MINE)

        verify { messageService.resolveConflict("s1", ConflictStrategy.MINE) }
    }

    @Test
    fun `resolveConflict failure fires error`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.SERVER) } throws
            RuntimeException("No conflict data")

        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        engine.resolveConflict("s1", ConflictStrategy.SERVER)

        verify { listener.onError("resolveConflict", "No conflict data") }
    }

    @Test
    fun `handleSessionExpired clears login state and notifies listeners`() {
        stubLoggedIn()
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)

        every { syncService.fetchProfile() } throws SessionExpiredException()
        engine.loadProfile()

        assertFalse(engine.state.isLoggedIn)
        assertEquals("Session expired", engine.state.status)
        verify { listener.onSessionExpired() }
        verify { syncService.logout() }
        verify { notifier.notifyFailure("Session expired. Please log in again.") }
    }

    @Test
    fun `startConnectivityChecker delegates to checker`() {
        engine.startConnectivityChecker()
        verify { connectivityChecker.start() }
    }

    @Test
    fun `stopConnectivityChecker delegates to checker`() {
        engine.stopConnectivityChecker()
        verify { connectivityChecker.stop() }
    }

    @Test
    fun `initial state has defaults`() {
        val freshEngine =
            DesktopSyncEngine(syncService = syncService, messageService = messageService, analytics = analytics)

        assertFalse(freshEngine.state.isLoggedIn)
        assertEquals("", freshEngine.state.userName)
        assertNull(freshEngine.state.userRole)
        assertTrue(freshEngine.state.isOnline)
        assertFalse(freshEngine.state.isSyncing)
        assertEquals("", freshEngine.state.status)
        assertTrue(freshEngine.state.messages.isEmpty())
        assertTrue(freshEngine.state.contacts.isEmpty())
    }
}
