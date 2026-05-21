package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelSearchTest {
    private val i18n = mockk<I18nService>(relaxed = true)

    private fun createVm(): SyncViewModel {
        val authModule = mockk<AuthModule>(relaxed = true)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        return SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
    }

    @Test
    fun `searchQuery debounces rapid keystrokes`() {
        val latch = CountDownLatch(1)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        every { syncDataModule.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val authModule = mockk<AuthModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        val viewModel = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
        viewModel.searchQuery = "h"
        viewModel.searchQuery = "he"
        viewModel.searchQuery = "hel"
        viewModel.searchQuery = "hell"
        viewModel.searchQuery = "hello"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) { syncDataModule.loadMessages() }
    }

    @Test
    fun `searchQuery initial set triggers loadMessages`() {
        val latch = CountDownLatch(1)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        every { syncDataModule.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val authModule = mockk<AuthModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        val viewModel = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
        viewModel.searchQuery = "test"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        verify(exactly = 1) { syncDataModule.loadMessages() }
    }

    @Test
    fun `searchQuery same value does not trigger additional loadMessages`() {
        val latch = CountDownLatch(1)
        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        every { syncDataModule.loadMessages() } answers
            {
                latch.countDown()
                Unit
            }
        val authModule = mockk<AuthModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)
        val viewModel = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)
        viewModel.searchQuery = "test"
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        viewModel.searchQuery = "test"
        SwingUtilities.invokeAndWait {}
        verify(exactly = 1) { syncDataModule.loadMessages() }
    }
}
