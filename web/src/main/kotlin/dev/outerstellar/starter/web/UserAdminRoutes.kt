package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRole
import java.util.UUID
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class UserAdminRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val securityService: SecurityService,
) : ServerRoutes {
    private val userIdPath = Path.string().of("userId")

    override val routes =
        listOf(
            "/admin/users" meta
                {
                    summary = "User administration page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildUserAdminPage(request.webContext))
                },
            "/admin/users" / userIdPath / "toggle-enabled" meta
                {
                    summary = "Toggle user enabled status"
                } bindContract
                POST to
                { userId, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        val admin = ctx.user!!
                        val users = securityService.listUsers()
                        val target = users.find { it.id == userId }
                        if (target != null) {
                            securityService.setUserEnabled(
                                admin.id,
                                UUID.fromString(userId),
                                !target.enabled,
                            )
                        }
                        renderer.render(pageFactory.buildUserAdminPage(ctx))
                    }
                },
            "/admin/users" / userIdPath / "toggle-role" meta
                {
                    summary = "Toggle user role between USER and ADMIN"
                } bindContract
                POST to
                { userId, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        val admin = ctx.user!!
                        val users = securityService.listUsers()
                        val target = users.find { it.id == userId }
                        if (target != null) {
                            val newRole =
                                if (target.role == "ADMIN") UserRole.USER else UserRole.ADMIN
                            securityService.setUserRole(admin.id, UUID.fromString(userId), newRole)
                        }
                        renderer.render(pageFactory.buildUserAdminPage(ctx))
                    }
                },
        )
}
