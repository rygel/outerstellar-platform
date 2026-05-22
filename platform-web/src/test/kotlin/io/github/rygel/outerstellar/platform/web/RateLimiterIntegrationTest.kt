package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.RequestSource
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.hamkrest.hasStatus

class RateLimiterIntegrationTest : WebTest() {

    private val app by lazy { buildApp() }

    private fun loginRequest(ip: String) =
        app(
            Request(POST, "/api/v1/auth/login")
                .source(RequestSource(ip))
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
        val ip = "192.168.${(1..254).random()}.${(1..254).random()}"

        repeat(10) { loginRequest(ip) }

        val response = loginRequest(ip)
        assertThat(response, hasStatus(Status.TOO_MANY_REQUESTS))
    }

    @Test
    fun `different client IPs behind trusted proxy are rate-limited independently`() {
        val filter =
            rateLimitFilter(
                maxRequests = 2,
                windowMs = 60_000L,
                trustedProxies = listOf("10.0.0.1"),
                pathPrefixes = listOf("/api/v1/auth/login"),
            )
        val handler = filter.then { Response(Status.OK) }

        repeat(2) {
            val req =
                Request(POST, "/api/v1/auth/login")
                    .source(RequestSource("10.0.0.1"))
                    .header("X-Forwarded-For", "1.2.3.4")
            assertEquals(Status.OK, handler(req).status)
        }

        val blocked =
            Request(POST, "/api/v1/auth/login").source(RequestSource("10.0.0.1")).header("X-Forwarded-For", "1.2.3.4")
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            handler(blocked).status,
            "3rd request from same client IP should be rate limited",
        )

        val differentIp =
            Request(POST, "/api/v1/auth/login").source(RequestSource("10.0.0.1")).header("X-Forwarded-For", "5.6.7.8")
        assertTrue(
            handler(differentIp).status != Status.TOO_MANY_REQUESTS,
            "Different client IP behind trusted proxy should not be rate limited",
        )
    }

    @Test
    fun `rate limiter also applies to register endpoint`() {
        val ip = "172.16.${(1..254).random()}.${(1..254).random()}"
        fun registerRequest() =
            app(
                Request(POST, "/api/v1/auth/register")
                    .source(RequestSource(ip))
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
                    .source(RequestSource(ip))
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"$username","password":"wrong"}""")
            )
        }

        val response =
            app(
                Request(POST, "/api/v1/auth/login")
                    .source(RequestSource("10.99.0.100"))
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
    fun `spoofed X-Forwarded-For is ignored when trusted proxies are configured`() {
        val handler: HttpHandler = { Response(Status.OK) }
        val neverTrusted =
            rateLimitFilter(
                    trustedProxies = listOf("10.0.0.1", "10.0.0.2"),
                    pathPrefixes = listOf("/api/v1/auth/login"),
                    maxRequests = 3,
                    windowMs = 60000L,
                )
                .then(handler)

        repeat(3) { i ->
            val response = neverTrusted(Request(POST, "/api/v1/auth/login").header("X-Forwarded-For", "1.2.3.$i"))
            assertTrue(response.status != Status.TOO_MANY_REQUESTS, "Request $i should not be blocked yet")
        }

        val blockedResponse = neverTrusted(Request(POST, "/api/v1/auth/login").header("X-Forwarded-For", "9.9.9.9"))
        assertEquals(
            Status.TOO_MANY_REQUESTS,
            blockedResponse.status,
            "4th request with different spoofed IP should be blocked because all hit the 'unknown' bucket",
        )
    }

    @Test
    fun `different usernames from same IP use separate account buckets`() {
        val ip = "10.88.${(1..254).random()}.${(1..254).random()}"
        val targetAccount = "target-${UUID.randomUUID()}"

        repeat(9) { i ->
            app(
                Request(POST, "/api/v1/auth/login")
                    .source(RequestSource(ip))
                    .header("content-type", "application/json")
                    .header("X-Forwarded-For", ip)
                    .body("""{"username":"filler-$i","password":"wrong"}""")
            )
        }

        val response =
            app(
                Request(POST, "/api/v1/auth/login")
                    .source(RequestSource(ip))
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
    fun `rate limiter ignores X-Forwarded-For when no trusted proxies configured`() {
        val filter = rateLimitFilter(maxRequests = 2, windowMs = 60_000L, trustedProxies = emptyList())
        val handler = filter.then { Response(Status.OK) }

        for (i in 1..2) {
            val req =
                Request(POST, "/api/v1/auth/login")
                    .header("X-Forwarded-For", "1.2.3.$i")
                    .header("content-type", "application/json")
                    .body("""{"username":"user$i"}""")
            assertEquals(Status.OK, handler(req).status)
        }

        val spoofedReq =
            Request(POST, "/api/v1/auth/login")
                .header("X-Forwarded-For", "9.9.9.9")
                .header("content-type", "application/json")
                .body("""{"username":"spoofed"}""")
        assertEquals(Status.TOO_MANY_REQUESTS, handler(spoofedReq).status)
    }

    @Test
    fun `per-account rate limit works with form-encoded email`() {
        val email = "target-${UUID.randomUUID()}@test.com"
        repeat(20) { i ->
            val ip = "10.77.0.${i + 1}"
            app(
                Request(POST, "/auth/components/result")
                    .source(RequestSource(ip))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .header("X-Forwarded-For", ip)
                    .body("mode=sign-in&email=${java.net.URLEncoder.encode(email, "UTF-8")}&password=wrong")
            )
        }

        val response =
            app(
                Request(POST, "/auth/components/result")
                    .source(RequestSource("10.77.0.100"))
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
