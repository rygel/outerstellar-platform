package dev.outerstellar.starter

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.PasswordEncoder
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.H2WebTest
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.StubOutboxRepository
import dev.outerstellar.starter.web.StubTransactionManager
import dev.outerstellar.starter.web.WebPageFactory
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status

class ReproductionTest {
    @AfterEach
    fun teardown() {
        H2WebTest.cleanup()
    }

    @Test
    fun `reproduce theme synchronization issue`() {
        H2WebTest.setup()
        val repository = JooqMessageRepository(H2WebTest.testDsl, H2WebTest.testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository)
        
        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)

        val app = app(
            messageService, 
            repository, 
            outbox, 
            cache, 
            createRenderer(), 
            pageFactory, 
            H2WebTest.testConfig, 
            securityService,
            userRepository,
            passwordEncoder
        )
        
        val response = app.http!!(Request(GET, "/?theme=dracula"))
        assertEquals(Status.OK, response.status)
        // Verify that the theme is applied in the body (CSS variables)
        assertEquals(true, response.bodyString().contains("--bg-primary: #282a36"))
    }
}
