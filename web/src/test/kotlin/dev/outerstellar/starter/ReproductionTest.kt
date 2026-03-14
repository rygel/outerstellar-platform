package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.H2WebTest
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.StubOutboxRepository
import dev.outerstellar.starter.web.StubTransactionManager
import dev.outerstellar.starter.web.WebPageFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test

class ReproductionTest : H2WebTest() {

    @Test
    fun `reproduce theme synchronization issue`() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, messageService)
        val passwordEncoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, passwordEncoder)
        val contactService =
            io.mockk.mockk<dev.outerstellar.starter.service.ContactService>(relaxed = true)

        val app =
            app(
                messageService,
                contactService,
                repository,
                outbox,
                cache,
                createRenderer(),
                pageFactory,
                testConfig,
                securityService,
                userRepository,
                passwordEncoder,
            )

        val response = app.http!!(Request(GET, "/?theme=dracula"))
        assertEquals(Status.OK, response.status)
        // Verify that the theme is applied in the body (CSS variables)
        // In Dracula theme, background color is #282A36
        val body = response.bodyString()
        assertTrue(
            body.contains("--color-background: #282A36"),
            "Should contain Dracula background color",
        )
    }
}
