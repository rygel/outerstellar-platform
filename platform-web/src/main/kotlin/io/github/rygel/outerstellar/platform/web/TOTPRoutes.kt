package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.SessionService
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
    private val authService: AuthService,
    private val renderer: TemplateRenderer,
    private val sessionCookieSecure: Boolean,
    private val totpService: TOTPService,
    private val sessionService: SessionService,
) {
    val routes =
        routes(
            "/auth/components/totp-verify" bind
                POST to
                { request ->
                    val ctx = RequestContext.KEY(request)
                    val shellRenderer = ShellRenderer.KEY(request)
                    val partialToken = request.form("partialToken") ?: return@to Response(OK).body("Missing token")
                    val code = request.form("code") ?: return@to Response(OK).body("Missing code")

                    val result = authService.verifyTotp(partialToken, code, sessionService)
                    when (result.status) {
                        "success" -> {
                            val sessionToken = result.token ?: return@to Response(OK).body("Missing session token")
                            Response(OK)
                                .header("Set-Cookie", SessionCookie.create(sessionToken, sessionCookieSecure))
                                .header("HX-Redirect", "/")
                        }
                        "invalid_code" ->
                            Response(OK)
                                .body(
                                    renderer(
                                        TotpChallengeForm(
                                            partialToken = partialToken,
                                            title = shellRenderer.i18n.translate("web.totp.title"),
                                            description = shellRenderer.i18n.translate("web.totp.enterCode"),
                                            codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                            verifyLabel = shellRenderer.i18n.translate("web.totp.verifyCode"),
                                            backLinkLabel = shellRenderer.i18n.translate("web.totp.backLink"),
                                            error = shellRenderer.i18n.translate("web.totp.invalidCode"),
                                        )
                                    )
                                )
                        "expired" ->
                            Response(OK)
                                .body(
                                    renderer(
                                        TotpChallengeForm(
                                            partialToken = partialToken,
                                            title = shellRenderer.i18n.translate("web.totp.title"),
                                            description = shellRenderer.i18n.translate("web.totp.enterCode"),
                                            codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                            verifyLabel = shellRenderer.i18n.translate("web.totp.verifyCode"),
                                            backLinkLabel = shellRenderer.i18n.translate("web.totp.backLink"),
                                            error = shellRenderer.i18n.translate("web.totp.sessionExpired"),
                                        )
                                    )
                                )
                        else ->
                            Response(OK)
                                .body(
                                    renderer(
                                        TotpChallengeForm(
                                            partialToken = partialToken,
                                            title = shellRenderer.i18n.translate("web.totp.title"),
                                            description = shellRenderer.i18n.translate("web.totp.enterCode"),
                                            codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                            verifyLabel = shellRenderer.i18n.translate("web.totp.verifyCode"),
                                            backLinkLabel = shellRenderer.i18n.translate("web.totp.backLink"),
                                            error = shellRenderer.i18n.translate("web.totp.sessionExpired"),
                                        )
                                    )
                                )
                    }
                },
            "/auth/components/totp-setup-status" bind
                GET to
                { request ->
                    val ctx = RequestContext.KEY(request)
                    val shellRenderer = ShellRenderer.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val remaining = countBackupCodes(user.totpBackupCodes)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(
                                    totpEnabled = user.totpEnabled,
                                    totpRemainingBackupCodes = remaining,
                                    enabledLabel = shellRenderer.i18n.translate("web.totp.enabled"),
                                    disableLabel = shellRenderer.i18n.translate("web.totp.disable"),
                                    passwordLabel = shellRenderer.i18n.translate("web.totp.passwordLabel"),
                                    backupCodesLabel = shellRenderer.i18n.translate("web.totp.backupCodes"),
                                    backupCodesHint = shellRenderer.i18n.translate("web.totp.backupCodes.hint"),
                                    backupCodesRemainingLabel =
                                        shellRenderer.i18n.translate("web.totp.backupCodes.remaining"),
                                    copyLabel = shellRenderer.i18n.translate("web.totp.backupCodes.copy"),
                                    downloadLabel = shellRenderer.i18n.translate("web.totp.backupCodes.download"),
                                    disabledLabel = shellRenderer.i18n.translate("web.totp.scanQr"),
                                    manualKeyLabel = shellRenderer.i18n.translate("web.totp.manualKey"),
                                    codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                    setupLabel = shellRenderer.i18n.translate("web.totp.enable"),
                                    verifyLabel = shellRenderer.i18n.translate("web.totp.verifyAndEnable"),
                                )
                            )
                        )
                },
            "/auth/components/totp-setup" bind
                POST to
                { request ->
                    val ctx = RequestContext.KEY(request)
                    val shellRenderer = ShellRenderer.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val secret = totpService.generateSecret()
                    val qrDataUri = totpService.generateQrDataUri(secret, user.email)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(
                                    totpQrDataUri = qrDataUri,
                                    totpSecret = secret,
                                    disabledLabel = shellRenderer.i18n.translate("web.totp.scanQr"),
                                    manualKeyLabel = shellRenderer.i18n.translate("web.totp.manualKey"),
                                    codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                    verifyLabel = shellRenderer.i18n.translate("web.totp.verifyAndEnable"),
                                )
                            )
                        )
                },
            "/auth/components/totp-verify-setup" bind
                POST to
                { request ->
                    val secret = request.form("secret") ?: return@to Response(OK).body("Missing secret")
                    val code = request.form("code") ?: return@to Response(OK).body("Missing code")
                    val ctx = RequestContext.KEY(request)
                    val shellRenderer = ShellRenderer.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")

                    if (!totpService.verifyCode(secret, code)) {
                        val qrDataUri = totpService.generateQrDataUri(secret, user.email)
                        return@to Response(OK)
                            .body(
                                renderer(
                                    TotpSetupFragment(
                                        totpQrDataUri = qrDataUri,
                                        totpSecret = secret,
                                        disabledLabel = shellRenderer.i18n.translate("web.totp.scanQr"),
                                        manualKeyLabel = shellRenderer.i18n.translate("web.totp.manualKey"),
                                        codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                        verifyLabel = shellRenderer.i18n.translate("web.totp.verifyAndEnable"),
                                    )
                                )
                            )
                    }
                    val (rawCodes, hashedCodes) = totpService.generateBackupCodes()
                    authService.enableTotp(user.id, secret, hashedCodes)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(
                                    totpEnabled = true,
                                    totpBackupCodes = rawCodes,
                                    totpRemainingBackupCodes = rawCodes.size,
                                    enabledLabel = shellRenderer.i18n.translate("web.totp.enabled"),
                                    disableLabel = shellRenderer.i18n.translate("web.totp.disable"),
                                    passwordLabel = shellRenderer.i18n.translate("web.totp.passwordLabel"),
                                    backupCodesLabel = shellRenderer.i18n.translate("web.totp.backupCodes"),
                                    backupCodesHint = shellRenderer.i18n.translate("web.totp.backupCodes.hint"),
                                    backupCodesRemainingLabel =
                                        shellRenderer.i18n.translate("web.totp.backupCodes.remaining"),
                                    copyLabel = shellRenderer.i18n.translate("web.totp.backupCodes.copy"),
                                    downloadLabel = shellRenderer.i18n.translate("web.totp.backupCodes.download"),
                                )
                            )
                        )
                },
            "/auth/components/totp-disable" bind
                POST to
                { request ->
                    val password = request.form("password") ?: return@to Response(OK).body("Missing password")
                    val ctx = RequestContext.KEY(request)
                    val shellRenderer = ShellRenderer.KEY(request)
                    val user = ctx.user ?: return@to Response(OK).body("Not authenticated")
                    val authResult = authService.authenticate(user.username, password)
                    if (authResult !is AuthResult.Authenticated) {
                        return@to Response(OK)
                            .body(
                                renderer(
                                    TotpSetupFragment(
                                        totpEnabled = true,
                                        enabledLabel = shellRenderer.i18n.translate("web.totp.enabled"),
                                        disableLabel = shellRenderer.i18n.translate("web.totp.disable"),
                                        passwordLabel = shellRenderer.i18n.translate("web.totp.passwordLabel"),
                                        backupCodesLabel = shellRenderer.i18n.translate("web.totp.backupCodes"),
                                        backupCodesHint = shellRenderer.i18n.translate("web.totp.backupCodes.hint"),
                                        backupCodesRemainingLabel =
                                            shellRenderer.i18n.translate("web.totp.backupCodes.remaining"),
                                        copyLabel = shellRenderer.i18n.translate("web.totp.backupCodes.copy"),
                                        downloadLabel = shellRenderer.i18n.translate("web.totp.backupCodes.download"),
                                        totpRemainingBackupCodes = countBackupCodes(user.totpBackupCodes),
                                    )
                                )
                            )
                    }
                    authService.disableTotp(user.id)
                    Response(OK)
                        .body(
                            renderer(
                                TotpSetupFragment(
                                    totpEnabled = false,
                                    disabledLabel = shellRenderer.i18n.translate("web.totp.scanQr"),
                                    setupLabel = shellRenderer.i18n.translate("web.totp.enable"),
                                )
                            )
                        )
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
