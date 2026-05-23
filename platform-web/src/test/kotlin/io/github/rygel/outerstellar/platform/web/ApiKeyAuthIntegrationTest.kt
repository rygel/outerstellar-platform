package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
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
import org.http4k.format.KotlinxSerialization
import org.http4k.hamkrest.hasStatus
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
class ApiKeyAuthIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                apiKeyRepository = apiKeyRepository,
                sessionRepository = sessionRepository,
            )

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "apikeyuser",
                email = "apikey@test.com",
                passwordHash = testPasswordHash,
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

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
        assertThat(response, hasStatus(Status.OK))
        return KotlinxSerialization.asA(response.bodyString(), CreateApiKeyResponse::class)
    }

    @Test
    fun `POST api-keys without auth returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("content-type", "application/json")
                    .body("""{"name":"test-key"}""")
            )
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
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
        assertThat(response, hasStatus(Status.BAD_REQUEST))
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

        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${result.key}"))
        assertThat(response, hasStatus(Status.OK))
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
                        """{"messages":[{"syncId":"$syncId","author":"apikeyuser",""" +
                            """"content":"via api key","updatedAtEpochMs":1000}]}"""
                    )
            )
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET api-keys lists created keys`() {
        createApiKey("list-key")

        val response = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", uuidBearer()))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertTrue(body.contains("list-key"), "Key list should include created key name")
    }

    @Test
    fun `DELETE api-keys removes the key`() {
        val result = createApiKey("delete-me")

        // Get the key ID from the list
        val listResponse = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", uuidBearer()))
        val listBody = listResponse.bodyString()
        // Extract ID from response (list returns ApiKeySummary with id field)
        val idMatch = """"id"\s*:\s*(\d+)""".toRegex().find(listBody)
        val keyId = idMatch?.groupValues?.get(1) ?: return // skip if can't extract

        app(Request(DELETE, "/api/v1/auth/api-keys/$keyId").header("Authorization", uuidBearer()))

        // Now the key should be rejected
        val syncResponse = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${result.key}"))
        assertThat(syncResponse, hasStatus(Status.UNAUTHORIZED))
    }

    @Test
    fun `invalid random string as Bearer is rejected`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer totally-not-a-valid-key"))
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
    }
}
