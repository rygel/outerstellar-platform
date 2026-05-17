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
                    secret = "outerstellar-jazzer-fuzz-secret-2024",
                    issuer = "outerstellar-fuzz",
                    expirySeconds = 3600L,
                )
            )
    }

    @FuzzTest
    fun cspPolicyFuzz(data: String) {
        Filters.securityHeaders(cspPolicy = data)
            .then { request -> Response(Status.OK).body("ok") }
            .invoke(Request(Method.GET, "/"))
    }

    @FuzzTest
    fun jwtValidationFuzz(data: String) {
        jwtService.extractClaims(data)
    }

    @FuzzTest
    fun oauthCallbackParsingFuzz(data: String) {
        val get = Request(Method.GET, "/auth/oauth/test/callback?code=$data&state=$data")
        get.query("code")
        get.query("state")
        val post =
            Request(Method.POST, "/auth/oauth/test/callback")
                .body(data)
                .header("content-type", "application/x-www-form-urlencoded")
        post.bodyString()
    }

    @FuzzTest
    fun rateLimiterFuzz(data: String) {
        val jsonRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("""{"username":"$data","email":"$data"}""")
                .header("content-type", "application/json")
        extractAccountIdentifier(jsonRequest)
        val formRequest =
            Request(Method.POST, "/api/v1/auth/login")
                .body("username=$data&email=$data")
                .header("content-type", "application/x-www-form-urlencoded")
        extractAccountIdentifier(formRequest)
    }

    @FuzzTest
    fun tokenBucketFuzz(data: String) {
        val maxRequests = (data.hashCode() and 0x7FFFFFFF) % 100 + 1
        val windowMs = ((data.hashCode() / 100) and 0x7FFFFFFF).toLong() % 86400000 + 1
        TokenBucket(maxRequests, windowMs).tryConsume()
    }

    @FuzzTest
    fun inputValidationFuzz(data: String) {
        try {
            UrlValidator.validate(data)
        } catch (_: IllegalArgumentException) {}
    }
}
