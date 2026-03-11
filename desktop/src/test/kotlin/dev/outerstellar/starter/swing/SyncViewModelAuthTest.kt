package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.AuthTokenResponse
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SyncViewModelAuthTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    @Test
    fun `login success updates auth state and author`() {
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("t", "alice", "USER")

        val vm = SyncViewModel(messageService, syncService, i18nService)
        val latch = CountDownLatch(1)
        var callbackSuccess = false
        var callbackError: String? = null

        vm.login("alice", "secret") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "login callback timed out")
        assertTrue(callbackSuccess)
        assertNull(callbackError)
        assertTrue(vm.isLoggedIn)
        assertEquals("alice", vm.userName)
        assertEquals("alice", vm.author)
        assertEquals(i18nService.translate("swing.status.loggedIn", "alice"), vm.status)
    }

    @Test
    fun `login failure preserves logged out state and returns error`() {
        every { syncService.login("alice", "bad") } throws RuntimeException("boom")

        val vm = SyncViewModel(messageService, syncService, i18nService)
        val latch = CountDownLatch(1)
        var callbackSuccess = true
        var callbackError: String? = null

        vm.login("alice", "bad") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "login callback timed out")
        assertFalse(callbackSuccess)
        assertEquals("boom", callbackError)
        assertFalse(vm.isLoggedIn)
        assertEquals("", vm.userName)
        assertEquals(i18nService.translate("swing.author.default"), vm.author)
    }

    @Test
    fun `logout clears auth state and calls sync service logout`() {
        every { syncService.login("alice", "secret") } returns AuthTokenResponse("t", "alice", "USER")
        every { syncService.logout() } just runs

        val vm = SyncViewModel(messageService, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS), "login callback timed out")

        vm.logout()

        verify(exactly = 1) { syncService.logout() }
        assertFalse(vm.isLoggedIn)
        assertEquals("", vm.userName)
        assertEquals(i18nService.translate("swing.author.default"), vm.author)
        assertEquals(i18nService.translate("swing.status.loggedOut"), vm.status)
    }

    @Test
    fun `register success updates auth state and author`() {
        every { syncService.register("newuser", "secret123") } returns AuthTokenResponse("t", "newuser", "USER")

        val vm = SyncViewModel(messageService, syncService, i18nService)
        val latch = CountDownLatch(1)
        var callbackSuccess = false
        var callbackError: String? = null

        vm.register("newuser", "secret123") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "register callback timed out")
        assertTrue(callbackSuccess)
        assertNull(callbackError)
        assertTrue(vm.isLoggedIn)
        assertEquals("newuser", vm.userName)
        assertEquals("newuser", vm.author)
        assertEquals(i18nService.translate("swing.status.registered", "newuser"), vm.status)
    }

    @Test
    fun `register failure preserves logged out state and returns error`() {
        every { syncService.register("newuser", "short") } throws RuntimeException("Registration failed: 409 CONFLICT")

        val vm = SyncViewModel(messageService, syncService, i18nService)
        val latch = CountDownLatch(1)
        var callbackSuccess = true
        var callbackError: String? = null

        vm.register("newuser", "short") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "register callback timed out")
        assertFalse(callbackSuccess)
        assertEquals("Registration failed: 409 CONFLICT", callbackError)
        assertFalse(vm.isLoggedIn)
        assertEquals("", vm.userName)
    }
}
