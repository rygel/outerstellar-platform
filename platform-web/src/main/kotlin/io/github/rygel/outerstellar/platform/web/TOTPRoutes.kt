package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.TOTPService
import org.http4k.core.Method.GET
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
    private val totpService: TOTPService,
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
                },
            "/auth/components/totp-setup-status" bind
                GET to
                { request ->
                    val ctx = WebContext.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val remaining = countBackupCodes(user.totpBackupCodes)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(totpEnabled = user.totpEnabled, totpRemainingBackupCodes = remaining)
                            )
                        )
                },
            "/auth/components/totp-setup" bind
                POST to
                { request ->
                    val ctx = WebContext.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val secret = totpService.generateSecret()
                    val qrDataUri = totpService.generateQrDataUri(secret, user.email)
                    Response(OK).body(renderer(TotpSetupFragment(totpQrDataUri = qrDataUri, totpSecret = secret)))
                },
            "/auth/components/totp-verify-setup" bind
                POST to
                { request ->
                    val secret = request.form("secret") ?: return@to Response(OK).body("Missing secret")
                    val code = request.form("code") ?: return@to Response(OK).body("Missing code")
                    val ctx = WebContext.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")

                    if (!totpService.verifyCode(secret, code)) {
                        val qrDataUri = totpService.generateQrDataUri(secret, user.email)
                        return@to Response(OK)
                            .body(renderer(TotpSetupFragment(totpQrDataUri = qrDataUri, totpSecret = secret)))
                    }
                    val (rawCodes, hashedCodes) = totpService.generateBackupCodes()
                    securityService.enableTotp(user.id, secret, hashedCodes)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(
                                    totpEnabled = true,
                                    totpBackupCodes = rawCodes,
                                    totpRemainingBackupCodes = rawCodes.size,
                                )
                            )
                        )
                },
            "/auth/components/totp-disable" bind
                POST to
                { request ->
                    val password = request.form("password") ?: return@to Response(OK).body("Missing password")
                    val ctx = WebContext.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val authResult = securityService.authenticate(user.username, password)
                    if (authResult !is AuthResult.Authenticated) {
                        return@to Response(OK)
                            .body("""<div id="totp-setup" class="alert alert-error">Incorrect password</div>""")
                    }
                    securityService.disableTotp(user.id)
                    Response(OK).body(renderer(TotpSetupFragment(totpEnabled = false)))
                },
        )
}

private fun countBackupCodes(json: String?): Int {
    if (json.isNullOrBlank()) return 0
    return json
        .removeSurrounding("[", "]")
        .split(",")
        .map { it.trim().removeSurrounding("\"") }
        .count { it.isNotBlank() }
}
