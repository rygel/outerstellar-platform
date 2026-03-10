package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.MessageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.http4k.core.ContentType
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageActionE2ETest {

    private lateinit var appHandler: org.http4k.core.HttpHandler
    private val repository = mockk<MessageRepository>(relaxed = true)

    @BeforeEach
    fun setup() {
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = mockk<MessageCache>(relaxed = true)
        val transactionManager = object : TransactionManager {
            override fun <T> inTransaction(block: () -> T): T = block()
        }

        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, true)
        val i18n = I18nService.fromResourceBundle("web-messages")
        val config = AppConfig(port = 0, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        appHandler = app(messageService, repository, outbox, cache, createRenderer(), pageFactory, config, i18n).http!!
    }

    @Test
    fun `creating a message using form lenses works correctly`() {
        val request = Request(POST, "/messages")
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("author", "Tester")
            .form("content", "Hello World")

        val response = appHandler(request)

        assertEquals(Status.FOUND, response.status)
        verify { repository.createServerMessage("Tester", "Hello World") }
    }

    @Test
    fun `creating a message with missing content is rejected by the strict lens`() {
        val request = Request(POST, "/messages")
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("author", "Tester")

        val response = appHandler(request)
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `fetching the delete confirmation modal returns the modal fragment`() {
        val request = Request(GET, "/components/modals/confirm-delete/msg-123")
        val response = appHandler(request)

        assertEquals(Status.OK, response.status)
        val body = response.bodyString()

        assertTrue(body.contains("id=\"delete-modal-msg-123\""), "Should contain the modal ID")
        assertTrue(body.contains("Delete Message"), "Should contain the modal title")
        assertTrue(body.contains("hx-delete=\"/messages/msg-123\""), "Should contain the delete action URL")
    }

    @Test
    fun `deleting a message soft-deletes it and returns the updated list`() {
        val request = Request(DELETE, "/messages/msg-123")
        val response = appHandler(request)

        assertEquals(Status.OK, response.status)
        verify { repository.softDelete("msg-123") }
        
        assertTrue(response.bodyString().contains("Current synchronized messages"))
    }
}
