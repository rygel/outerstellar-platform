package io.github.rygel.outerstellar.platform.web.fuzz

import com.code_intelligence.jazzer.junit.FuzzTest
import io.github.rygel.outerstellar.platform.JwtConfig
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.service.UrlValidator
import io.github.rygel.outerstellar.platform.web.Filters
import io.github.rygel.outerstellar.platform.web.TokenBucket
import io.github.rygel.outerstellar.platform.web.extractAccountIdentifier
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then

class WebFuzzTest {

    companion object {
        private val jwtService =
            JwtService(
                JwtConfig(
                    enabled = true,
                    secret = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2",
                    issuer = "outerstellar-fuzz",
                    expirySeconds = 3600L,
                )
            )
    }

    @FuzzTest
    fun cspPolicyFuzz(data: String?) {
        val input = data ?: return
        Filters.securityHeaders(cspPolicy = input)
            .then { request -> Response(Status.OK).body("ok") }
            .invoke(Request(Method.GET, "/"))
    }

    @FuzzTest
    fun jwtValidationFuzz(data: String?) {
        val input = data ?: return
        jwtService.extractClaims(input)
    }

    @FuzzTest
    fun oauthCallbackParsingFuzz(data: String?) {
        val input = data ?: return
        val get = Request(Method.GET, "/auth/oauth/test/callback?code=$input&state=fuzzstate")
        get.query("code")
        get.query("state")
        val post =
            Request(Method.POST, "/auth/oauth/test/callback")
                .body(input)
                .header("content-type", "application/x-www-form-urlencoded")
        post.bodyString()
    }

    @FuzzTest
    fun rateLimiterFuzz(data: String?) {
        val input = data ?: return
        val jsonRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("""{"username":"$input","email":"$input"}""")
                .header("content-type", "application/json")
        extractAccountIdentifier(jsonRequest)
        val formRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("username=$input&email=$input")
                .header("content-type", "application/x-www-form-urlencoded")
        extractAccountIdentifier(formRequest)
    }

    @FuzzTest
    fun tokenBucketFuzz(data: String?) {
        val input = data ?: return
        val maxRequests = (input.hashCode() and 0x7FFFFFFF) % 100 + 1
        val windowMs = ((input.hashCode() / 100) and 0x7FFFFFFF).toLong() % 86400000 + 1
        TokenBucket(maxRequests, windowMs).tryConsume()
    }

    @FuzzTest
    fun inputValidationFuzz(data: String?) {
        val input = data ?: return
        try {
            UrlValidator.validate(input)
        } catch (_: IllegalArgumentException) {}
    }
}
