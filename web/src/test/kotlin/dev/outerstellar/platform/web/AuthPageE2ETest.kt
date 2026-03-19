package dev.outerstellar.platform.web

import dev.outerstellar.platform.app
import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.JooqMessageRepository
import dev.outerstellar.platform.security.BCryptPasswordEncoder
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.service.MessageService
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
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, messageService)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
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
        val response = app.http!!(Request(GET, "/auth"))

        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().contains("Auth Examples"))
    }
}
