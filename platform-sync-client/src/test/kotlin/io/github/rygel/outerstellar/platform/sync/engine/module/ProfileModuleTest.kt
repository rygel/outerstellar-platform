@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class ProfileModuleTest {

    private lateinit var profileClient: ProfileClient
    private lateinit var analytics: AnalyticsService
    private lateinit var notifier: ModuleNotifier
    private lateinit var module: ProfileModuleImpl

    private var authState = AuthState()
    private var loadDataCalled = false
    private var stopAutoSyncCalled = false
    private var logoutCalled = false

    @BeforeEach
    fun setUp() {
        profileClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")
        loadDataCalled = false
        stopAutoSyncCalled = false
        logoutCalled = false
        module =
            ProfileModuleImpl(
                profileClient = profileClient,
                analytics = analytics,
                authStateProvider = { authState },
                onLoadData = { loadDataCalled = true },
                onStopAutoSync = { stopAutoSyncCalled = true },
                onLogout = { logoutCalled = true },
                notifier = notifier,
            )
    }

    @Test
    fun `loadProfile success`() {
        every { profileClient.fetchProfile() } returns
            UserProfileResponse("alice", "a@b.c", "http://avatar", true, false)

        module.loadProfile()

        assertEquals("a@b.c", module.profileState.userEmail)
        assertEquals("http://avatar", module.profileState.userAvatarUrl)
        assertTrue(module.profileState.emailNotificationsEnabled)
        assertFalse(module.profileState.pushNotificationsEnabled)
    }

    @Test
    fun `loadProfile session expired`() {
        every { profileClient.fetchProfile() } throws SessionExpiredException()

        module.loadProfile()

        assertTrue(logoutCalled)
        assertTrue(stopAutoSyncCalled)
    }

    @Test
    fun `updateProfile success`() {
        every { profileClient.updateProfile("new@b.c", any(), any()) } returns Unit
        every { profileClient.fetchProfile() } returns UserProfileResponse("user", "new@b.c", null, true, true)

        val result = module.updateProfile("new@b.c")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "profile_updated") }
        verify { notifier.notifySuccess("Profile updated") }
        assertTrue(loadDataCalled)
    }

    @Test
    fun `updateProfile session expired`() {
        every { profileClient.updateProfile("x@b.c", any(), any()) } throws SessionExpiredException()

        val result = module.updateProfile("x@b.c")

        assertTrue(result.isFailure)
        assertTrue(logoutCalled)
    }

    @Test
    fun `updateNotificationPreferences success`() {
        val result = module.updateNotificationPreferences(emailEnabled = false, pushEnabled = true)

        assertTrue(result.isSuccess)
        assertFalse(module.profileState.emailNotificationsEnabled)
        assertTrue(module.profileState.pushNotificationsEnabled)
    }

    @Test
    fun `updateNotificationPreferences session expired`() {
        every { profileClient.updateNotificationPreferences(any(), any()) } throws SessionExpiredException()

        val result = module.updateNotificationPreferences(true, true)

        assertTrue(result.isFailure)
        assertTrue(logoutCalled)
    }

    @Test
    fun `deleteAccount success`() {
        val result = module.deleteAccount("secret")

        assertTrue(result.isSuccess)
        verify { profileClient.deleteAccount("secret") }
        verify { analytics.track("user", "account_deleted") }
        verify { notifier.notifySuccess("Account deleted") }
        assertTrue(stopAutoSyncCalled)
        assertTrue(logoutCalled)
    }

    @Test
    fun `deleteAccount failure`() {
        every { profileClient.deleteAccount("secret") } throws RuntimeException("Fail")

        val result = module.deleteAccount("secret")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Account deletion failed: Fail") }
    }
}
