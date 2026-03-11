package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PagedResult
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.MessageRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MessageServiceTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val service = MessageService(repository)

    @Test
    fun `listMessages returns items from repository`() {
        val summary = MessageSummary("id-1", "author", "content", 1000L, false)
        val items = listOf(summary)
        val paged = PagedResult(items, PaginationMetadata(1, 10, 1L))

        every { repository.listMessages("test", null, 10, 0) } returns items
        every { repository.countMessages("test", null) } returns 1L

        val result = service.listMessages("test", limit = 10, offset = 0)
        assertEquals(paged, result)
    }

    @Test
    fun `listDirtyMessages returns dirty items from repository`() {
        val msg = StoredMessage("id-1", "author", "content", 1000L, true, false, 1L)
        val items = listOf(msg)
        every { repository.listDirtyMessages() } returns items

        val result = service.listDirtyMessages()
        assertEquals(items, result)
    }
}
