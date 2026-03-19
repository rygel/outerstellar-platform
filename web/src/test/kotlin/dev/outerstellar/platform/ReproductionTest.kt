package dev.outerstellar.platform

import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.JooqMessageRepository
import dev.outerstellar.platform.persistence.JooqUserRepository
import dev.outerstellar.platform.security.BCryptPasswordEncoder
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.web.H2WebTest
import dev.outerstellar.platform.web.StubMessageCache
import dev.outerstellar.platform.web.StubOutboxRepository
import dev.outerstellar.platform.web.StubTransactionManager
import dev.outerstellar.platform.web.WebPageFactory
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
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val passwordEncoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, passwordEncoder)
        val contactService =
            io.mockk.mockk<dev.outerstellar.platform.service.ContactService>(relaxed = true)

        val app =
            app(
                messageService,
                contactService,
                outbox,
                cache,
                createRenderer(),
                pageFactory,
                testConfig,
                securityService,
                userRepository,
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
