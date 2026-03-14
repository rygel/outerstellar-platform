package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.SessionExpiredException
import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.web.AuthTokenResponse
import dev.outerstellar.starter.web.UserSummary
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncViewModelAuthTest {
    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    @Test
    fun `login success updates auth state and author`() {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
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

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
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
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")
        every { syncService.logout() } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
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
        every { syncService.register("newuser", "secret123") } returns
            AuthTokenResponse("t", "newuser", "USER")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
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
        every { syncService.register("newuser", "short") } throws
            RuntimeException("Registration failed: 409 CONFLICT")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
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

    @Test
    fun `changePassword success calls callback with true`() {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")
        every { syncService.changePassword("old", "newpass") } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val loginLatch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> loginLatch.countDown() }
        assertTrue(loginLatch.await(3, TimeUnit.SECONDS))

        val latch = CountDownLatch(1)
        var success = false

        vm.changePassword("old", "newpass") { s, _ ->
            success = s
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "changePassword callback timed out")
        assertTrue(success)
    }

    @Test
    fun `changePassword failure returns error message`() {
        every { syncService.changePassword("wrong", "newpass") } throws
            SyncException("Password change failed: Current password is incorrect")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        vm.changePassword("wrong", "newpass") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success)
        assertTrue(error!!.contains("Current password is incorrect"))
    }

    @Test
    fun `session expiry on changePassword logs out user`() {
        every { syncService.login("alice", "secret") } returns
            AuthTokenResponse("t", "alice", "USER")
        every { syncService.changePassword(any(), any()) } throws SessionExpiredException()

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val loginLatch = CountDownLatch(1)
        vm.login("alice", "secret") { _, _ -> loginLatch.countDown() }
        assertTrue(loginLatch.await(3, TimeUnit.SECONDS))
        assertTrue(vm.isLoggedIn)

        val latch = CountDownLatch(1)
        vm.changePassword("old", "new") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        assertFalse(vm.isLoggedIn)
        assertEquals("", vm.userName)
        assertEquals(i18nService.translate("swing.session.expired"), vm.status)
    }

    @Test
    fun `login sets userRole from server response`() {
        every { syncService.login("admin", "secret") } returns
            AuthTokenResponse("t", "admin", "ADMIN")

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.login("admin", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        assertEquals("ADMIN", vm.userRole)
    }

    @Test
    fun `logout clears userRole and adminUsers`() {
        every { syncService.login("admin", "secret") } returns
            AuthTokenResponse("t", "admin", "ADMIN")
        every { syncService.logout() } just runs

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.login("admin", "secret") { _, _ -> latch.countDown() }
        assertTrue(latch.await(3, TimeUnit.SECONDS))

        vm.logout()

        assertNull(vm.userRole)
        assertTrue(vm.adminUsers.isEmpty())
    }

    @Test
    fun `loadUsers populates adminUsers list`() {
        val users =
            listOf(
                UserSummary("1", "admin", "admin@test.com", "ADMIN", true),
                UserSummary("2", "alice", "alice@test.com", "USER", true),
            )
        every { syncService.listUsers() } returns users

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.loadUsers()

        assertTrue(latch.await(3, TimeUnit.SECONDS), "loadUsers callback timed out")
        assertEquals(2, vm.adminUsers.size)
        assertEquals("admin", vm.adminUsers[0].username)
        assertEquals("alice", vm.adminUsers[1].username)
    }

    @Test
    fun `toggleUserEnabled calls service and refreshes list`() {
        val users = listOf(UserSummary("1", "alice", "alice@test.com", "USER", false))
        every { syncService.setUserEnabled("1", true) } just runs
        every { syncService.listUsers() } returns users

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserEnabled("1", false)

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        verify { syncService.setUserEnabled("1", true) }
        assertEquals(1, vm.adminUsers.size)
    }

    @Test
    fun `toggleUserRole flips between USER and ADMIN`() {
        every { syncService.setUserRole("1", "ADMIN") } just runs
        every { syncService.listUsers() } returns emptyList()

        val vm = SyncViewModel(messageService, null, syncService, i18nService)
        val latch = CountDownLatch(1)
        vm.addObserver { latch.countDown() }

        vm.toggleUserRole("1", "USER")

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        verify { syncService.setUserRole("1", "ADMIN") }
    }
}
