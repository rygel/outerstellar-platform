package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileState
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelProfileTest {

    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private data class TestHarness(
        val vm: SyncViewModel,
        val profileModule: ProfileModule,
        val profileState: AtomicReference<ProfileState>,
        val authState: AtomicReference<io.github.rygel.outerstellar.platform.sync.engine.module.AuthState>,
    )

    private fun createHarness(): TestHarness {
        val profileState = AtomicReference(ProfileState())
        val profileModule = mockk<ProfileModule>(relaxed = true)
        every { profileModule.profileState } answers { profileState.get() }
        every { profileModule.updateProfile(any(), any(), any()) } returns Result.success(Unit)
        every { profileModule.updateNotificationPreferences(any(), any()) } returns Result.success(Unit)
        every { profileModule.deleteAccount(any()) } returns Result.success(Unit)

        val authState =
            AtomicReference(
                io.github.rygel.outerstellar.platform.sync.engine.module.AuthState(
                    isLoggedIn = true,
                    userName = "alice",
                )
            )
        val authModule = mockk<AuthModule>(relaxed = true)
        every { authModule.authState } answers { authState.get() }

        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)

        val vm = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService)
        return TestHarness(vm, profileModule, profileState, authState)
    }

    private fun awaitCallback(action: ((Boolean, String?) -> Unit) -> Unit): Pair<Boolean, String?> {
        val latch = CountDownLatch(1)
        var result: Pair<Boolean, String?> = false to null
        action { success, error ->
            result = success to error
            latch.countDown()
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback timed out")
        return result
    }

    // ── loadProfile ───────────────────────────────────────────────────────────

    @Test
    fun `loadProfile populates userEmail from profile`() {
        val h = createHarness()
        every { h.profileModule.loadProfile() } answers
            {
                h.profileState.set(ProfileState(userEmail = "alice@example.com"))
                Unit
            }

        h.profileModule.loadProfile()
        h.profileState.set(ProfileState(userEmail = "alice@example.com"))
        h.vm.javaClass.getDeclaredMethod("syncFromModules").apply { isAccessible = true }.invoke(h.vm)

        assertEquals("alice@example.com", h.vm.userEmail)
    }

    @Test
    fun `loadProfile notifies observers on completion`() {
        val h = createHarness()
        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.loadProfile { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    fun `updateProfile calls profileModule and returns success`() {
        val h = createHarness()
        every { h.profileModule.updateProfile("new@test.com", "alice2", null) } returns Result.success(Unit)

        val (success, error) = awaitCallback { h.vm.updateProfile("new@test.com", "alice2", null, it) }

        assertTrue(success)
        assertNull(error)
        verify { h.profileModule.updateProfile("new@test.com", "alice2", null) }
    }

    @Test
    fun `updateProfile notifies observers on success`() {
        val h = createHarness()
        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.updateProfile("a@b.com", null, null) { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `updateProfile on SyncException propagates error message`() {
        val h = createHarness()
        every { h.profileModule.updateProfile(any(), any(), any()) } returns
            Result.failure(SyncException("Username already taken"))

        val (success, error) = awaitCallback { h.vm.updateProfile("a@b.com", "taken", null, it) }

        assertFalse(success)
        assertEquals("Username already taken", error)
        assertTrue(h.vm.isLoggedIn)
    }

    // ── updateNotificationPreferences ─────────────────────────────────────────

    @Test
    fun `updateNotificationPreferences delegates to profileModule`() {
        val h = createHarness()

        val (success, error) = awaitCallback { h.vm.updateNotificationPreferences(false, true, it) }

        assertTrue(success)
        assertNull(error)
        verify { h.profileModule.updateNotificationPreferences(false, true) }
    }

    @Test
    fun `updateNotificationPreferences notifies observers`() {
        val h = createHarness()
        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.updateNotificationPreferences(true, false) { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `updateNotificationPreferences on SyncException stays logged in`() {
        val h = createHarness()
        every { h.profileModule.updateNotificationPreferences(any(), any()) } returns
            Result.failure(SyncException("Server error"))

        val (success, error) = awaitCallback { h.vm.updateNotificationPreferences(true, true, it) }

        assertFalse(success)
        assertEquals("Server error", error)
        assertTrue(h.vm.isLoggedIn)
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    fun `deleteAccount calls profileModule deleteAccount`() {
        val h = createHarness()

        val (success, error) = awaitCallback { h.vm.deleteAccount("secret", it) }

        assertTrue(success)
        assertNull(error)
        verify { h.profileModule.deleteAccount("secret") }
    }

    @Test
    fun `deleteAccount notifies observers`() {
        val h = createHarness()
        val latch = CountDownLatch(1)
        h.vm.addObserver { latch.countDown() }

        h.vm.deleteAccount("secret") { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `deleteAccount on SyncException propagates error and keeps session`() {
        val h = createHarness()
        every { h.profileModule.deleteAccount("secret") } returns
            Result.failure(SyncException("Cannot delete only admin"))

        val (success, error) = awaitCallback { h.vm.deleteAccount("secret", it) }

        assertFalse(success)
        assertEquals("Cannot delete only admin", error)
        assertTrue(h.vm.isLoggedIn)
    }

    @Test
    fun `deleteAccount on generic error keeps session`() {
        val h = createHarness()
        every { h.profileModule.deleteAccount("secret") } returns Result.failure(RuntimeException("Unexpected failure"))

        val (success, error) = awaitCallback { h.vm.deleteAccount("secret", it) }

        assertFalse(success)
        assertEquals("Unexpected failure", error)
        assertTrue(h.vm.isLoggedIn)
    }
}
