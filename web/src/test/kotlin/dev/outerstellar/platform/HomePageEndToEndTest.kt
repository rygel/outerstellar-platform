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

class HomePageEndToEndTest : H2WebTest() {

    @Test
    fun `home page is available on running server`() {
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

        repository.seedMessages()

        val appHandler =
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

        val response = appHandler.http!!(Request(GET, "/"))

        if (response.status != Status.OK || !response.bodyString().contains("Outerstellar")) {
            println("TEST FAILURE DEBUG: Status=${response.status}, Body=${response.bodyString()}")
        }

        assertEquals(Status.OK, response.status)
        assertTrue(
            response.bodyString().contains("Outerstellar Platform"),
            "Body should contain brand name",
        )
        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }
}
