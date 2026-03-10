package dev.outerstellar.starter

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.WebPageFactory
import io.mockk.every
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReproductionTest {

    @Test
    fun `reproduce ClassCastException on auth page`() {
        val repository = mockk<MessageRepository>()
        val outbox = mockk<OutboxRepository>()
        val cache = mockk<MessageCache>()
        val transactionManager = object : dev.outerstellar.starter.persistence.TransactionManager {
            override fun <T> inTransaction(block: () -> T): T = block()
        }
        
        every { repository.listMessages() } returns emptyList()
        every { repository.listDirtyMessages() } returns emptyList()
        
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, true)
        val i18n = I18nService.fromResourceBundle("web-messages")
        
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)
        
        // Use real app initialization
        val app = app(messageService, repository, outbox, cache, createRenderer(), pageFactory, config, i18n).http!!

        val request = Request(GET, "/auth?lang=en&theme=catppuccin-frappe&layout=nice")
        
        val response = app(request)
        assertEquals(Status.OK, response.status)
    }
}
