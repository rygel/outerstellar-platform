package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.model.ApiKeySummary
import dev.outerstellar.starter.model.CreateApiKeyResponse
import dev.outerstellar.starter.persistence.JooqApiKeyRepository
import dev.outerstellar.starter.persistence.JooqMessageRepository
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
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson.auto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the API key lifecycle (Feature 6).
 *
 * Covers:
 * - POST /api/v1/auth/api-keys creates a key with osk_ prefix
 * - GET /api/v1/auth/api-keys lists keys by bearer auth
 * - DELETE /api/v1/auth/api-keys/{id} removes the key
 * - Created API key can be used as a bearer token for sync API
 * - Using a deleted API key returns 401
 * - POST with blank name returns 400
 * - Key response contains keyPrefix that starts with "osk_"
 * - GET returns empty list when no keys exist
 * - Multiple keys can be created for same user
 * - DELETE of another user's key is a no-op (key not found scoped by userId)
 */
class ApiKeyLifecycleIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var apiKeyRepository: JooqApiKeyRepository
    private lateinit var securityService: SecurityService
    private lateinit var testUser: User
    private lateinit var otherUser: User

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        apiKeyRepository = JooqApiKeyRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        securityService =
            SecurityService(
                userRepository = userRepository,
                passwordEncoder = encoder,
                apiKeyRepository = apiKeyRepository,
            )
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "apikeytestuser",
                email = "apikey@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        otherUser =
            User(
                id = UUID.randomUUID(),
                username = "otherapikeyuser",
                email = "other@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        userRepository.save(otherUser)

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

    private fun bearerFor(user: User) = "Bearer ${user.id}"

    private val createApiKeyResponseLens = Body.auto<CreateApiKeyResponse>().toLens()
    private val apiKeySummaryListLens = Body.auto<List<ApiKeySummary>>().toLens()

    // ---- Create ----

    @Test
    fun `POST api-v1-auth-api-keys creates key and returns 200`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Test Key"}""")
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `created API key starts with osk_ prefix`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Prefix Test"}""")
            )
        val body = createApiKeyResponseLens(response)
        assertTrue(body.key.startsWith("osk_"), "API key must start with osk_, got: ${body.key}")
    }

    @Test
    fun `create API key response contains keyPrefix field`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Prefix Field"}""")
            )
        val body = createApiKeyResponseLens(response)
        assertTrue(body.keyPrefix.isNotBlank(), "Response should include keyPrefix")
        assertTrue(
            body.key.startsWith(body.keyPrefix),
            "keyPrefix should be the beginning of the full key",
        )
    }

    @Test
    fun `POST api-keys without bearer returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("content-type", "application/json")
                    .body("""{"name":"No Auth"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST api-keys with blank name returns 400`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":""}""")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    // ---- List ----

    @Test
    fun `GET api-v1-auth-api-keys returns empty list when no keys exist`() {
        val response =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        assertEquals(Status.OK, response.status)
        val keys = apiKeySummaryListLens(response)
        assertTrue(keys.isEmpty(), "Should return empty list when no keys have been created")
    }

    @Test
    fun `GET api-v1-auth-api-keys returns created key in list`() {
        app(
            Request(POST, "/api/v1/auth/api-keys")
                .header("Authorization", bearerFor(testUser))
                .header("content-type", "application/json")
                .body("""{"name":"Listed Key"}""")
        )

        val response =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(response)
        assertEquals(1, keys.size, "One key should be in the list")
        assertEquals("Listed Key", keys.first().name)
    }

    @Test
    fun `GET api-keys does not return other user keys`() {
        // Create a key for otherUser
        app(
            Request(POST, "/api/v1/auth/api-keys")
                .header("Authorization", bearerFor(otherUser))
                .header("content-type", "application/json")
                .body("""{"name":"Other User Key"}""")
        )

        val response =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(response)
        assertTrue(
            keys.none { it.name == "Other User Key" },
            "testUser should not see otherUser's keys",
        )
    }

    @Test
    fun `multiple keys can be created for same user`() {
        repeat(3) { i ->
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Key $i"}""")
            )
        }

        val response =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(response)
        assertEquals(3, keys.size, "Three keys should be listed")
    }

    @Test
    fun `GET api-keys without bearer returns 401`() {
        val response = app(Request(GET, "/api/v1/auth/api-keys"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- Delete ----

    @Test
    fun `DELETE api-v1-auth-api-keys-id removes the key`() {
        val createResponse =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"To Delete"}""")
            )
        val createdKey = createApiKeyResponseLens(createResponse)

        // Find the key ID from the list
        val listResponse =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(listResponse)
        val keyId = keys.first().id

        val deleteResponse =
            app(
                Request(DELETE, "/api/v1/auth/api-keys/$keyId")
                    .header("Authorization", bearerFor(testUser))
            )
        assertEquals(Status.OK, deleteResponse.status)

        val afterDelete =
            apiKeySummaryListLens(
                app(
                    Request(GET, "/api/v1/auth/api-keys")
                        .header("Authorization", bearerFor(testUser))
                )
            )
        assertTrue(afterDelete.isEmpty(), "Key should be gone after deletion")
    }

    @Test
    fun `DELETE api-keys without bearer returns 401`() {
        val response = app(Request(DELETE, "/api/v1/auth/api-keys/999"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    // ---- Using API key as bearer token ----

    @Test
    fun `created API key can authenticate sync API requests`() {
        val createResponse =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Sync Auth Key"}""")
            )
        val apiKey = createApiKeyResponseLens(createResponse).key

        val syncResponse =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $apiKey"))
        assertEquals(Status.OK, syncResponse.status, "API key should work as sync bearer token")
    }

    @Test
    fun `deleted API key can no longer authenticate requests`() {
        val createResponse =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"name":"Soon Deleted"}""")
            )
        val apiKey = createApiKeyResponseLens(createResponse).key

        // Delete it
        val listResponse =
            app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keyId = apiKeySummaryListLens(listResponse).first().id
        app(
            Request(DELETE, "/api/v1/auth/api-keys/$keyId")
                .header("Authorization", bearerFor(testUser))
        )

        // Now try to use it
        val syncResponse =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $apiKey"))
        assertEquals(
            Status.UNAUTHORIZED,
            syncResponse.status,
            "Deleted API key must not authenticate",
        )
    }

    // ---- PUT /api/v1/auth/password (bearer protected) ----

    @Test
    fun `PUT api-v1-auth-password changes password with valid current password`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"pass","newPassword":"new-strong-pass"}""")
            )
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `PUT api-v1-auth-password with wrong current password returns 400`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"wrongpass","newPassword":"new-strong-pass"}""")
            )
        assertEquals(Status.BAD_REQUEST, response.status)
    }

    @Test
    fun `PUT api-v1-auth-password without bearer returns 401`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"pass","newPassword":"new-strong-pass"}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }
}
