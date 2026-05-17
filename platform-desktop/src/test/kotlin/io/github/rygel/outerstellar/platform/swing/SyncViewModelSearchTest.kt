package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.SyncEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelSearchTest {
    private val engine = mockk<SyncEngine>(relaxed = true)
    private val i18n = mockk<I18nService>(relaxed = true)

    @Test
    fun `searchQuery debounces rapid keystrokes`() {
        val latch = CountDownLatch(1)
        every { engine.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val viewModel = SyncViewModel(engine, i18n)
        viewModel.searchQuery = "h"
        viewModel.searchQuery = "he"
        viewModel.searchQuery = "hel"
        viewModel.searchQuery = "hell"
        viewModel.searchQuery = "hello"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) { engine.loadMessages() }
    }

    @Test
    fun `searchQuery initial set triggers loadMessages`() {
        val latch = CountDownLatch(1)
        every { engine.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val viewModel = SyncViewModel(engine, i18n)
        viewModel.searchQuery = "test"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) { engine.loadMessages() }
    }

    @Test
    fun `searchQuery same value does not trigger additional loadMessages`() {
        val latch = CountDownLatch(1)
        every { engine.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val viewModel = SyncViewModel(engine, i18n)
        viewModel.searchQuery = "test"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        viewModel.searchQuery = "test"
        SwingUtilities.invokeAndWait {}
        verify(exactly = 1) { engine.loadMessages() }
    }
}
