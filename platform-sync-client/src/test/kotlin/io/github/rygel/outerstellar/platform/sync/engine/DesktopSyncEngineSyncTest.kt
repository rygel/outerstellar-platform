@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineSyncTest : DesktopSyncEngineTestBase() {

    @Test
    fun `sync success updates state and tracks analytics`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 3, pulled = 2, conflicts = 1)

        val result = engine.sync()

        assertTrue(result.isSuccess)
        assertFalse(engine.state.isSyncing)
        assertEquals("Synced: pushed 3, pulled 2, 1 conflict(s)", engine.state.status)
        verify { analytics.track("user", "manual_sync") }
        verify { notifier.notifySuccess(engine.state.status) }
    }

    @Test
    fun `sync when offline returns failure`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Offline", result.exceptionOrNull()?.message)
        verify { notifier.notifyFailure("Cannot sync while offline") }
    }

    @Test
    fun `sync when not logged in returns failure`() {
        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync when already syncing is no-op`() {
        stubLoggedIn()
        every { syncService.sync() } answers
            {
                Thread.sleep(100)
                io.github.rygel.outerstellar.platform.sync.SyncStats()
            }

        val results = mutableListOf<Result<Unit>>()
        val t1 = Thread { results.add(engine.sync()) }
        val t2 = Thread { results.add(engine.sync()) }
        t1.start()
        Thread.sleep(20)
        t2.start()
        t1.join()
        t2.join()

        assertTrue(results.all { it.isSuccess })
        verify(exactly = 1) { syncService.sync() }
    }

    @Test
    fun `sync failure calls notifier with failure`() {
        stubLoggedIn()
        every { syncService.sync() } throws RuntimeException("Network error")

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertFalse(engine.state.isSyncing)
        assertEquals("Sync failed: Network error", engine.state.status)
        verify { notifier.notifyFailure("Sync failed: Network error") }
    }

    @Test
    fun `sync session expired fires handleSessionExpired`() {
        stubLoggedIn()
        every { syncService.sync() } throws SessionExpiredException()

        val result = engine.sync()

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
        assertFalse(engine.state.isLoggedIn)
    }

    @Test
    fun `sync auto mode does not notify`() {
        stubLoggedIn()
        clearMocks(notifier)
        stubSyncSuccess(pushed = 1)

        val result = engine.sync(isAuto = true)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { notifier.notifySuccess(any()) }
        verify { analytics.track("user", "auto_sync") }
    }

    @Test
    fun `startAutoSync creates executor and stopAutoSync shuts down`() {
        engine.startAutoSync()
        engine.stopAutoSync()
    }

    @Test
    fun `double startAutoSync is no-op`() {
        engine.startAutoSync()
        engine.startAutoSync()
        engine.stopAutoSync()
    }

    @Test
    fun `sync with zero stats shows up to date`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 0, pulled = 0, conflicts = 0)

        engine.sync()

        assertEquals("Everything up to date", engine.state.status)
    }

    @Test
    fun `sync offline auto mode does not notify`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = engine.sync(isAuto = true)

        assertTrue(result.isFailure)
        verify(exactly = 0) { notifier.notifyFailure(any()) }
    }

    @Test
    fun `sync failure auto mode does not fire error to listeners`() {
        stubLoggedIn()
        val listener = mockk<EngineListener>(relaxed = true)
        engine.addListener(listener)
        every { syncService.sync() } throws RuntimeException("fail")

        engine.sync(isAuto = true)

        verify(exactly = 0) { listener.onError(any(), any()) }
    }
}
