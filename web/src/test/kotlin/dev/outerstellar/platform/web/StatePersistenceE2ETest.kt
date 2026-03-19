package dev.outerstellar.platform.web

import dev.outerstellar.platform.app
import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.JooqMessageRepository
import dev.outerstellar.platform.security.BCryptPasswordEncoder
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.service.MessageService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.AfterEach

class StatePersistenceE2ETest : H2WebTest() {
    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `language preference is persisted in cookie`() {
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

        val response = app.http!!(Request(GET, "/?lang=fr"))
        assertEquals(Status.OK, response.status)

        val langCookie = response.cookies().find { it.name == "app_lang" }
        assertEquals("fr", langCookie?.value)
    }
}
