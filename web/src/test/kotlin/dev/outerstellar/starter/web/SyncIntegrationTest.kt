package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncPullResponse
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
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, encoder)

        // Pre-register an admin user for Bearer Auth
        val adminId = UUID.randomUUID()
        userRepository.save(User(
            id = adminId,
            username = "admin",
            email = "admin@test.com",
            passwordHash = encoder.encode("password"),
            role = UserRole.ADMIN
        ))

        val app = app(
            messageService, repository, outbox, cache, createRenderer(), 
            pageFactory, testConfig, securityService, userRepository, encoder
        ).http!!

        // Add some data
        repository.createServerMessage("Alice", "Hello")
        repository.createServerMessage("Bob", "Hi")

        // Pull changes with Bearer Auth
        val response = app(Request(GET, "/api/v1/sync?since=0")
            .header("Authorization", "Bearer $adminId")
        )

        assertEquals(Status.OK, response.status)
        val pullResponse = asA(response.bodyString(), SyncPullResponse::class)
        assertEquals(2, pullResponse.messages.size)
    }
}
