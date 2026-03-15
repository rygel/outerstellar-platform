package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.SessionExpiredException
import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.AuthTokenResponse
import dev.outerstellar.starter.web.UserProfileResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for profile operations in SyncViewModel.
 *
 * Covers:
 * - loadProfile populates userEmail, userAvatarUrl, notification flags
 * - loadProfile on session expiry logs out
 * - loadProfile on generic error reports error but stays logged in
 * - updateProfile calls syncService and reloads profile state
 * - updateProfile on session expiry logs out
 * - updateProfile on SyncException propagates error message
 * - updateNotificationPreferences persists flags in ViewModel
 * - updateNotificationPreferences on session expiry logs out
 * - deleteAccount clears session state on success
 * - deleteAccount on SyncException propagates error, keeps session
 */
class SyncViewModelProfileTest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private fun loginVm(): SyncViewModel {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("token", "alice", "USER")
        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Login timed out")
        return vm
    }

    private fun awaitCallback(
        action: ((Boolean, String?) -> Unit) -> Unit
    ): Pair<Boolean, String?> {
        val latch = CountDownLatch(1)
        var result: Pair<Boolean, String?> = false to null
        action { success, error ->
            result = success to error
            latch.countDown()
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Callback timed out")
        return result
    }

    private fun stubProfile(
        username: String = "alice",
        email: String = "alice@test.com",
        avatarUrl: String? = null,
        emailNotif: Boolean = true,
        pushNotif: Boolean = true,
    ) = UserProfileResponse(username, email, avatarUrl, emailNotif, pushNotif)

    // ── loadProfile ───────────────────────────────────────────────────────────

    @Test
    fun `loadProfile populates userEmail from profile`() {
        every { syncService.fetchProfile() } returns stubProfile(email = "alice@example.com")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.loadProfile(it) }

        assertTrue(success)
        assertNull(error)
        assertEquals("alice@example.com", vm.userEmail)
    }

    @Test
    fun `loadProfile sets avatarUrl from profile`() {
        every { syncService.fetchProfile() } returns
            stubProfile(avatarUrl = "https://example.com/avatar.png")
        val vm = loginVm()

        awaitCallback { vm.loadProfile(it) }

        assertEquals("https://example.com/avatar.png", vm.userAvatarUrl)
    }

    @Test
    fun `loadProfile sets notification flags from profile`() {
        every { syncService.fetchProfile() } returns
            stubProfile(emailNotif = false, pushNotif = true)
        val vm = loginVm()

        awaitCallback { vm.loadProfile(it) }

        assertFalse(vm.emailNotificationsEnabled)
        assertTrue(vm.pushNotificationsEnabled)
    }

    @Test
    fun `loadProfile notifies observers on completion`() {
        every { syncService.fetchProfile() } returns stubProfile()
        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.loadProfile { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `loadProfile on session expiry logs out`() {
        every { syncService.fetchProfile() } throws SessionExpiredException()
        val vm = loginVm()

        val (success, _) = awaitCallback { vm.loadProfile(it) }

        assertFalse(success)
        assertFalse(vm.isLoggedIn)
    }

    @Test
    fun `loadProfile on generic error stays logged in and reports error`() {
        every { syncService.fetchProfile() } throws RuntimeException("Network error")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.loadProfile(it) }

        assertFalse(success)
        assertTrue(vm.isLoggedIn, "User should remain logged in after non-session error")
        assertEquals("Network error", error)
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    fun `updateProfile calls syncService and reloads profile`() {
        every { syncService.updateProfile("new@test.com", "alice2", null) } returns Unit
        every { syncService.fetchProfile() } returns
            stubProfile(username = "alice2", email = "new@test.com")
        val vm = loginVm()

        val (success, error) =
            awaitCallback { vm.updateProfile("new@test.com", "alice2", null, it) }

        assertTrue(success)
        assertNull(error)
        verify { syncService.updateProfile("new@test.com", "alice2", null) }
        verify(atLeast = 1) { syncService.fetchProfile() }
        assertEquals("alice2", vm.userName)
        assertEquals("new@test.com", vm.userEmail)
    }

    @Test
    fun `updateProfile notifies observers on success`() {
        every { syncService.updateProfile(any(), any(), any()) } returns Unit
        every { syncService.fetchProfile() } returns stubProfile()
        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.updateProfile("a@b.com", null, null) { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `updateProfile on session expiry logs out`() {
        every { syncService.updateProfile(any(), any(), any()) } throws SessionExpiredException()
        val vm = loginVm()

        val (success, _) = awaitCallback { vm.updateProfile("a@b.com", null, null, it) }

        assertFalse(success)
        assertFalse(vm.isLoggedIn)
    }

    @Test
    fun `updateProfile on SyncException propagates error message`() {
        every { syncService.updateProfile(any(), any(), any()) } throws
            SyncException("Username already taken")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.updateProfile("a@b.com", "taken", null, it) }

        assertFalse(success)
        assertEquals("Username already taken", error)
        assertTrue(vm.isLoggedIn)
    }

    // ── updateNotificationPreferences ─────────────────────────────────────────

    @Test
    fun `updateNotificationPreferences stores flags in ViewModel`() {
        every { syncService.updateNotificationPreferences(false, true) } returns Unit
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.updateNotificationPreferences(false, true, it) }

        assertTrue(success)
        assertNull(error)
        assertFalse(vm.emailNotificationsEnabled)
        assertTrue(vm.pushNotificationsEnabled)
    }

    @Test
    fun `updateNotificationPreferences notifies observers`() {
        every { syncService.updateNotificationPreferences(any(), any()) } returns Unit
        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.updateNotificationPreferences(true, false) { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `updateNotificationPreferences on session expiry logs out`() {
        every { syncService.updateNotificationPreferences(any(), any()) } throws
            SessionExpiredException()
        val vm = loginVm()

        val (success, _) = awaitCallback { vm.updateNotificationPreferences(true, true, it) }

        assertFalse(success)
        assertFalse(vm.isLoggedIn)
    }

    @Test
    fun `updateNotificationPreferences on SyncException stays logged in`() {
        every { syncService.updateNotificationPreferences(any(), any()) } throws
            SyncException("Server error")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.updateNotificationPreferences(true, true, it) }

        assertFalse(success)
        assertEquals("Server error", error)
        assertTrue(vm.isLoggedIn)
    }

    // ── deleteAccount ─────────────────────────────────────────────────────────

    @Test
    fun `deleteAccount clears session state on success`() {
        every { syncService.deleteAccount() } returns Unit
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.deleteAccount(it) }

        assertTrue(success)
        assertNull(error)
        assertFalse(vm.isLoggedIn)
        assertEquals("", vm.userName)
        assertEquals("", vm.userEmail)
        assertNull(vm.userRole)
    }

    @Test
    fun `deleteAccount notifies observers`() {
        every { syncService.deleteAccount() } returns Unit
        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.deleteAccount { _, _ -> }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified")
    }

    @Test
    fun `deleteAccount on SyncException propagates error and keeps session`() {
        every { syncService.deleteAccount() } throws SyncException("Cannot delete only admin")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.deleteAccount(it) }

        assertFalse(success)
        assertEquals("Cannot delete only admin", error)
        assertTrue(vm.isLoggedIn)
    }

    @Test
    fun `deleteAccount on generic error keeps session`() {
        every { syncService.deleteAccount() } throws RuntimeException("Unexpected failure")
        val vm = loginVm()

        val (success, error) = awaitCallback { vm.deleteAccount(it) }

        assertFalse(success)
        assertEquals("Unexpected failure", error)
        assertTrue(vm.isLoggedIn)
    }
}
