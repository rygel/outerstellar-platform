package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson.asA
import org.junit.jupiter.api.AfterEach
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncIntegrationTest : H2WebTest() {

    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `can pull changes from api`() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = JooqSessionRepository(testDsl),
            )

        // Pre-register an admin user for Bearer Auth
        val adminId = UUID.randomUUID()
        userRepository.save(
            User(
                id = adminId,
                username = "admin",
                email = "admin@test.com",
                passwordHash = encoder.encode("password"),
                role = UserRole.ADMIN,
            )
        )
        val adminToken = securityService.createSession(adminId)
        val contactService =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.service.ContactService>(
                relaxed = true
            )

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
                .http!!

        // Add some data
        repository.createServerMessage("Alice", "Hello")
        repository.createServerMessage("Bob", "Hi")

        // Pull changes with Bearer Auth
        val response =
            app(Request(GET, "/api/v1/sync?since=0").header("Authorization", "Bearer $adminToken"))

        assertEquals(Status.OK, response.status)
        val pullResponse = asA(response.bodyString(), SyncPullResponse::class)
        assertEquals(2, pullResponse.messages.size)
    }
}
