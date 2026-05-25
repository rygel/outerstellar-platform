package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.security.AccountService
import io.github.rygel.outerstellar.platform.security.ApiKeyService
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
import org.http4k.lens.long
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val apiKeyService: ApiKeyService,
    private val passwordResetService: PasswordResetService,
    private val authService: AuthService,
    private val accountService: AccountService,
    private val sessionService: SessionService,
    private val sessionCookieSecure: Boolean,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val appConfig: AppConfig,
) : ServerRoutes {
    private val logger = LoggerFactory.getLogger(AuthRoutes::class.java)
    private val modePath = Path.string().of("mode")
    private val apiKeyIdPath = Path.long().of("id")
    private val tokenPath = Path.string().of("token")

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
                                .header("Set-Cookie", SessionCookie.create(sessionToken, sessionCookieSecure))
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
            "/auth/change-password" meta
                {
                    summary = "Change password page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    if (ctx.user == null) {
                        Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                    } else {
                        renderer.render(pageFactory.buildChangePasswordPage(shellRenderer))
                    }
                },
            "/auth/components/change-password" meta
                {
                    summary = "Process password change form"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.UNAUTHORIZED).body("Not logged in")
                    } else {
                        val currentPassword = request.form("currentPassword").orEmpty()
                        val newPassword = request.form("newPassword").orEmpty()
                        val confirmPassword = request.form("confirmPassword").orEmpty()

                        if (newPassword != confirmPassword) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.password.error.title"),
                                    message = shellRenderer.i18n.translate("web.password.error.mismatch"),
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        } else {
                            try {
                                accountService.changePassword(user.id, currentPassword, newPassword)
                                renderer.render(
                                    AuthResultFragment(
                                        title = shellRenderer.i18n.translate("web.password.success.title"),
                                        message = shellRenderer.i18n.translate("web.password.success.body"),
                                        toneClass = "bg-success/10 border-success/30 text-success",
                                    )
                                )
                            } catch (e: WeakPasswordException) {
                                renderer.render(
                                    AuthResultFragment(
                                        title = shellRenderer.i18n.translate("web.password.error.title"),
                                        message = e.message ?: "Password change failed",
                                        toneClass = "bg-error/10 border-error/30 text-error",
                                    )
                                )
                            }
                        }
                    }
                },
            "/auth/reset" / tokenPath meta
                {
                    summary = "Password reset page"
                } bindContract
                GET to
                { token ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.requestContext
                        val shellRenderer = request.shellRenderer
                        renderer.render(pageFactory.buildResetPasswordPage(shellRenderer, token))
                    }
                },
            "/auth/components/reset-confirm" meta
                {
                    summary = "Process password reset form"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val token = request.form("token").orEmpty()
                    val newPassword = request.form("newPassword").orEmpty()
                    val confirmPassword = request.form("confirmPassword").orEmpty()

                    if (newPassword != confirmPassword) {
                        renderer.render(
                            AuthResultFragment(
                                title = shellRenderer.i18n.translate("web.reset.error.title"),
                                message = shellRenderer.i18n.translate("web.reset.error.mismatch"),
                                toneClass = "bg-error/10 border-error/30 text-error",
                            )
                        )
                    } else {
                        try {
                            passwordResetService.resetPassword(token, newPassword)
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.reset.success.title"),
                                    message = shellRenderer.i18n.translate("web.reset.success.body"),
                                    toneClass = "bg-success/10 border-success/30 text-success",
                                )
                            )
                        } catch (e: IllegalArgumentException) {
                            logger.warn("Password reset failed with invalid token: {}", e.message)
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.reset.error.title"),
                                    message = shellRenderer.i18n.translate("web.reset.error.invalid"),
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        } catch (e: WeakPasswordException) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.reset.error.title"),
                                    message = e.message ?: shellRenderer.i18n.translate("web.reset.error.invalid"),
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            logger.error("Unexpected error during password reset", e)
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.reset.error.title"),
                                    message = shellRenderer.i18n.translate("web.reset.error.invalid"),
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        }
                    }
                },
            "/auth/profile" meta
                {
                    summary = "User profile page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    if (ctx.user == null) {
                        Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                    } else {
                        renderer.render(pageFactory.buildProfilePage(ctx, shellRenderer))
                    }
                },
            "/auth/components/profile-update" meta
                {
                    summary = "Process profile update form"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.UNAUTHORIZED).body("Not logged in")
                    } else {
                        val newEmail = request.form("email").orEmpty()
                        val newUsername = request.form("username")?.takeIf { it.isNotBlank() }
                        val newAvatarUrl = request.form("avatarUrl")
                        try {
                            accountService.updateProfile(user.id, newEmail, newUsername, newAvatarUrl)
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.profile.success.title"),
                                    message = shellRenderer.i18n.translate("web.profile.success.body"),
                                    toneClass = "bg-success/10 border-success/30 text-success",
                                )
                            )
                        } catch (e: UsernameAlreadyExistsException) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.profile.error.title"),
                                    message = e.message ?: "Update failed",
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        } catch (e: IllegalArgumentException) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.profile.error.title"),
                                    message = e.message ?: "Update failed",
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        }
                    }
                },
            "/auth/notification-preferences" meta
                {
                    summary = "Update notification preferences"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.UNAUTHORIZED).body("Not logged in")
                    } else {
                        val emailEnabled = request.form("emailNotifications") == "on"
                        val pushEnabled = request.form("pushNotifications") == "on"
                        accountService.updateNotificationPreferences(user.id, emailEnabled, pushEnabled)
                        renderer.render(
                            AuthResultFragment(
                                title = shellRenderer.i18n.translate("web.profile.notif.success.title"),
                                message = shellRenderer.i18n.translate("web.profile.notif.success.body"),
                                toneClass = "bg-success/10 border-success/30 text-success",
                            )
                        )
                    }
                },
            "/auth/account/delete" meta
                {
                    summary = "Delete own account"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.UNAUTHORIZED).body("Not logged in")
                    } else {
                        try {
                            val currentPassword = request.form("currentPassword").orEmpty()
                            if (currentPassword.isBlank()) {
                                return@to Response(Status.BAD_REQUEST).body("Current password is required")
                            }
                            accountService.deleteAccount(user.id, currentPassword)
                            Response(Status.FOUND)
                                .header("location", shellRenderer.url("/auth?deleted=true"))
                                .header("Set-Cookie", SessionCookie.clear(sessionCookieSecure))
                        } catch (e: io.github.rygel.outerstellar.platform.model.InsufficientPermissionException) {
                            renderer.render(
                                AuthResultFragment(
                                    title = shellRenderer.i18n.translate("web.profile.delete.error.title"),
                                    message = e.message ?: "Cannot delete account",
                                    toneClass = "bg-error/10 border-error/30 text-error",
                                )
                            )
                        }
                    }
                },
            "/auth/api-keys" meta
                {
                    summary = "API keys management page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    if (ctx.user == null) {
                        Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                    } else {
                        renderer.render(pageFactory.buildApiKeysPage(ctx, shellRenderer))
                    }
                },
            "/auth/api-keys/create" meta
                {
                    summary = "Create a new API key"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val ctx = request.requestContext
                    val shellRenderer = request.shellRenderer
                    val user = ctx.user
                    if (user == null) {
                        Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                    } else {
                        val name = request.form("name").orEmpty()
                        if (name.isBlank()) {
                            renderer.render(pageFactory.buildApiKeysPage(ctx, shellRenderer))
                        } else {
                            val result = apiKeyService.createApiKey(user.id, name)
                            renderer.render(
                                pageFactory.buildApiKeysPage(
                                    ctx,
                                    shellRenderer,
                                    newKey = result.key,
                                    newKeyName = result.name,
                                )
                            )
                        }
                    }
                },
            "/auth/api-keys" / apiKeyIdPath / "delete" meta
                {
                    summary = "Delete an API key"
                } bindContract
                POST to
                { id, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.requestContext
                        val shellRenderer = request.shellRenderer
                        val user = ctx.user
                        if (user == null) {
                            Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
                        } else {
                            apiKeyService.deleteApiKey(user.id, id)
                            Response(Status.FOUND).header("location", shellRenderer.url("/auth/api-keys"))
                        }
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
