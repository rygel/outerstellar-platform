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
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomePageEndToEndTest : H2WebTest() {

    @Test
    fun `home page is available on running server`() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository)
        val passwordEncoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, passwordEncoder)

        repository.seedStarterMessages()

        val appHandler = app(
            messageService,
            repository,
            outbox,
            cache,
            createRenderer(),
            pageFactory,
            testConfig,
            securityService,
            userRepository,
            passwordEncoder
        )

        val response = appHandler.http!!(Request(GET, "/"))

        if (response.status != Status.OK || !response.bodyString().contains("Outerstellar")) {
            println("TEST FAILURE DEBUG: Status=${response.status}, Body=${response.bodyString()}")
        }

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Outerstellar Starter"), "Body should contain brand name")
        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }
}
