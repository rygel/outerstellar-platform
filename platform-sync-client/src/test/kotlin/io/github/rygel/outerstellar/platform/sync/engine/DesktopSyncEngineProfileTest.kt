@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import io.mockk.every
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineProfileTest : DesktopSyncEngineTestBase() {

    @Test
    fun `loadProfile success`() {
        every { syncService.fetchProfile() } returns UserProfileResponse("alice", "a@b.c", "http://avatar", true, false)

        engine.loadProfile()

        assertEquals("alice", engine.state.userName)
        assertEquals("a@b.c", engine.state.userEmail)
        assertEquals("http://avatar", engine.state.userAvatarUrl)
        assertTrue(engine.state.emailNotificationsEnabled)
        assertFalse(engine.state.pushNotificationsEnabled)
    }

    @Test
    fun `loadProfile session expired`() {
        every { syncService.fetchProfile() } throws SessionExpiredException()

        engine.loadProfile()

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `updateProfile success`() {
        stubLoggedIn()
        every { syncService.updateProfile("new@b.c", any(), any()) } returns Unit
        every { syncService.fetchProfile() } returns UserProfileResponse("user", "new@b.c", null, true, true)

        val result = engine.updateProfile("new@b.c")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "profile_updated") }
        verify { notifier.notifySuccess("Profile updated") }
    }

    @Test
    fun `updateProfile session expired`() {
        every { syncService.updateProfile("x@b.c", any(), any()) } throws SessionExpiredException()

        val result = engine.updateProfile("x@b.c")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `updateNotificationPreferences success`() {
        val result = engine.updateNotificationPreferences(emailEnabled = false, pushEnabled = true)

        assertTrue(result.isSuccess)
        assertFalse(engine.state.emailNotificationsEnabled)
        assertTrue(engine.state.pushNotificationsEnabled)
    }

    @Test
    fun `updateNotificationPreferences session expired`() {
        every { syncService.updateNotificationPreferences(any(), any()) } throws SessionExpiredException()

        val result = engine.updateNotificationPreferences(true, true)

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `deleteAccount success`() {
        stubLoggedIn()

        val result = engine.deleteAccount()

        assertTrue(result.isSuccess)
        assertFalse(engine.state.isLoggedIn)
        assertEquals("Account deleted", engine.state.status)
        verify { syncService.deleteAccount() }
        verify { syncService.logout() }
        verify { analytics.track("user", "account_deleted") }
        verify { notifier.notifySuccess("Account deleted") }
    }

    @Test
    fun `deleteAccount failure`() {
        stubLoggedIn()
        every { syncService.deleteAccount() } throws RuntimeException("Fail")

        val result = engine.deleteAccount()

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Account deletion failed: Fail") }
    }

    @Test
    fun `changePassword success`() {
        stubLoggedIn()

        val result = engine.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { syncService.changePassword("old", "new") }
        verify { analytics.track("user", "password_changed") }
        verify { notifier.notifySuccess("Password changed") }
    }

    @Test
    fun `changePassword session expired`() {
        every { syncService.changePassword(any(), any()) } throws SessionExpiredException()

        val result = engine.changePassword("old", "new")

        assertTrue(result.isFailure)
        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `changePassword failure`() {
        every { syncService.changePassword(any(), any()) } throws RuntimeException("Weak")

        val result = engine.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password change failed: Weak") }
    }
}
