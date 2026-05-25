package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.model.ApiKeySummary
import io.github.rygel.outerstellar.platform.model.CreateApiKeyResponse
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
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
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.hamkrest.hasStatus
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
class ApiKeyLifecycleIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var testUserPassword: String
    private lateinit var otherUser: User
    private lateinit var testToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setupTest() {
        testUserPassword = testPassword()
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "apikeytestuser",
                email = "apikey@test.com",
                passwordHash = encoder.encode(testUserPassword),
                role = UserRole.USER,
            )
        otherUser =
            User(
                id = UUID.randomUUID(),
                username = "otherapikeyuser",
                email = "other@test.com",
                passwordHash = testPasswordHash,
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        userRepository.save(otherUser)
        testToken = sessionSvc.createSession(testUser.id)
        otherToken = sessionSvc.createSession(otherUser.id)

        app = buildApp()
    }

    private fun bearerFor(user: User) = if (user == testUser) "Bearer $testToken" else "Bearer $otherToken"

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
        assertThat(response, hasStatus(Status.OK))
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
        assertTrue(body.key.startsWith(body.keyPrefix), "keyPrefix should be the beginning of the full key")
    }

    @Test
    fun `POST api-keys without bearer returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/auth/api-keys")
                    .header("content-type", "application/json")
                    .body("""{"name":"No Auth"}""")
            )
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
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
        assertThat(response, hasStatus(Status.BAD_REQUEST))
    }

    // ---- List ----

    @Test
    fun `GET api-v1-auth-api-keys returns empty list when no keys exist`() {
        val response = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        assertThat(response, hasStatus(Status.OK))
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

        val response = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
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

        val response = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(response)
        assertTrue(keys.none { it.name == "Other User Key" }, "testUser should not see otherUser's keys")
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

        val response = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(response)
        assertEquals(3, keys.size, "Three keys should be listed")
    }

    @Test
    fun `GET api-keys without bearer returns 401`() {
        val response = app(Request(GET, "/api/v1/auth/api-keys"))
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
    }

    // ---- Delete ----

    @Test
    fun `DELETE api-v1-auth-api-keys-id removes the key`() {
        app(
            Request(POST, "/api/v1/auth/api-keys")
                .header("Authorization", bearerFor(testUser))
                .header("content-type", "application/json")
                .body("""{"name":"To Delete"}""")
        )

        // Find the key ID from the list
        val listResponse = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keys = apiKeySummaryListLens(listResponse)
        val keyId = keys.first().id

        val deleteResponse =
            app(Request(DELETE, "/api/v1/auth/api-keys/$keyId").header("Authorization", bearerFor(testUser)))
        assertThat(deleteResponse, hasStatus(Status.OK))

        val afterDelete =
            apiKeySummaryListLens(
                app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
            )
        assertTrue(afterDelete.isEmpty(), "Key should be gone after deletion")
    }

    @Test
    fun `DELETE api-keys without bearer returns 401`() {
        val response = app(Request(DELETE, "/api/v1/auth/api-keys/999"))
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
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

        val syncResponse = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $apiKey"))
        assertThat(syncResponse, hasStatus(Status.OK))
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
        val listResponse = app(Request(GET, "/api/v1/auth/api-keys").header("Authorization", bearerFor(testUser)))
        val keyId = apiKeySummaryListLens(listResponse).first().id
        app(Request(DELETE, "/api/v1/auth/api-keys/$keyId").header("Authorization", bearerFor(testUser)))

        // Now try to use it
        val syncResponse = app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer $apiKey"))
        assertThat(syncResponse, hasStatus(Status.UNAUTHORIZED))
    }

    // ---- PUT /api/v1/auth/password (bearer protected) ----

    @Test
    fun `PUT api-v1-auth-password changes password with valid current password`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"$testUserPassword","newPassword":"${testPassword()}"}""")
            )
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `PUT api-v1-auth-password with wrong current password returns 400`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("Authorization", bearerFor(testUser))
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"wrongpass","newPassword":"${testPassword()}"}""")
            )
        assertThat(response, hasStatus(Status.BAD_REQUEST))
    }

    @Test
    fun `PUT api-v1-auth-password without bearer returns 401`() {
        val response =
            app(
                Request(PUT, "/api/v1/auth/password")
                    .header("content-type", "application/json")
                    .body("""{"currentPassword":"$testUserPassword","newPassword":"${testPassword()}"}""")
            )
        assertThat(response, hasStatus(Status.UNAUTHORIZED))
    }
}
