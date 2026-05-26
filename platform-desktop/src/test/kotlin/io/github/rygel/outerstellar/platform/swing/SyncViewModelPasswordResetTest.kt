package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelPasswordResetTest {

    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private fun createVm(): Pair<SyncViewModel, AuthModule> {
        val authState = AtomicReference(io.github.rygel.outerstellar.platform.sync.engine.module.AuthState())
        val authModule = mockk<AuthModule>(relaxed = true)
        every { authModule.authState } answers { authState.get() }
        every { authModule.requestPasswordReset(any()) } returns Result.success(Unit)
        every { authModule.resetPassword(any(), any()) } returns Result.success(Unit)

        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)

        return SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService) to
            authModule
    }

    @Test
    fun `requestPasswordReset success calls callback with true and no error`() {
        val (vm, _) = createVm()
        val latch = CountDownLatch(1)
        var success = false
        var error: String? = "initial"

        vm.requestPasswordReset("user@example.com") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "requestPasswordReset callback timed out")
        assertTrue(success)
        assertNull(error)
    }

    @Test
    fun `requestPasswordReset failure propagates error message`() {
        val (vm, authModule) = createVm()
        every { authModule.requestPasswordReset("user@example.com") } returns
            Result.failure(SyncException("Password reset request failed: 500 INTERNAL_SERVER_ERROR"))

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
        assertTrue(
            errorMsg != null && (errorMsg.contains("500") || errorMsg.contains("reset request failed")),
            errorMsg,
        )
    }

    @Test
    fun `resetPassword success calls callback with true`() {
        val (vm, _) = createVm()
        val latch = CountDownLatch(1)
        var success = false

        vm.resetPassword("valid-token-123", "newSecurePass99") { s, _ ->
            success = s
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertTrue(success)
    }

    @Test
    fun `resetPassword with invalid token returns error`() {
        val (vm, authModule) = createVm()
        every { authModule.resetPassword("bad-token", any()) } returns
            Result.failure(SyncException("Password reset failed: Invalid or expired token"))

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
        assertTrue(errorMsg != null && (errorMsg.contains("Invalid") || errorMsg.contains("expired")), errorMsg)
    }

    @Test
    fun `resetPassword notifies observers on success`() {
        val (vm, _) = createVm()
        var notified = false
        vm.addObserver { notified = true }
        val latch = CountDownLatch(1)

        vm.resetPassword("token-abc", "newpass123") { _, _ -> latch.countDown() }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        javax.swing.SwingUtilities.invokeAndWait {}
        assertTrue(notified, "Observers should be notified after resetPassword")
    }
}
