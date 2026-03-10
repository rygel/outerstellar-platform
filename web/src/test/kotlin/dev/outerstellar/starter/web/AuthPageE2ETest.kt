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
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthPageE2ETest {

    private lateinit var appHandler: org.http4k.core.HttpHandler
    private val repository = mockk<MessageRepository>()

    @BeforeEach
    fun setup() {
        val outbox = mockk<OutboxRepository>()
        val cache = mockk<MessageCache>()
        val transactionManager = object : TransactionManager {
            override fun <T> inTransaction(block: () -> T): T = block()
        }

        every { repository.listMessages() } returns emptyList()
        every { repository.listDirtyMessages() } returns emptyList()

        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, true)
        val i18n = I18nService.fromResourceBundle("web-messages")
        val config = AppConfig(port = 0, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        // Initialize the app handler
        val renderer = createRenderer()
        appHandler = app(messageService, repository, outbox, cache, renderer, pageFactory, config, i18n).http!!
    }

    @Test
    fun `auth page renders correctly`() {
        // Execute a request to the Auth page
        val response = appHandler(Request(GET, "/auth"))

        // Verify basic response
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()

        // 1. Verify Global Shell content
        assertTrue(body.contains("Outerstellar Starter"), "Should contain the App Title")
        
        // 2. Verify Auth Page specific content
        assertTrue(body.contains("Authentication page examples"), "Should contain the Auth Heading")
        assertTrue(body.contains("HTMX"), "Should contain the HTMX helper text")
        
        // 3. Verify that the tabs are present
        assertTrue(body.contains("Sign in"), "Should contain the Sign in tab label")
        assertTrue(body.contains("Register"), "Should contain the Register tab label")
    }
}
