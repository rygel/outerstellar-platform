package io.github.rygel.outerstellar.platform.web.fuzz

import com.code_intelligence.jazzer.junit.FuzzTest
import io.github.rygel.outerstellar.platform.JwtConfig
import io.github.rygel.outerstellar.platform.persistence.OAuthUserInfo
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.OAuthException
import io.github.rygel.outerstellar.platform.security.OAuthProvider
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.service.UrlValidator
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.OAuthRoutes
import io.github.rygel.outerstellar.platform.web.TokenBucket
import io.github.rygel.outerstellar.platform.web.extractAccountIdentifier
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.then
import org.http4k.format.KotlinxSerialization

class WebFuzzTest {

    companion object {
        private const val OAUTH_STATE = "fuzzstate"

        private val jwtService =
            JwtService(
                JwtConfig(
                    enabled = true,
                    secret = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
                    issuer = "outerstellar-fuzz",
                    expirySeconds = 3600L,
                )
            )

        private val oauthHandler = contract {
            renderer = OpenApi3(ApiInfo("OAuth Fuzz", "v1.0"), KotlinxSerialization)
            routes +=
                OAuthRoutes(
                        providers =
                            mapOf(
                                "test" to
                                    object : OAuthProvider {
                                        override val name: String = "test"

                                        override fun authorizationUrl(state: String, redirectUri: String): String =
                                            "$redirectUri?state=$state"

                                        override fun exchangeCode(
                                            code: String,
                                            state: String,
                                            redirectUri: String,
                                        ): OAuthUserInfo = throw OAuthException("Fuzzed callback should fail closed")
                                    }
                            ),
                        oauthService = mockk<OAuthService>(relaxed = true),
                        sessionService = mockk<SessionService>(relaxed = true),
                    )
                    .routes
        }
    }

    @FuzzTest
    fun cspPolicyFuzz(data: String?) {
        val input = data.orEmpty()
        val filter = Filters.securityHeaders(cspPolicy = input).then { _: Request -> Response(Status.OK).body("ok") }

        val uiResponse = filter(Request(Method.GET, "/"))
        assertEquals(input, uiResponse.header("Content-Security-Policy"))

        val apiResponse = filter(Request(Method.GET, "/api/v1/health"))
        assertNull(apiResponse.header("Content-Security-Policy"))
    }

    @FuzzTest
    fun jwtValidationFuzz(data: String?) {
        jwtService.extractClaims(data.orEmpty())
    }

    @FuzzTest
    fun oauthCallbackParsingFuzz(data: String?) {
        val request =
            Request(Method.POST, "/auth/oauth/test/callback")
                .body(data.orEmpty())
                .header("content-type", "application/x-www-form-urlencoded")
                .cookie(Cookie("oauth_state", OAUTH_STATE))
        val response = oauthHandler(request)

        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("oauth_error=true"))
    }

    @FuzzTest
    fun rateLimiterFuzz(data: String?) {
        val input = data.orEmpty()
        val jsonRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("""{"username":"$input","email":"$input"}""")
                .header("content-type", "application/json")
        val jsonIdentifier = extractAccountIdentifier(jsonRequest)
        assertTrue(jsonIdentifier == null || jsonIdentifier == jsonIdentifier.trim().lowercase())

        val formRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("username=$input&email=$input")
                .header("content-type", "application/x-www-form-urlencoded")
        val formIdentifier = extractAccountIdentifier(formRequest)
        assertTrue(formIdentifier == null || formIdentifier == formIdentifier.trim().lowercase())
    }

    @FuzzTest
    fun tokenBucketFuzz(data: String?) {
        val input = data.orEmpty()
        val maxRequests = (input.hashCode() and 0x7FFFFFFF) % 32 + 1
        val windowMs = ((input.hashCode() / 100) and 0x7FFFFFFF).toLong() % 86_400_000 + 1
        val bucket = TokenBucket(maxRequests, windowMs)

        repeat(maxRequests) { assertTrue(bucket.tryConsume()) }
        assertFalse(bucket.tryConsume())
    }

    @FuzzTest
    fun inputValidationFuzz(data: String?) {
        try {
            UrlValidator.validate(data.orEmpty())
        } catch (_: IllegalArgumentException) {}
    }
}
