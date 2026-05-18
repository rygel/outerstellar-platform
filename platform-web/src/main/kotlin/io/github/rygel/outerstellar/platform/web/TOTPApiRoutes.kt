package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.TotpConfirmRequest
import io.github.rygel.outerstellar.platform.model.TotpConfirmResponse
import io.github.rygel.outerstellar.platform.model.TotpDisableRequest
import io.github.rygel.outerstellar.platform.model.TotpSetupResponse
import io.github.rygel.outerstellar.platform.model.TotpVerifyRequest
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.TOTPService
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.routing.bind
import org.http4k.routing.routes

class TOTPApiRoutes(private val securityService: SecurityService, private val totpService: TOTPService) {
    private val totpVerifyRequest = Body.auto<TotpVerifyRequest>().toLens()
    private val totpVerifyResponse = Body.auto<TotpVerifyResponse>().toLens()
    private val totpSetupResponse = Body.auto<TotpSetupResponse>().toLens()
    private val totpConfirmRequest = Body.auto<TotpConfirmRequest>().toLens()
    private val totpConfirmResponse = Body.auto<TotpConfirmResponse>().toLens()
    private val totpDisableRequest = Body.auto<TotpDisableRequest>().toLens()

    val routes =
        routes(
            "/api/v1/auth/totp/verify" bind
                POST to
                { request ->
                    val body = totpVerifyRequest(request)
                    val result = securityService.verifyTotp(body.partialToken, body.code)
                    when (result.status) {
                        "success" -> Response(OK).with(totpVerifyResponse of result)
                        "invalid_code" -> Response(UNAUTHORIZED).with(totpVerifyResponse of result)
                        else -> Response(UNAUTHORIZED).with(totpVerifyResponse of result)
                    }
                },
            "/api/v1/auth/totp/setup" bind
                POST to
                { request ->
                    val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
                    val secret = totpService.generateSecret()
                    val qrDataUri = totpService.generateQrDataUri(secret, user.email)
                    Response(OK).with(totpSetupResponse of TotpSetupResponse(secret, qrDataUri))
                },
            "/api/v1/auth/totp/confirm" bind
                POST to
                { request ->
                    val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
                    val body = totpConfirmRequest(request)
                    if (!totpService.verifyCode(body.secret, body.code)) {
                        return@to Response(OK).with(totpConfirmResponse of TotpConfirmResponse("invalid_code"))
                    }
                    val (rawCodes, hashedCodes) = totpService.generateBackupCodes()
                    securityService.enableTotp(user.id, body.secret, hashedCodes)
                    Response(CREATED).with(totpConfirmResponse of TotpConfirmResponse("success", rawCodes))
                },
            "/api/v1/auth/totp/disable" bind
                POST to
                { request ->
                    val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
                    val body = totpDisableRequest(request)
                    val authResult = securityService.authenticate(user.username, body.password)
                    if (authResult !is AuthResult.Authenticated) {
                        return@to Response(UNAUTHORIZED)
                    }
                    securityService.disableTotp(user.id)
                    Response(OK)
                },
        )
}
