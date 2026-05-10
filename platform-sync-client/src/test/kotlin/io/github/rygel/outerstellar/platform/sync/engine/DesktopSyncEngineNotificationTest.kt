@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.mockk.every
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineNotificationTest : DesktopSyncEngineTestBase() {

    @Test
    fun `loadNotifications success`() {
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2025-01-01"))
        every { syncService.listNotifications() } returns notifs

        engine.loadNotifications()

        assertEquals(1, engine.state.notifications.size)
        assertEquals("n1", engine.state.notifications[0].id)
    }

    @Test
    fun `loadNotifications session expired`() {
        every { syncService.listNotifications() } throws SessionExpiredException()

        engine.loadNotifications()

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `markNotificationRead reloads notifications`() {
        every { syncService.markNotificationRead("n1") } returns Unit
        every { syncService.listNotifications() } returns emptyList()

        engine.markNotificationRead("n1")

        verify { syncService.markNotificationRead("n1") }
        verify { syncService.listNotifications() }
    }

    @Test
    fun `markAllNotificationsRead reloads notifications`() {
        every { syncService.markAllNotificationsRead() } returns Unit
        every { syncService.listNotifications() } returns emptyList()

        engine.markAllNotificationsRead()

        verify { syncService.markAllNotificationsRead() }
        verify { syncService.listNotifications() }
    }

    @Test
    fun `markNotificationRead session expired`() {
        every { syncService.markNotificationRead("n1") } throws SessionExpiredException()

        engine.markNotificationRead("n1")

        assertEquals("Session expired", engine.state.status)
    }

    @Test
    fun `unreadNotificationCount counts unread`() {
        every { syncService.listNotifications() } returns
            listOf(
                NotificationSummary("1", "A", "B", "INFO", false, "2025-01-01"),
                NotificationSummary("2", "C", "D", "INFO", true, "2025-01-01"),
                NotificationSummary("3", "E", "F", "INFO", false, "2025-01-01"),
            )

        engine.loadNotifications()

        assertEquals(2, engine.state.unreadNotificationCount)
    }
}
