package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.UserAdminService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.web.StubMessageCache
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.bodyContains
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus

class PlatformAppTest {
    @Test
    fun `can start the app and get home page`() {
        val messageService = mockk<MessageService>(relaxed = true)
        val repository = mockk<MessageRepository>(relaxed = true)
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = StubMessageCache()
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:postgresql://localhost/test", devDashboardEnabled = true)

        val securityService = mockk<SecurityService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val userAdminService = mockk<UserAdminService>(relaxed = true)
        val sessionService = mockk<SessionService>(relaxed = true)
        every { userRepository.countAll() } returns 0L
        val contactService =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)

        val polyHandler =
            app(
                messageService,
                contactService,
                outbox,
                cache,
                createRenderer(),
                pageFactory,
                config,
                securityService,
                userAdminService,
                sessionService,
                userRepository,
            )
        val handler = polyHandler.http
        assertNotNull(handler)

        val healthResponse = handler(Request(Method.GET, "/health"))
        assertThat(healthResponse, hasStatus(Status.OK))
        assertThat(healthResponse, bodyContains("UP"))
    }
}
