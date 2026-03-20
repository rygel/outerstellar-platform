package dev.outerstellar.platform.swing

import dev.outerstellar.platform.model.AuthTokenResponse
import dev.outerstellar.platform.model.SessionExpiredException
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.swing.viewmodel.SyncViewModel
import dev.outerstellar.platform.sync.SyncService
import io.github.rygel.outerstellar.i18n.I18nService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for session expiry handling in SyncViewModel admin operations.
 *
 * Covers:
 * - loadUsers throwing SessionExpiredException sets isLoggedIn=false
 * - loadUsers session expiry clears userName and userRole
 * - loadUsers session expiry notifies observers
 * - toggleUserEnabled throwing SessionExpiredException sets isLoggedIn=false
 * - toggleUserEnabled session expiry clears userName and userRole
 * - toggleUserRole throwing SessionExpiredException sets isLoggedIn=false
 * - toggleUserRole session expiry clears userName and userRole
 * - changePassword throwing SessionExpiredException sets isLoggedIn=false
 * - Non-session errors in loadUsers do not log out the user
 */
class SyncViewModelSessionExpiryTest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    /** Login the VM and wait for login to complete. Returns the VM. */
    private fun loginVm(): SyncViewModel {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("token", "alice", "ADMIN")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "Login timed out")
        assertTrue(vm.isLoggedIn, "Should be logged in after login")
        return vm
    }

    // ---- loadUsers ----

    @Test
    fun `loadUsers SessionExpiredException sets isLoggedIn to false`() {
        every { syncService.listUsers() } throws SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Observer not notified after loadUsers expiry")
        assertFalse(vm.isLoggedIn, "isLoggedIn should be false after session expiry")
    }

    @Test
    fun `loadUsers SessionExpiredException clears userName and userRole`() {
        every { syncService.listUsers() } throws SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.userName.isEmpty(), "userName should be cleared after session expiry")
        assertTrue(vm.userRole == null, "userRole should be null after session expiry")
    }

    @Test
    fun `loadUsers SessionExpiredException notifies observers`() {
        every { syncService.listUsers() } throws SessionExpiredException("Session expired")

        val vm = loginVm()
        var notified = false
        val latch = CountDownLatch(1)
        vm.addObserver {
            notified = true
            latch.countDown()
        }

        vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(notified, "Observers should be notified on session expiry")
    }

    @Test
    fun `loadUsers non-session error does not log out user`() {
        every { syncService.listUsers() } throws RuntimeException("Network error")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.isLoggedIn, "Non-session error should not log out the user")
        assertEquals("alice", vm.userName, "userName should be preserved on non-session error")
    }

    // ---- toggleUserEnabled ----

    @Test
    fun `toggleUserEnabled SessionExpiredException sets isLoggedIn to false`() {
        every { syncService.setUserEnabled(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserEnabled("user-123", true)

        assertTrue(
            latch.await(3, TimeUnit.SECONDS),
            "Observer not notified after toggleUserEnabled expiry",
        )
        assertFalse(vm.isLoggedIn, "isLoggedIn should be false after session expiry")
    }

    @Test
    fun `toggleUserEnabled SessionExpiredException clears user state`() {
        every { syncService.setUserEnabled(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserEnabled("user-123", false)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.userName.isEmpty(), "userName should be cleared")
        assertTrue(vm.userRole == null, "userRole should be null")
    }

    // ---- toggleUserRole ----

    @Test
    fun `toggleUserRole SessionExpiredException sets isLoggedIn to false`() {
        every { syncService.setUserRole(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserRole("user-456", "USER")

        assertTrue(
            latch.await(3, TimeUnit.SECONDS),
            "Observer not notified after toggleUserRole expiry",
        )
        assertFalse(vm.isLoggedIn, "isLoggedIn should be false after session expiry")
    }

    @Test
    fun `toggleUserRole SessionExpiredException clears user state`() {
        every { syncService.setUserRole(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserRole("user-456", "ADMIN")

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.userName.isEmpty(), "userName should be cleared")
        assertTrue(vm.userRole == null, "userRole should be null")
    }

    // ---- changePassword ----

    @Test
    fun `changePassword SessionExpiredException sets isLoggedIn to false`() {
        every { syncService.changePassword(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.changePassword("oldPass", "newPass") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "changePassword callback timed out")
        assertFalse(vm.isLoggedIn, "isLoggedIn should be false after changePassword session expiry")
    }

    @Test
    fun `changePassword SessionExpiredException returns false with error message`() {
        every { syncService.changePassword(any(), any()) } throws
            SessionExpiredException("Session expired")

        val vm = loginVm()
        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        vm.changePassword("oldPass", "newPass") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success, "Should return false on session expiry")
        val errorMsg = error
        assertTrue(
            errorMsg != null && errorMsg.isNotBlank(),
            "Should have an error message on session expiry",
        )
    }

    @Test
    fun `changePassword success does not affect login state`() {
        every { syncService.changePassword(any(), any()) } just runs

        val vm = loginVm()
        val latch = CountDownLatch(1)
        vm.changePassword("oldPass", "StrongNewPass1!") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.isLoggedIn, "User should remain logged in after successful password change")
        assertEquals("alice", vm.userName, "userName should be preserved")
    }
}
