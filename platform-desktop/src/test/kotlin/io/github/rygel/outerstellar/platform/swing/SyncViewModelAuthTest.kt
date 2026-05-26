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
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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

class SyncViewModelAuthTest {
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    private data class TestModules(
        val vm: SyncViewModel,
        val authModule: AuthModule,
        val authState: AtomicReference<io.github.rygel.outerstellar.platform.sync.engine.module.AuthState>,
    )

    private fun createVm(): TestModules {
        val authState = AtomicReference(io.github.rygel.outerstellar.platform.sync.engine.module.AuthState())
        val authModule = mockk<AuthModule>(relaxed = true)
        every { authModule.authState } answers { authState.get() }
        every { authModule.addListener(any()) } just runs
        every { authModule.removeListener(any()) } just runs
        every { authModule.login(any(), any()) } returns Result.success(Unit)
        every { authModule.register(any(), any()) } returns Result.success(Unit)
        every { authModule.logout() } answers
            {
                authState.set(io.github.rygel.outerstellar.platform.sync.engine.module.AuthState())
                Unit
            }

        val syncDataModule = mockk<SyncDataModule>(relaxed = true)
        val profileModule = mockk<ProfileModule>(relaxed = true)
        val adminModule = mockk<AdminModule>(relaxed = true)
        val notificationModule = mockk<NotificationModule>(relaxed = true)

        val vm = SyncViewModel(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18nService)
        return TestModules(vm, authModule, authState)
    }

    private fun loginVm(modules: TestModules, user: String = "alice", role: String = "USER"): TestModules {
        modules.authState.set(
            io.github.rygel.outerstellar.platform.sync.engine.module.AuthState(
                isLoggedIn = true,
                userName = user,
                userRole = role,
            )
        )
        return modules
    }

    @Test
    fun `login success calls authModule login`() {
        val test = createVm()
        val latch = CountDownLatch(1)
        var callbackSuccess = false
        var callbackError: String? = null

        test.vm.login("alice", "secret") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "login callback timed out")
        assertTrue(callbackSuccess)
        assertNull(callbackError)
        verify { test.authModule.login("alice", "secret") }
    }

    @Test
    fun `login failure returns error`() {
        val test = createVm()
        every { test.authModule.login("alice", "bad") } returns Result.failure(RuntimeException("boom"))

        val latch = CountDownLatch(1)
        var callbackSuccess = true
        var callbackError: String? = null

        test.vm.login("alice", "bad") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "login callback timed out")
        assertFalse(callbackSuccess)
        assertEquals("boom", callbackError)
    }

    @Test
    fun `logout calls authModule logout`() {
        val test = loginVm(createVm())

        test.vm.logout()

        verify(exactly = 1) { test.authModule.logout() }
    }

    @Test
    fun `register success calls authModule register`() {
        val test = createVm()
        val latch = CountDownLatch(1)
        var callbackSuccess = false
        var callbackError: String? = null

        test.vm.register("newuser", "secret123") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "register callback timed out")
        assertTrue(callbackSuccess)
        assertNull(callbackError)
        verify { test.authModule.register("newuser", "secret123") }
    }

    @Test
    fun `register failure preserves logged out state and returns error`() {
        val test = createVm()
        every { test.authModule.register("newuser", "short") } returns
            Result.failure(RuntimeException("Registration failed: 409 CONFLICT"))

        val latch = CountDownLatch(1)
        var callbackSuccess = true
        var callbackError: String? = null

        test.vm.register("newuser", "short") { success, error ->
            callbackSuccess = success
            callbackError = error
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "register callback timed out")
        assertFalse(callbackSuccess)
        assertEquals("Registration failed: 409 CONFLICT", callbackError)
    }

    @Test
    fun `changePassword success calls callback with true`() {
        val test = loginVm(createVm())
        every { test.authModule.changePassword("old", "newpass") } returns Result.success(Unit)

        val latch = CountDownLatch(1)
        var success = false

        test.vm.changePassword("old", "newpass") { s, _ ->
            success = s
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "changePassword callback timed out")
        assertTrue(success)
    }

    @Test
    fun `changePassword failure returns error message`() {
        val test = createVm()
        every { test.authModule.changePassword("wrong", "newpass") } returns
            Result.failure(SyncException("Password change failed: Current password is incorrect"))

        val latch = CountDownLatch(1)
        var success = true
        var error: String? = null

        test.vm.changePassword("wrong", "newpass") { s, e ->
            success = s
            error = e
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertFalse(success)
        assertTrue(error!!.contains("Current password is incorrect"))
    }
}
