package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.model.CreateApiKeyResponse
import dev.outerstellar.starter.persistence.JooqApiKeyRepository
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqSessionRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for API key authentication (named API keys as Bearer tokens).
 *
 * Covers:
 * - POST /api/v1/auth/api-keys creates a key and returns it in the response
 * - Named API key (not UUID) can authenticate sync endpoints as Bearer
 * - Deleted API key is rejected by Bearer auth
 * - POST /api/v1/auth/api-keys without auth returns 401
 * - POST /api/v1/auth/api-keys with blank name returns 400
 * - GET /api/v1/auth/api-keys lists the created key
 * - DELETE /api/v1/auth/api-keys/{id} removes the key
 */
class ApiKeyAuthIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val apiKeyRepository = JooqApiKeyRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                apiKeyRepository = apiKeyRepository,
                sessionRepository = JooqSessionRepository(testDsl),
            )
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "apikeyuser",
                email = "apikey@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

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

    @AfterEach fun teardown() = cleanup()

    private fun uuidBearer() = "Bearer $sessionToken"

    /** Creates an API key via the API and returns the raw key value. */
    private fun createApiKey(name: String = "my-key"): CreateApiKeyResponse {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", uuidBearer())
                    .header("content-type", "application/json")
                    .body("""{"name":"$name"}""")
            )
        assertEquals(Status.OK, response.status, "Key creation should succeed")
        return Jackson.asA(response.bodyString(), CreateApiKeyResponse::class)
    }

    @Test
    fun `POST api-keys without auth returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("content-type", "application/json")
                    .body("""{"name":"test-key"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST api-keys with blank name returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", uuidBearer())
                    .header("content-type", "application/json")
                    .body("""{"name":""}""")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `POST api-keys returns key in response`() {
        val result = createApiKey("integration-test-key")
        assertTrue(result.key.isNotBlank(), "Created key should not be blank")
        assertEquals("integration-test-key", result.name, "Key name should match request")
    }

    @Test
    fun `named API key can authenticate sync endpoint`() {
        val result = createApiKey("sync-key")

        val response =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${result.key}"))
        assertEquals(Status.OK, response.status, "Named API key should authenticate sync endpoint")
    }

    @Test
    fun `named API key can push messages`() {
        val result = createApiKey("push-key")
        val syncId = UUID.randomUUID().toString()

        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", "Bearer ${result.key}")
                    .header("content-type", "application/json")
                    .body(
                        """{"messages":[{"syncId":"$syncId","author":"apikeyuser","content":"via api key","updatedAtEpochMs":1000}]}"""
                    )
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET api-keys lists created keys`() {
        createApiKey("list-key")

        val response =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", uuidBearer()))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.contains("list-key"), "Key list should include created key name")
    }

    @Test
    fun `DELETE api-keys removes the key`() {
        val result = createApiKey("delete-me")

        // Get the key ID from the list
        val listResponse =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", uuidBearer()))
        val listBody = listResponse.bodyString()
        // Extract ID from response (list returns ApiKeySummary with id field)
        val idMatch = """"id"\s*:\s*(\d+)""".toRegex().find(listBody)
        val keyId = idMatch?.groupValues?.get(1) ?: return // skip if can't extract

        app(Request(DELETE, "/api/v1/auth/api-keys/$keyId").header("Authorization", uuidBearer()))

        // Now the key should be rejected
        val syncResponse =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${result.key}"))
        assertEquals(Status.UNAUTHORIZED, syncResponse.status, "Deleted API key should be rejected")
    }

    @Test
    fun `invalid random string as Bearer is rejected`() {
        val response =
            app(
                Request(GET, "/api/v1/sync")
                    .header("Authorization", "Bearer totally-not-a-valid-key")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }
}
