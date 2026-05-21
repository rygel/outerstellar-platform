@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class NotificationModuleTest {

    private lateinit var notificationClient: NotificationClient
    private lateinit var module: NotificationModuleImpl

    private var stopAutoSyncCalled = false
    private var logoutCalled = false

    @BeforeEach
    fun setUp() {
        notificationClient = mockk(relaxed = true)
        stopAutoSyncCalled = false
        logoutCalled = false
        module =
            NotificationModuleImpl(
                notificationClient = notificationClient,
                onStopAutoSync = { stopAutoSyncCalled = true },
                onLogout = { logoutCalled = true },
            )
    }

    @Test
    fun `loadNotifications success`() {
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2025-01-01"))
        every { notificationClient.listNotifications() } returns notifs

        module.loadNotifications()

        assertEquals(1, module.notificationState.notifications.size)
        assertEquals("n1", module.notificationState.notifications[0].id)
    }

    @Test
    fun `loadNotifications session expired`() {
        every { notificationClient.listNotifications() } throws SessionExpiredException()

        module.loadNotifications()

        assertTrue(logoutCalled)
        assertTrue(stopAutoSyncCalled)
    }

    @Test
    fun `markNotificationRead reloads notifications`() {
        every { notificationClient.markNotificationRead("n1") } returns Unit
        every { notificationClient.listNotifications() } returns emptyList()

        module.markNotificationRead("n1")

        verify { notificationClient.markNotificationRead("n1") }
        verify { notificationClient.listNotifications() }
    }

    @Test
    fun `markAllNotificationsRead reloads notifications`() {
        every { notificationClient.markAllNotificationsRead() } returns Unit
        every { notificationClient.listNotifications() } returns emptyList()

        module.markAllNotificationsRead()

        verify { notificationClient.markAllNotificationsRead() }
        verify { notificationClient.listNotifications() }
    }

    @Test
    fun `markNotificationRead session expired`() {
        every { notificationClient.markNotificationRead("n1") } throws SessionExpiredException()

        module.markNotificationRead("n1")

        assertTrue(logoutCalled)
    }

    @Test
    fun `unreadNotificationCount counts unread`() {
        every { notificationClient.listNotifications() } returns
            listOf(
                NotificationSummary("1", "A", "B", "INFO", false, "2025-01-01"),
                NotificationSummary("2", "C", "D", "INFO", true, "2025-01-01"),
                NotificationSummary("3", "E", "F", "INFO", false, "2025-01-01"),
            )

        module.loadNotifications()

        assertEquals(2, module.notificationState.unreadCount)
    }
}
