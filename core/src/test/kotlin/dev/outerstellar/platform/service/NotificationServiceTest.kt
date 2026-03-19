package dev.outerstellar.platform.service

import dev.outerstellar.platform.persistence.Notification
import dev.outerstellar.platform.persistence.NotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationServiceTest {

    private val repository = mockk<NotificationRepository>(relaxed = true)
    private val service = NotificationService(repository)

    private val userId = UUID.randomUUID()
    private val notificationId = UUID.randomUUID()

    @Test
    fun `create saves notification with correct fields`() {
        val slot = slot<Notification>()

        service.create(userId, "Hello", "World", "success")

        verify { repository.save(capture(slot)) }
        assertEquals(userId, slot.captured.userId)
        assertEquals("Hello", slot.captured.title)
        assertEquals("World", slot.captured.body)
        assertEquals("success", slot.captured.type)
    }

    @Test
    fun `create uses info as default type`() {
        val slot = slot<Notification>()

        service.create(userId, "Title", "Body")

        verify { repository.save(capture(slot)) }
        assertEquals("info", slot.captured.type)
    }

    @Test
    fun `listForUser delegates to repository`() {
        val notification = Notification(userId = userId, title = "T", body = "B")
        every { repository.findByUserId(userId, 50) } returns listOf(notification)

        val result = service.listForUser(userId)

        assertEquals(listOf(notification), result)
    }

    @Test
    fun `countUnread delegates to repository`() {
        every { repository.countUnread(userId) } returns 3

        val result = service.countUnread(userId)

        assertEquals(3, result)
    }

    @Test
    fun `markRead delegates to repository`() {
        service.markRead(notificationId, userId)

        verify { repository.markRead(notificationId, userId) }
    }

    @Test
    fun `markAllRead delegates to repository`() {
        service.markAllRead(userId)

        verify { repository.markAllRead(userId) }
    }

    @Test
    fun `delete delegates to repository`() {
        service.delete(notificationId, userId)

        verify { repository.delete(notificationId, userId) }
    }
}
