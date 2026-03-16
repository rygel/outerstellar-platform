package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.AuthTokenResponse
import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for password reset flow in SyncViewModel (Swing desktop UI).
 *
 * Covers:
 * - requestPasswordReset success calls callback with true and no error
 * - requestPasswordReset failure propagates error message to callback
 * - requestPasswordReset with unknown email returns success (server always 200, no user leak)
 * - resetPassword success calls callback with true
 * - resetPassword with invalid token returns error
 * - resetPassword with weak password returns error
 * - resetPassword does not log out an authenticated user on failure
 * - requestPasswordReset invokes the sync service method exactly once
 * - resetPassword invokes the sync service method exactly once
 */
class SyncViewModelPasswordResetTest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    // ---- requestPasswordReset ----

    @Test
    fun `requestPasswordReset success calls callback with true and no error`() {
        every { syncService.requestPasswordReset("user@example.com") } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = false
        var error: String? = "initial"

        vm.requestPasswordReset("user@example.com") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "requestPasswordReset callback timed out")
        assertTrue(success, "Success should be true for valid email")
        assertNull(error, "Error should be null on success")
    }

    @Test
    fun `requestPasswordReset failure propagates error message`() {
        every { syncService.requestPasswordReset("user@example.com") } throws
            SyncException("Password reset request failed: 500 INTERNAL_SERVER_ERROR")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        vm.requestPasswordReset("user@example.com") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success)
        val errorMsg = error
        assertNotNull(errorMsg)
        assertTrue(
            errorMsg!!.contains("500") || errorMsg.contains("reset request failed"),
            errorMsg,
        )
    }

    @Test
    fun `requestPasswordReset for unknown email still returns success (server 200)`() {
        // The server always returns 200 for reset requests (no user enumeration)
        // So the client SyncService.requestPasswordReset will succeed
        every { syncService.requestPasswordReset("nobody@unknown.com") } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = false

        vm.requestPasswordReset("nobody@unknown.com") { s, _ ->
            success = s
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(success, "Unknown email still returns success from the server's perspective")
    }

    @Test
    fun `requestPasswordReset invokes sync service once`() {
        every { syncService.requestPasswordReset(any()) } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.requestPasswordReset("user@example.com") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        verify(exactly = 1) { syncService.requestPasswordReset("user@example.com") }
    }

    // ---- resetPassword ----

    @Test
    fun `resetPassword success calls callback with true`() {
        every { syncService.resetPassword("valid-token-123", "newSecurePass99") } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = false

        vm.resetPassword("valid-token-123", "newSecurePass99") { s, _ ->
            success = s
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "resetPassword callback timed out")
        assertTrue(success)
    }

    @Test
    fun `resetPassword with invalid token returns error`() {
        every { syncService.resetPassword("bad-token", any()) } throws
            SyncException("Password reset failed: Invalid or expired token")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        vm.resetPassword("bad-token", "newSecurePass99") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success)
        val errorMsg = error
        assertNotNull(errorMsg)
        assertTrue(errorMsg!!.contains("Invalid") || errorMsg.contains("expired"), errorMsg)
    }

    @Test
    fun `resetPassword with weak password returns error`() {
        every { syncService.resetPassword(any(), "short") } throws
            SyncException("Password reset failed: Password must be at least 8 characters")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        vm.resetPassword("some-token", "short") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success)
        assertNotNull(error)
    }

    @Test
    fun `resetPassword does not log out an authenticated user on failure`() {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")
        every { syncService.resetPassword("bad-token", any()) } throws
            SyncException("Password reset failed: Invalid token")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val loginLatch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> loginLatch.countDown() }
        assertTrue(loginLatch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.isLoggedIn, "Should be logged in before reset attempt")

        val latch = CountDownLatch(1)
        vm.resetPassword("bad-token", "newpass") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        assertTrue(vm.isLoggedIn, "User should still be logged in after failed password reset")
        assertTrue(vm.userName.isNotEmpty(), "Username should be preserved")
    }

    @Test
    fun `resetPassword invokes sync service once`() {
        every { syncService.resetPassword(any(), any()) } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.resetPassword("token-abc", "newpass123") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        verify(exactly = 1) { syncService.resetPassword("token-abc", "newpass123") }
    }

    @Test
    fun `resetPassword notifies observers on success`() {
        every { syncService.resetPassword(any(), any()) } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        var notified = false
        vm.addObserver { notified = true }
        val latch = CountDownLatch(1)

        vm.resetPassword("token-abc", "newpass123") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(notified, "Observers should be notified after resetPassword")
    }
}
