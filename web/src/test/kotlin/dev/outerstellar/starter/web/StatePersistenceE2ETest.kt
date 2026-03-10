package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies

class StatePersistenceE2ETest : PostgresWebTest() {
    @Test
    fun `language preference is persisted in cookie`() {
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, true)
        val i18n = I18nService.fromResourceBundle("messages")
        
        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)

        val app = app(
            messageService, 
            repository, 
            outbox, 
            cache, 
            createRenderer(), 
            pageFactory, 
            testConfig, 
            i18n,
            securityService,
            userRepository
        )
        
        val response = app.http!!(Request(GET, "/?lang=fr"))
        assertEquals(Status.OK, response.status)
        
        val langCookie = response.cookies().find { it.name == "app_lang" }
        assertEquals("fr", langCookie?.value)
    }
}
