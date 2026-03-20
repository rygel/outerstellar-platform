package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.OAuthException
import io.github.rygel.outerstellar.platform.security.OAuthProvider
import io.github.rygel.outerstellar.platform.security.SecurityService
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.slf4j.LoggerFactory

/**
 * OAuth 2.0 routes for social sign-in (Sign in with Apple, etc.).
 *
 * One set of routes is registered per provider in [providers].
 *
 * Flow:
 * 1. GET /auth/oauth/{provider} → redirect to provider authorization URL
 * 2. GET /auth/oauth/{provider}/callback → exchange code, create/find user, set session cookie
 * 3. POST /auth/oauth/{provider}/callback → same as above (Apple uses form_post response mode)
 */
class OAuthRoutes(
    private val providers: Map<String, OAuthProvider>,
    private val securityService: SecurityService,
    private val sessionCookieSecure: Boolean = false,
) : ServerRoutes {

    private val logger = LoggerFactory.getLogger(OAuthRoutes::class.java)

    override val routes =
        providers.flatMap { (providerName, provider) ->
            listOf(
                "/auth/oauth/$providerName" meta
                    {
                        summary = "Initiate $providerName OAuth sign-in"
                    } bindContract
                    GET to
                    { request: Request ->
                        initiateOAuth(request, providerName, provider)
                    },
                "/auth/oauth/$providerName/callback" meta
                    {
                        summary = "$providerName OAuth callback (GET)"
                    } bindContract
                    GET to
                    { request: Request ->
                        handleCallback(request, providerName, provider)
                    },
                "/auth/oauth/$providerName/callback" meta
                    {
                        summary = "$providerName OAuth callback (POST — form_post mode)"
                    } bindContract
                    POST to
                    { request: Request ->
                        handleCallback(request, providerName, provider)
                    },
                "/auth/oauth/$providerName/not-configured" meta
                    {
                        summary = "$providerName not configured error page"
                    } bindContract
                    GET to
                    { _: Request ->
                        Response(Status.SERVICE_UNAVAILABLE)
                            .header("content-type", "text/html; charset=utf-8")
                            .body(
                                "<h2>Sign in with $providerName is not yet configured.</h2>" +
                                    "<p>The provider credentials have not been set up. " +
                                    "Please contact the administrator.</p>" +
                                    "<a href='/auth'>Back to sign in</a>"
                            )
                    },
            )
        }

    private fun initiateOAuth(
        request: Request,
        providerName: String,
        provider: OAuthProvider,
    ): Response {
        val state = java.util.UUID.randomUUID().toString()
        val redirectUri =
            "${request.uri.scheme}://${request.uri.authority}/auth/oauth/$providerName/callback"

        val stateCookie =
            Cookie(
                name = "oauth_state",
                value = state,
                maxAge = 600L,
                path = "/",
                secure = sessionCookieSecure,
                httpOnly = true,
            )

        logger.info("Initiating OAuth flow for provider={}", providerName)
        val authUrl = provider.authorizationUrl(state, redirectUri)

        return Response(Status.FOUND).header("location", authUrl).cookie(stateCookie)
    }

    private fun handleCallback(
        request: Request,
        providerName: String,
        provider: OAuthProvider,
    ): Response {
        val code =
            request.query("code")
                ?: request.bodyString().parseFormField("code")
                ?: return badCallbackResponse("Missing authorization code")

        val returnedState = request.query("state") ?: request.bodyString().parseFormField("state")
        val expectedState = request.cookie("oauth_state")?.value

        if (returnedState == null || returnedState != expectedState) {
            logger.warn(
                "OAuth state mismatch for provider={}: expected={} got={}",
                providerName,
                expectedState,
                returnedState,
            )
            return badCallbackResponse("Invalid state parameter — possible CSRF attempt")
        }

        val redirectUri =
            "${request.uri.scheme}://${request.uri.authority}/auth/oauth/$providerName/callback"

        return try {
            val userInfo = provider.exchangeCode(code, returnedState, redirectUri)
            val user =
                securityService.findOrCreateOAuthUser(
                    providerName,
                    userInfo.subject,
                    userInfo.email,
                )

            logger.info(
                "OAuth sign-in successful: user={} provider={}",
                user.username,
                providerName,
            )
            val maxAge = 365L * 24 * 3600

            Response(Status.FOUND)
                .header("location", "/")
                .cookie(
                    Cookie(
                        WebContext.SESSION_COOKIE,
                        user.id.toString(),
                        maxAge = maxAge,
                        path = "/",
                        secure = sessionCookieSecure,
                        httpOnly = true,
                    )
                )
                .cookie(Cookie("oauth_state", "", maxAge = 0L, path = "/"))
        } catch (e: OAuthException) {
            logger.warn("OAuth callback error for provider={}: {}", providerName, e.message)
            Response(Status.FOUND).header("location", "/auth?oauth_error=true")
        }
    }

    private fun badCallbackResponse(reason: String): Response {
        logger.warn("Bad OAuth callback: {}", reason)
        return Response(Status.FOUND).header("location", "/auth?oauth_error=true")
    }

    /** Parse a single form field from an `application/x-www-form-urlencoded` body. */
    private fun String.parseFormField(name: String): String? =
        split('&')
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2 && java.net.URLDecoder.decode(parts[0], "UTF-8") == name) {
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
                } else null
            }
            .firstOrNull()
}
