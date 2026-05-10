@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class DesktopSyncEngineDataTest : DesktopSyncEngineTestBase() {

    @Test
    fun `loadData fetches messages and contacts`() {
        every { messageService.listMessages(any()) } returns
            PagedResult(
                items = listOf(MessageSummary("s1", "a", "c", 1L, false)),
                metadata = PaginationMetadata(1, 100, 1),
            )
        every { contactService.listContacts(any()) } returns
            listOf(ContactSummary("c1", "Alice", emptyList(), emptyList(), emptyList(), "", "", "", 1L, false))

        engine.loadData()

        assertEquals(1, engine.state.messages.size)
        assertEquals(1, engine.state.contacts.size)
        assertEquals("s1", engine.state.messages[0].syncId)
        assertEquals("c1", engine.state.contacts[0].syncId)
    }

    @Test
    fun `loadData with search query passes query to services`() {
        every { messageService.listMessages(query = "test") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        engine.setSearchQuery("test")
        engine.loadData()

        verify { messageService.listMessages(query = "test") }
        verify { contactService.listContacts(query = "test") }
    }

    @Test
    fun `createLocalMessage success`() {
        every { messageService.createLocalMessage("author", "content") } returns mockk(relaxed = true)
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        val result = engine.createLocalMessage("author", "content")

        assertTrue(result.isSuccess)
        verify { messageService.createLocalMessage("author", "content") }
        verify { messageService.listMessages(any()) }
    }

    @Test
    fun `createLocalMessage validation failure returns Result failure`() {
        every { messageService.createLocalMessage("", "content") } throws
            ValidationException(listOf("Author is required."))

        val result = engine.createLocalMessage("", "content")

        assertTrue(result.isFailure)
    }

    @Test
    fun `createContact success`() {
        stubLoggedIn()
        every { contactService.createContact(any(), any(), any(), any(), any(), any(), any()) } returns
            mockk(relaxed = true)
        every { contactService.listContacts(any()) } returns emptyList()

        val result = engine.createContact("Alice", listOf("a@b.c"), emptyList(), emptyList(), "Co", "Addr", "Dept")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "contact_created", mapOf("name" to "Alice")) }
    }

    @Test
    fun `createContact without contact service returns failure`() {
        val engineNoContact =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
            )

        val result = engineNoContact.createContact("A", emptyList(), emptyList(), emptyList(), "", "", "")

        assertTrue(result.isFailure)
        assertEquals("Contact service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `setSearchQuery updates state and triggers loadData`() {
        every { messageService.listMessages(query = "hello") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        engine.setSearchQuery("hello")

        assertEquals("hello", engine.state.searchQuery)
        verify { messageService.listMessages(query = "hello") }
    }

    @Test
    fun `loadMessages passes search query`() {
        every { messageService.listMessages(query = "find") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        engine.setSearchQuery("find")

        verify { messageService.listMessages(query = "find") }
    }

    @Test
    fun `loadMessages with blank query passes null`() {
        every { messageService.listMessages(query = null) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        engine.loadMessages()

        verify { messageService.listMessages(query = null) }
    }

    @Test
    fun `loadContacts without contactService is no-op`() {
        val engineNoContact =
            DesktopSyncEngine(
                syncService = syncService,
                messageService = messageService,
                contactService = null,
                analytics = analytics,
            )

        engineNoContact.loadContacts()

        verify(exactly = 0) { contactService.listContacts(any()) }
    }
}
