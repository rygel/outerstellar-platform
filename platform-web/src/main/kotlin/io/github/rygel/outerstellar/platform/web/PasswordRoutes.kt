package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.security.AccountService
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

class PasswordRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val accountService: AccountService,
    private val passwordResetService: PasswordResetService,
) : ServerRoutes {
    private val logger = LoggerFactory.getLogger(PasswordRoutes::class.java)
    private val tokenPath = Path.string().of("token")

    val publicRoutes: List<ContractRoute> =
        listOf(
            "/auth/reset" / tokenPath meta
                {
                    summary = "Password reset page"
                } bindContract
                GET to
                { token ->
                    { request: org.http4k.core.Request ->
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
        )

    val protectedRoutes: List<ContractRoute> =
        listOf(
            "/auth/change-password" meta
                {
                    summary = "Change password page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val shellRenderer = request.shellRenderer
                    renderer.render(pageFactory.buildChangePasswordPage(shellRenderer))
                },
            "/auth/components/change-password" meta
                {
                    summary = "Process password change form"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val shellRenderer = request.shellRenderer
                    val user = request.requestContext.user!!
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
                },
        )

    override val routes: List<ContractRoute> = publicRoutes + protectedRoutes
}
