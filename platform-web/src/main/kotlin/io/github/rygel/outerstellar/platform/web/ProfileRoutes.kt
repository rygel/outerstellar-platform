package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.security.AccountService
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.template.TemplateRenderer

class ProfileRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val accountService: AccountService,
    private val sessionCookieSecure: Boolean,
) : ServerRoutes {
    override val routes: List<ContractRoute> =
        listOf(
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
                        } catch (e: InsufficientPermissionException) {
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
        )
}
