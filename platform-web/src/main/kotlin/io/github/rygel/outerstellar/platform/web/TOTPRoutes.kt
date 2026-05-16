package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.body.form
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.template.TemplateRenderer

class TOTPRoutes(
    private val securityService: SecurityService,
    private val renderer: TemplateRenderer,
    private val sessionCookieSecure: Boolean,
) {
    val routes =
        routes(
            "/auth/components/totp-verify" bind
                POST to
                { request ->
                    val partialToken = request.form("partialToken") ?: return@to Response(OK).body("Missing token")
                    val code = request.form("code") ?: return@to Response(OK).body("Missing code")

                    val result = securityService.verifyTotp(partialToken, code)
                    when (result.status) {
                        "success" -> {
                            val sessionToken = result.token ?: return@to Response(OK).body("Missing session token")
                            Response(OK)
                                .header("Set-Cookie", SessionCookie.create(sessionToken, sessionCookieSecure))
                                .header("HX-Redirect", "/")
                        }
                        "invalid_code" ->
                            Response(OK).body(renderer(TotpChallengeForm(partialToken, "Invalid code. Try again.")))
                        else ->
                            Response(OK)
                                .body(
                                    renderer(TotpChallengeForm(partialToken, "Session expired. Please log in again."))
                                )
                    }
                }
        )
}
