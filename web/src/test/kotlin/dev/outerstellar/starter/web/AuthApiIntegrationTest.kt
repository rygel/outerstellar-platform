package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthApiIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl, testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService = dev.outerstellar.starter.service.MessageService(repository, outbox, transactionManager, cache)
        val pageFactory = WebPageFactory(repository)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService = SecurityService(userRepository, encoder)
        val contactService = io.mockk.mockk<dev.outerstellar.starter.service.ContactService>(relaxed = true)

        app = app(
            messageService, contactService, repository, outbox, cache, createRenderer(),
            pageFactory, testConfig, securityService, userRepository, encoder
        ).http!!
    }

    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `register api creates user and allows login`() {
        val registerLens = org.http4k.core.Body.auto<dev.outerstellar.starter.model.RegisterRequest>().toLens()
        val loginLens = org.http4k.core.Body.auto<dev.outerstellar.starter.model.LoginRequest>().toLens()
        val tokenLens = org.http4k.core.Body.auto<dev.outerstellar.starter.model.AuthTokenResponse>().toLens()

        val registerResponse = app(
            Request(POST, "/api/v1/auth/register").with(
                registerLens of dev.outerstellar.starter.model.RegisterRequest("api-user", "secret123")
            )
        )
        assertEquals(Status.OK, registerResponse.status)
        assertEquals("api-user", tokenLens(registerResponse).username)

        val loginResponse = app(
            Request(POST, "/api/v1/auth/login").with(
                loginLens of dev.outerstellar.starter.model.LoginRequest("api-user", "secret123")
            )
        )
        assertEquals(Status.OK, loginResponse.status)
        assertTrue(tokenLens(loginResponse).token.isNotBlank())
    }
}
