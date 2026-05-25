package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import io.github.rygel.outerstellar.platform.security.SessionService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val authService: AuthService,
    private val sessionService: SessionService,
    private val passwordResetService: PasswordResetService,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val appConfig: AppConfig,
) : ServerRoutes {
    private val modePath = Path.string().of("mode")

    override val routes =
        listOf(
            "/auth" meta
                {
                    summary = "Auth landing page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildAuthPage(request.requestContext, request.shellRenderer))
                },
            "/auth/components/forms" / modePath meta
                {
                    summary = "Auth form fragment"
                } bindContract
                GET to
                { mode ->
                    { request: org.http4k.core.Request ->
                        renderer.render(pageFactory.buildAuthForm(request.requestContext, request.shellRenderer, mode))
                    }
                },
            "/auth/components/result" meta
                {
                    summary = "Process auth and show result"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val mode = request.form("mode") ?: "sign-in"
                    val email = request.form("email").orEmpty()
                    val password = request.form("password").orEmpty()
                    val returnTo = safeReturnTo(request.query("returnTo") ?: request.form("returnTo"))

                    if (mode == "sign-in") {
                        val ctx = request.requestContext
                        val shellRenderer = request.shellRenderer
                        val authResult = authService.authenticate(email, password)
                        if (authResult is AuthResult.TotpRequired) {
                            return@to renderer.render(
                                TotpChallengeForm(
                                    partialToken = authResult.token,
                                    title = shellRenderer.i18n.translate("web.totp.title"),
                                    description = shellRenderer.i18n.translate("web.totp.enterCode"),
                                    codeLabel = shellRenderer.i18n.translate("web.totp.codeLabel"),
                                    verifyLabel = shellRenderer.i18n.translate("web.totp.verifyCode"),
                                    backLinkLabel = shellRenderer.i18n.translate("web.totp.backLink"),
                                )
                            )
                        }
                        if (authResult is AuthResult.Authenticated) {
                            val user = authResult.user
                            analytics.identify(
                                user.id.toString(),
                                mapOf("username" to user.username, "role" to user.role.name),
                            )
                            analytics.track(user.id.toString(), "User Logged In")
                            val sessionToken = sessionService.createSession(user.id)
                            Response(Status.FOUND)
                                .header("location", shellRenderer.url(returnTo))
                                .header("Set-Cookie", SessionCookie.create(sessionToken, appConfig.sessionCookieSecure))
                        } else {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.auth.result.error.title"),
                                    message = "Invalid credentials",
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        }
                    } else if (mode == "register") {
                        val ctx = request.requestContext
                        val shellRenderer = request.shellRenderer
                        if (!appConfig.registrationEnabled) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.auth.result.error.title"),
                                    message = "Registration is currently disabled",
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        } else {
                            try {
                                authService.register(email, password)
                                val target = shellRenderer.url("/auth?registered=true")
                                Response(Status.FOUND).header("location", target)
                            } catch (e: UsernameAlreadyExistsException) {
                                renderer.render(
                                    AuthResultFragment(
                                        title = shellRenderer.i18n.translate("web.auth.result.error.title"),
                                        message = e.message ?: "Registration failed",
                                        toneClass = "bg-error/10 border-error/30 text-error",
                                    )
                                )
                            } catch (e: WeakPasswordException) {
                                renderer.render(
                                    AuthResultFragment(
                                        title = shellRenderer.i18n.translate("web.auth.result.error.title"),
                                        message = e.message ?: "Registration failed",
                                        toneClass = "bg-error/10 border-error/30 text-error",
                                    )
                                )
                            } catch (e: IllegalArgumentException) {
                                renderer.render(
                                    AuthResultFragment(
                                        title = shellRenderer.i18n.translate("web.auth.result.error.title"),
                                        message = e.message ?: "Registration failed",
                                        toneClass = "bg-error/10 border-error/30 text-error",
                                    )
                                )
                            }
                        }
                    } else if (mode == "recover") {
                        val ctx = request.requestContext
                        val shellRenderer = request.shellRenderer
                        if (email.isNotBlank()) {
                            passwordResetService.requestPasswordReset(email)
                        }
                        renderer.render(
                            AuthResultFragment(
                                title = shellRenderer.i18n.translate("web.auth.result.success.title"),
                                message = shellRenderer.i18n.translate("web.reset.request.success"),
                                toneClass = "bg-success/10 border-success/30 text-success",
                            )
                        )
                    } else {
                        val formValues = request.form().associate { it.first to it.second }
                        renderer.render(pageFactory.buildAuthResult(request.shellRenderer, formValues))
                    }
                },
        )

    private fun safeReturnTo(returnTo: String?): String {
        return when {
            returnTo.isNullOrBlank() -> "/"
            !returnTo.startsWith("/") -> "/"
            returnTo.startsWith("//") -> "/"
            else -> returnTo
        }
    }
}
