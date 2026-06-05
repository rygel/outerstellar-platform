@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.engine.SessionLifecycle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AuthModuleTest {

    private lateinit var authClient: AuthClient
    private lateinit var analytics: AnalyticsService
    private lateinit var lifecycle: SessionLifecycle
    private lateinit var notifier: ModuleNotifier
    private lateinit var module: AuthModuleImpl

    @BeforeEach
    fun setUp() {
        authClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        lifecycle = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        module =
            AuthModuleImpl(authClient = authClient, analytics = analytics, lifecycle = lifecycle, notifier = notifier)
    }

    @Test
    fun `initial state has defaults`() {
        val fresh = AuthModuleImpl(authClient = authClient, analytics = analytics, lifecycle = mockk(relaxed = true))
        assertFalse(fresh.authState.isLoggedIn)
        assertEquals("", fresh.authState.userName)
        assertNull(fresh.authState.userRole)
    }

    @Test
    fun `login success updates state`() {
        every { authClient.login("alice", "pw") } returns AuthTokenResponse("tok", "alice", "ADMIN")

        val result = module.login("alice", "pw")

        assertTrue(result.isSuccess)
        assertTrue(module.authState.isLoggedIn)
        assertEquals("alice", module.authState.userName)
        assertEquals("ADMIN", module.authState.userRole)
        verify { analytics.identify("alice", mapOf("role" to "ADMIN")) }
        verify { analytics.track("alice", "user_login") }
        verify { notifier.notifySuccess("Logged in as alice") }
        verify { lifecycle.afterAuthSuccess() }
    }

    @Test
    fun `login failure returns Result failure`() {
        every { authClient.login("alice", "bad") } throws RuntimeException("Bad credentials")

        val result = module.login("alice", "bad")

        assertTrue(result.isFailure)
        assertFalse(module.authState.isLoggedIn)
        verify { notifier.notifyFailure("Login failed: Bad credentials") }
    }

    @Test
    fun `login session expired fires session expired`() {
        every { authClient.login("alice", "pw") } throws SessionExpiredException()

        val result = module.login("alice", "pw")

        assertTrue(result.isFailure)
        assertFalse(module.authState.isLoggedIn)
        verify { lifecycle.onSessionExpired() }
    }

    @Test
    fun `register success updates state`() {
        every { authClient.register("bob", "pw") } returns AuthTokenResponse("tok2", "bob", "USER")

        val result = module.register("bob", "pw")

        assertTrue(result.isSuccess)
        assertTrue(module.authState.isLoggedIn)
        assertEquals("bob", module.authState.userName)
        verify { analytics.track("bob", "user_register") }
        verify { notifier.notifySuccess("Registered as bob") }
        verify { lifecycle.afterAuthSuccess() }
    }

    @Test
    fun `register failure returns Result failure`() {
        every { authClient.register("bob", "pw") } throws RuntimeException("Taken")

        val result = module.register("bob", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Registration failed: Taken") }
    }

    @Test
    fun `logout clears state and tracks analytics`() {
        every { authClient.login("user", "pass") } returns AuthTokenResponse("tok", "user", "USER")
        module.login("user", "pass")

        module.logout()

        assertFalse(module.authState.isLoggedIn)
        assertEquals("", module.authState.userName)
        verify { lifecycle.beforeLogout() }
    }

    @Test
    fun `changePassword success`() {
        every { authClient.login("user", "pass") } returns AuthTokenResponse("tok", "user", "USER")
        module.login("user", "pass")

        val result = module.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { authClient.changePassword("old", "new") }
        verify { analytics.track("user", "password_changed") }
        verify { notifier.notifySuccess("Password changed") }
    }

    @Test
    fun `changePassword session expired`() {
        every { authClient.changePassword(any(), any()) } throws SessionExpiredException()

        val result = module.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { lifecycle.onSessionExpired() }
    }

    @Test
    fun `changePassword failure`() {
        every { authClient.changePassword(any(), any()) } throws RuntimeException("Weak")

        val result = module.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password change failed: Weak") }
    }

    @Test
    fun `requestPasswordReset success`() {
        val result = module.requestPasswordReset("a@b.c")

        assertTrue(result.isSuccess)
        verify { authClient.requestPasswordReset("a@b.c") }
        verify { notifier.notifySuccess("Password reset email sent") }
    }

    @Test
    fun `requestPasswordReset failure`() {
        every { authClient.requestPasswordReset(any()) } throws RuntimeException("Fail")

        val result = module.requestPasswordReset("a@b.c")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset request failed: Fail") }
    }

    @Test
    fun `resetPassword success`() {
        val result = module.resetPassword("token123", "newpass")

        assertTrue(result.isSuccess)
        verify { authClient.resetPassword("token123", "newpass") }
        verify { notifier.notifySuccess("Password has been reset") }
    }

    @Test
    fun `resetPassword failure`() {
        every { authClient.resetPassword(any(), any()) } throws RuntimeException("Expired")

        val result = module.resetPassword("tok", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset failed: Expired") }
    }
}
