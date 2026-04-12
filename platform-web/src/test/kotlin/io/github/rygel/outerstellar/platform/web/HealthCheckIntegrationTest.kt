package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the /health endpoint.
 *
 * Covers:
 * - GET /health returns 200
 * - Response is JSON with content-type application/json
 * - Response contains "status": "UP"
 * - Response contains "database" object with "status": "UP"
 * - Response contains user count in database.users
 * - User count increases after registering a new user
 * - Response contains "timestamp" field
 * - No authentication required
 */
class HealthCheckIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    @Test
    fun `GET health returns 200`() {
        val response = app(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET health returns JSON content-type`() {
        val response = app(Request(GET, "/health"))
        val contentType = response.header("content-type") ?: ""
        assertTrue(contentType.contains("application/json"), "Should return JSON, got: $contentType")
    }

    @Test
    fun `GET health contains status UP`() {
        val response = app(Request(GET, "/health"))
        val body = response.bodyString()
        assertTrue(body.contains("\"status\""), "Should contain status field")
        assertTrue(body.contains("UP"), "Status should be UP, got: $body")
    }

    @Test
    fun `GET health contains database object`() {
        val response = app(Request(GET, "/health"))
        val body = response.bodyString()
        assertTrue(body.contains("database"), "Should contain database field, got: $body")
    }

    @Test
    fun `GET health database status is UP`() {
        val response = app(Request(GET, "/health"))
        val body = response.bodyString()
        // database.status should be UP
        val databaseIdx = body.indexOf("database")
        assertTrue(databaseIdx >= 0, "Should contain database section")
        val dbSection = body.substring(databaseIdx)
        assertTrue(dbSection.contains("UP"), "Database status should be UP, got: $dbSection")
    }

    @Test
    fun `GET health contains timestamp field`() {
        val response = app(Request(GET, "/health"))
        val body = response.bodyString()
        assertTrue(body.contains("timestamp"), "Should contain timestamp field, got: $body")
    }

    @Test
    fun `GET health does not require authentication`() {
        // No auth header, no cookies — should still succeed
        val response = app(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET health user count reflects actual users in database`() {
        // Add a user and check count increases
        val userBefore = parseUserCount(app(Request(GET, "/health")).bodyString())

        val newUser =
            User(
                id = UUID.randomUUID(),
                username = "healthcheckuser",
                email = "healthcheck@test.com",
                passwordHash = "x".repeat(60),
                role = UserRole.USER,
            )
        userRepository.save(newUser)

        val userAfter = parseUserCount(app(Request(GET, "/health")).bodyString())
        assertTrue(
            userAfter > userBefore,
            "User count should increase after saving user: before=$userBefore, after=$userAfter",
        )
    }

    private fun parseUserCount(body: String): Int {
        // Extract users count from JSON: "users":N
        val match = """"users"\s*:\s*(\d+)""".toRegex().find(body)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }
}
