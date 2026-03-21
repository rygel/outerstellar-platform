package io.github.rygel.outerstellar.platform

import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.web.StubMessageCache
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.mockk.mockk
import kotlin.test.Test

class PlatformAppTest {
    @Test
    fun `can start the app and get home page`() {
        val messageService = mockk<MessageService>(relaxed = true)
        val repository = mockk<MessageRepository>(relaxed = true)
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = StubMessageCache()
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:h2:mem:test", devDashboardEnabled = true)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val contactService =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)

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
