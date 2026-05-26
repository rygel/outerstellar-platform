package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthListener
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelSessionExpiryTest {

    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private data class TestHarness(
        val vm: SyncViewModel,
        val authModule: AuthModule,
        val authState: AtomicReference<io.github.rygel.outerstellar.platform.sync.engine.module.AuthState>,
        val authListenerSlot: io.mockk.CapturingSlot<AuthListener>,
        val adminModule: AdminModule,
    )

    private fun createHarness(): TestHarness {
        val authState =
            AtomicReference(
                io.github.rygel.outerstellar.platform.sync.engine.module.AuthState(
                    isLoggedIn = true,
                    userName = "alice",
                    userRole = "ADMIN",
                )
            )
        val authModule = mockk<AuthModule>(relaxed = true)
        every { authModule.authState } answers { authState.get() }
        val authListenerSlot = slot<AuthListener>()
        every { authModule.addListener(capture(authListenerSlot)) } answers { nothing }
        every { authModule.removeListener(any()) } answers { nothing }
        every { authModule.changePassword(any(), any()) } returns Result.success(Unit)

        val adminModule = mockk<AdminModule>(relaxed = true)
        every { adminModule.setUserEnabled(any(), any()) } returns Result.success(Unit)
        every { adminModule.setUserRole(any(), any()) } returns Result.success(Unit)

        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)

        val vm = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService)
        return TestHarness(vm, authModule, authState, authListenerSlot, adminModule)
    }

    private fun simulateSessionExpiry(h: TestHarness) {
        h.authState.set(io.github.rygel.outerstellar.platform.sync.engine.module.AuthState())
        if (h.authListenerSlot.isCaptured) {
            h.authListenerSlot.captured.onSessionExpired()
        }
    }

    // ---- loadUsers ----

    @Test
    fun `loadUsers SessionExpiredException sets isLoggedIn to false`() {
        val h = createHarness()
        every { h.adminModule.loadUsers() } answers { simulateSessionExpiry(h) }

        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified after loadUsers expiry")
        assertFalse(h.vm.isLoggedIn, "isLoggedIn should be false after session expiry")
    }

    @Test
    fun `loadUsers SessionExpiredException clears userName and userRole`() {
        val h = createHarness()
        every { h.adminModule.loadUsers() } answers { simulateSessionExpiry(h) }

        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(h.vm.userName.isEmpty(), "userName should be cleared after session expiry")
        assertTrue(h.vm.userRole == null, "userRole should be null after session expiry")
    }

    // ---- toggleUserEnabled ----

    @Test
    fun `toggleUserEnabled SessionExpiredException sets isLoggedIn to false`() {
        val h = createHarness()
        every { h.adminModule.setUserEnabled(any(), any()) } answers
            {
                simulateSessionExpiry(h)
                Result.success(Unit)
            }

        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.toggleUserEnabled("user-123", true)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(h.vm.isLoggedIn)
    }

    // ---- toggleUserRole ----

    @Test
    fun `toggleUserRole SessionExpiredException sets isLoggedIn to false`() {
        val h = createHarness()
        every { h.adminModule.setUserRole(any(), any()) } answers
            {
                simulateSessionExpiry(h)
                Result.success(Unit)
            }

        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.toggleUserRole("user-456", "USER")

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(h.vm.isLoggedIn)
    }

    // ---- changePassword ----

    @Test
    fun `changePassword SessionExpiredException sets isLoggedIn to false`() {
        val h = createHarness()
        every { h.authModule.changePassword(any(), any()) } answers
            {
                simulateSessionExpiry(h)
                Result.failure(SessionExpiredException())
            }

        val latch = CountDownLatch(1)
        h.vm.changePassword("oldPass", "newPass") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "changePassword callback timed out")
        assertFalse(h.vm.isLoggedIn)
    }

    @Test
    fun `changePassword success does not affect login state`() {
        val h = createHarness()

        val latch = CountDownLatch(1)
        h.vm.changePassword("oldPass", "StrongNewPass1!") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(h.vm.isLoggedIn, "User should remain logged in after successful password change")
        assertEquals("alice", h.vm.userName, "userName should be preserved")
    }
}
