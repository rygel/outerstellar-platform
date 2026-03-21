package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
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
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val transactionManager = StubTransactionManager()
        val messageService =
            io.github.rygel.outerstellar.platform.service.MessageService(
                repository,
                outbox,
                transactionManager,
                cache,
            )
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val contactService =
            io.mockk.mockk<io.github.rygel.outerstellar.platform.service.ContactService>(
                relaxed = true
            )

        app =
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
    }

    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `register api creates user and allows login`() {
        val registerLens =
            org.http4k.core.Body.auto<io.github.rygel.outerstellar.platform.model.RegisterRequest>()
                .toLens()
        val loginLens =
            org.http4k.core.Body.auto<io.github.rygel.outerstellar.platform.model.LoginRequest>()
                .toLens()
        val tokenLens =
            org.http4k.core.Body.auto<
                io.github.rygel.outerstellar.platform.model.AuthTokenResponse
                >()
                .toLens()

        val registerResponse =
            app(
                Request(POST, "/api/v1/auth/register")
                    .with(
                        registerLens of
                            io.github.rygel.outerstellar.platform.model.RegisterRequest(
                                "api-user",
                                "secret123",
                            )
                    )
            )
        assertEquals(Status.OK, registerResponse.status)
        assertEquals("api-user", tokenLens(registerResponse).username)

        val loginResponse =
            app(
                Request(POST, "/api/v1/auth/login")
                    .with(
                        loginLens of
                            io.github.rygel.outerstellar.platform.model.LoginRequest(
                                "api-user",
                                "secret123",
                            )
                    )
            )
        assertEquals(Status.OK, loginResponse.status)
        assertTrue(tokenLens(loginResponse).token.isNotBlank())
    }
}
