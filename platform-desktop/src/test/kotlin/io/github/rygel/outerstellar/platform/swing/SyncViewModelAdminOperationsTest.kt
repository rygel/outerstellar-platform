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
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelAdminOperationsTest {

    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private fun createVm(): Pair<SyncViewModel, AdminModule> {
        val adminModule = mockk<AdminModule>(relaxed = true)
        every { adminModule.setUserEnabled(any(), any()) } returns Result.success(Unit)
        every { adminModule.setUserRole(any(), any()) } returns Result.success(Unit)

        val authState =
            io.github.rygel.outerstellar.platform.sync.engine.module.AuthState(
                isLoggedIn = true,
                userName = "alice",
                userRole = "ADMIN",
            )
        val authModule = mockk<AuthModule>(relaxed = true)
        every { authModule.authState } returns authState

        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)

        val vm = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService)
        return vm to adminModule
    }

    private fun awaitObserver(vm: SyncViewModel, action: () -> Unit): Boolean {
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }
        action()
        return latch.await(3, TimeUnit.SECONDS)
    }

    @Test
    fun `loadUsers notifies observers on completion`() {
        val (vm, _) = createVm()
        val notified = awaitObserver(vm) { vm.loadUsers() }
        assertTrue(notified, "Observer should be notified after loadUsers")
    }

    @Test
    fun `toggleUserEnabled calls setUserEnabled with flipped value`() {
        val (vm, adminModule) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = true) })
        verify { adminModule.setUserEnabled("user-1", false) }
    }

    @Test
    fun `toggleUserEnabled when disabled flips to enabled`() {
        val (vm, adminModule) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = false) })
        verify { adminModule.setUserEnabled("user-1", true) }
    }

    @Test
    fun `toggleUserEnabled notifies observers`() {
        val (vm, _) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = false) })
    }

    @Test
    fun `toggleUserRole promotes USER to ADMIN`() {
        val (vm, adminModule) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") })
        verify { adminModule.setUserRole("user-1", "ADMIN") }
    }

    @Test
    fun `toggleUserRole demotes ADMIN to USER`() {
        val (vm, adminModule) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "ADMIN") })
        verify { adminModule.setUserRole("user-1", "USER") }
    }

    @Test
    fun `toggleUserRole notifies observers`() {
        val (vm, _) = createVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") })
    }
}
