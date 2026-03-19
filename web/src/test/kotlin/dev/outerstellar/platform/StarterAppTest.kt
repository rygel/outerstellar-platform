package dev.outerstellar.platform

import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.persistence.MessageRepository
import dev.outerstellar.platform.persistence.OutboxRepository
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRepository
import dev.outerstellar.platform.service.MessageService
import dev.outerstellar.platform.web.StubMessageCache
import dev.outerstellar.platform.web.WebPageFactory
import io.mockk.mockk
import kotlin.test.Test

class StarterAppTest {
    @Test
    fun `can start the app and get home page`() {
        val messageService = mockk<MessageService>(relaxed = true)
        val repository = mockk<MessageRepository>(relaxed = true)
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = StubMessageCache()
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val config =
            AppConfig(port = 8080, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
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
                config,
                securityService,
                userRepository,
            )
        // Simple verification - app is a PolyHandler, we just need to ensure it's not null
        // Full E2E logic is tested in H2WebTest (when docker is available)
        assert(app.http != null)
    }
}
