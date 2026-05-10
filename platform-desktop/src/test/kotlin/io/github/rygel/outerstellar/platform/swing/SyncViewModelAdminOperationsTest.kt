package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelAdminOperationsTest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private fun createVm(): SyncViewModel {
        val engine = DesktopSyncEngine(syncService, messageService, null, NoOpAnalyticsService())
        return SyncViewModel(engine, i18nService)
    }

    private fun loginVm(): SyncViewModel {
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("token", "alice", "ADMIN")
        val vm = createVm()
        val latch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Login timed out")
        return vm
    }

    private fun awaitObserver(vm: SyncViewModel, action: () -> Unit): Boolean {
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }
        action()
        return latch.await(3, TimeUnit.SECONDS)
    }

    private fun stubUser(id: String = "user-1", role: String = "USER", enabled: Boolean = true) =
        UserSummary(id = id, username = "bob", email = "bob@test.com", role = role, enabled = enabled)

    // ── loadUsers ────────────────────────────────────────────────────────────

    @Test
    fun `loadUsers populates adminUsers with returned list`() {
        val users = listOf(stubUser("1"), stubUser("2"))
        every { syncService.listUsers() } returns users

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.loadUsers() }, "Observer not notified")

        assertEquals(2, vm.adminUsers.size)
    }

    @Test
    fun `loadUsers with empty result sets adminUsers to empty`() {
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.loadUsers() })

        assertTrue(vm.adminUsers.isEmpty())
    }

    @Test
    fun `loadUsers notifies observers on completion`() {
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        val notified = awaitObserver(vm) { vm.loadUsers() }

        assertTrue(notified, "Observer should be notified after loadUsers")
    }

    @Test
    fun `loadUsers non-session error sets status message but does not log out`() {
        every { syncService.listUsers() } throws RuntimeException("Server error")

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.loadUsers() })

        assertTrue(vm.isLoggedIn, "User should still be logged in after non-session error")
        assertTrue(vm.status.isNotBlank())
    }

    // ── toggleUserEnabled ────────────────────────────────────────────────────

    @Test
    fun `toggleUserEnabled calls setUserEnabled with flipped value`() {
        every { syncService.setUserEnabled(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = true) })

        verify { syncService.setUserEnabled("user-1", false) }
    }

    @Test
    fun `toggleUserEnabled when disabled flips to enabled`() {
        every { syncService.setUserEnabled(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = false) })

        verify { syncService.setUserEnabled("user-1", true) }
    }

    @Test
    fun `toggleUserEnabled reloads user list after toggling`() {
        every { syncService.setUserEnabled(any(), any()) } returns Unit
        val users = listOf(stubUser(enabled = false))
        every { syncService.listUsers() } returns users

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = true) })

        assertEquals(1, vm.adminUsers.size)
        verify(atLeast = 1) { syncService.listUsers() }
    }

    @Test
    fun `toggleUserEnabled non-session error does not log out`() {
        every { syncService.setUserEnabled(any(), any()) } throws RuntimeException("Toggle failed")

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = true) })

        assertTrue(vm.isLoggedIn)
    }

    @Test
    fun `toggleUserEnabled notifies observers`() {
        every { syncService.setUserEnabled(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        val notified = awaitObserver(vm) { vm.toggleUserEnabled("user-1", currentEnabled = false) }

        assertTrue(notified)
    }

    // ── toggleUserRole ────────────────────────────────────────────────────────

    @Test
    fun `toggleUserRole promotes USER to ADMIN`() {
        every { syncService.setUserRole(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") })

        verify { syncService.setUserRole("user-1", "ADMIN") }
    }

    @Test
    fun `toggleUserRole demotes ADMIN to USER`() {
        every { syncService.setUserRole(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "ADMIN") })

        verify { syncService.setUserRole("user-1", "USER") }
    }

    @Test
    fun `toggleUserRole reloads user list after role change`() {
        every { syncService.setUserRole(any(), any()) } returns Unit
        val updated = listOf(stubUser(role = "ADMIN"))
        every { syncService.listUsers() } returns updated

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") })

        assertEquals("ADMIN", vm.adminUsers.first().role)
    }

    @Test
    fun `toggleUserRole non-session error does not log out`() {
        every { syncService.setUserRole(any(), any()) } throws RuntimeException("Role change failed")

        val vm = loginVm()
        assertTrue(awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") })

        assertTrue(vm.isLoggedIn)
    }

    @Test
    fun `toggleUserRole notifies observers`() {
        every { syncService.setUserRole(any(), any()) } returns Unit
        every { syncService.listUsers() } returns emptyList()

        val vm = loginVm()
        val notified = awaitObserver(vm) { vm.toggleUserRole("user-1", currentRole = "USER") }

        assertTrue(notified)
    }
}
