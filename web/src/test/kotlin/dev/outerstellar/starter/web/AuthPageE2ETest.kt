package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthPageE2ETest : H2WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `auth page renders correctly`() {
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, messageService)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
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
                encoder,
            )
        val response = app.http!!(Request(GET, "/auth"))

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Auth Examples"))
    }
}
