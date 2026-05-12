package io.github.rygel.outerstellar.platform.web

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the rate limiter (brute-force protection on auth endpoints).
 *
 * Covers:
 * - First 10 requests within the window succeed (no 429)
 * - The 11th request from the same IP to /api/v1/auth/login returns 429
 * - Different IPs are tracked independently (no cross-IP pollution)
 * - Rate limiter applies to /api/v1/auth/register as well
 * - Rate limit is not applied to unrelated endpoints
 */
class RateLimiterIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp()
    }

    @AfterEach fun teardown() = cleanup()

    /** Sends a login POST with a specific IP via X-Forwarded-For and returns the response. */
    private fun loginRequest(ip: String) =
        app(
            Request(POST, "/api/v1/auth/login")
                .header("content-type", "application/json")
                .header("X-Forwarded-For", ip)
                .body("""{"username":"nobody","password":"wrong"}""")
        )

    @Test
    fun `first request to login endpoint is not rate limited`() {
        val ip = "10.0.${UUID.randomUUID().toString().take(3)}.1"
        val response = loginRequest(ip)
        assertTrue(
            response.status != Status.TOO_MANY_REQUESTS,
            "First request should not be rate limited, got: ${response.status}",
        )
    }

    @Test
    fun `eleven requests from same IP triggers 429 on login endpoint`() {
        // Use a unique IP to avoid interference from other tests sharing this app instance
        val ip = "192.168.${(1..254).random()}.${(1..254).random()}"

        repeat(10) { loginRequest(ip) }

        val response = loginRequest(ip)
        assertEquals(Status.TOO_MANY_REQUESTS, response.status, "11th request from same IP should be rate limited")
    }

    @Test
    fun `different IPs are rate-limited independently`() {
        val ip1 = "10.1.1.1"
        val ip2 = "10.2.2.2"

        // Exhaust ip1's bucket
        repeat(10) { loginRequest(ip1) }
        assertEquals(Status.TOO_MANY_REQUESTS, loginRequest(ip1).status)

        // ip2 should not be affected
        val ip2Response = loginRequest(ip2)
        assertTrue(
            ip2Response.status != Status.TOO_MANY_REQUESTS,
            "Different IP should not be rate limited, got: ${ip2Response.status}",
        )
    }

    @Test
    fun `rate limiter also applies to register endpoint`() {
        val ip = "172.16.${(1..254).random()}.${(1..254).random()}"
        fun registerRequest() =
            app(
                Request(POST, "/api/v1/auth/register")
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"testuser${UUID.randomUUID()}","password":"short"}""")
            )

        repeat(10) { registerRequest() }

        val response = registerRequest()
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            response.status,
            "11th register request from same IP should be rate limited",
        )
    }

    @Test
    fun `rate limiter does not apply to health endpoint`() {
        val ip = "203.0.113.1"
        repeat(15) {
            val response = app(Request(org.http4k.core.Method.GET, "/health").header("X-Forwarded-For", ip))
            assertTrue(response.status != Status.TOO_MANY_REQUESTS, "Health endpoint should never be rate limited")
        }
    }

    @Test
    fun `per-account rate limit triggers when same username is tried from many IPs`() {
        val username = "target-user-${UUID.randomUUID()}"
        repeat(20) { i ->
            val ip = "10.99.0.${i + 1}"
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"$username","password":"wrong"}""")
            )
        }

        val response =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", "10.99.0.100")
                    .body("""{"username":"$username","password":"wrong"}""")
            )
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            response.status,
            "21st request for same account from different IP should be rate limited",
        )
    }

    @Test
    fun `different usernames from same IP use separate account buckets`() {
        val ip = "10.88.${(1..254).random()}.${(1..254).random()}"
        val targetAccount = "target-${UUID.randomUUID()}"

        repeat(9) { i ->
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"filler-$i","password":"wrong"}""")
            )
        }

        val response =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"$targetAccount","password":"wrong"}""")
            )
        assertTrue(
            response.status != Status.TOO_MANY_REQUESTS,
            "Different usernames should have separate account buckets (10 requests from one IP, all within per-IP limit), got: ${response.status}",
        )
    }

    @Test
    fun `per-account rate limit works with form-encoded email`() {
        val email = "target-${UUID.randomUUID()}@test.com"
        repeat(20) { i ->
            val ip = "10.77.0.${i + 1}"
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .header("X-Forwarded-For", ip)
                    .body("mode=sign-in&email=${java.net.URLEncoder.encode(email, "UTF-8")}&password=wrong")
            )
        }

        val response =
            app(
                Request(POST, "/auth/components/result")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .header("X-Forwarded-For", "10.77.0.100")
                    .body("mode=sign-in&email=${java.net.URLEncoder.encode(email, "UTF-8")}&password=wrong")
            )
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            response.status,
            "Per-account limit should apply to form-encoded email too",
        )
    }
}
