package dev.outerstellar.starter.web

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form

class MessageActionE2ETest : H2WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `can create a message via form`() {
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository)
        
        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val encoder = BCryptPasswordEncoder(logRounds = 4)

        val app = app(
            messageService, 
            repository, 
            outbox, 
            cache, 
            createRenderer(), 
            pageFactory, 
            testConfig, 
            securityService,
            userRepository,
            encoder
        )
        
        val response = app.http!!(
            Request(POST, "/messages")
                .form("author", "Test Author")
                .form("content", "Test Content")
        )

        assertEquals(Status.FOUND, response.status)
        
        // Follow redirect
        val redirectResponse = app.http!!(Request(GET, "/"))
        assertEquals(Status.OK, redirectResponse.status)
        assertTrue(redirectResponse.bodyString().contains("Test Author"))
        assertTrue(redirectResponse.bodyString().contains("Test Content"))
    }
}
