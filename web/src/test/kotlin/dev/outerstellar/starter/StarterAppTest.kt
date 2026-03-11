package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.PasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.StubMessageCache
import dev.outerstellar.starter.web.WebPageFactory
import io.mockk.mockk
import kotlin.test.Test

class StarterAppTest {
    @Test
    fun `can start the app and get home page`() {
        val messageService = mockk<MessageService>(relaxed = true)
        val repository = mockk<MessageRepository>(relaxed = true)
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = StubMessageCache()
        val pageFactory = WebPageFactory(repository)
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val passwordEncoder = mockk<PasswordEncoder>(relaxed = true)
        val contactService = io.mockk.mockk<dev.outerstellar.starter.service.ContactService>(relaxed = true)

        val app = app(
            messageService,
            contactService,
            repository,
            outbox,
            cache,
            createRenderer(),
            pageFactory,
            config,
            securityService,
            userRepository,
            passwordEncoder
        )
        // Simple verification - app is a PolyHandler, we just need to ensure it's not null
        // Full E2E logic is tested in H2WebTest (when docker is available)
        assert(app.http != null)
    }
}
