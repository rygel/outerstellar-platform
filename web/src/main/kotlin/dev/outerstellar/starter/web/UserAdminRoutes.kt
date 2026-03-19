package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import dev.outerstellar.starter.model.InsufficientPermissionException
import dev.outerstellar.starter.model.UserSummary
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRole
import java.util.UUID
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

private const val DEFAULT_PAGE_LIMIT = 20

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
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, 100)
                            ?: DEFAULT_PAGE_LIMIT
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(
                        pageFactory.buildUserAdminPage(request.webContext, limit, offset)
                    )
                },
            "/admin/users/export" meta
                {
                    summary = "Export users as CSV"
                } bindContract
                GET to
                { _: org.http4k.core.Request ->
                    val users = securityService.listUsers()
                    Response(Status.OK)
                        .header("Content-Type", "text/csv; charset=utf-8")
                        .header("Content-Disposition", "attachment; filename=\"users.csv\"")
                        .body(usersAsCsv(users))
                },
            "/admin/users" / userIdPath / "toggle-enabled" meta
                {
                    summary = "Toggle user enabled status"
                } bindContract
                POST to
                { userId, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        val admin =
                            ctx.user ?: throw InsufficientPermissionException("ADMIN role required")
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
            "/admin/audit" meta
                {
                    summary = "Audit log page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    val limit =
                        request.query("limit")?.toIntOrNull()?.coerceIn(1, 100)
                            ?: DEFAULT_PAGE_LIMIT
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(
                        pageFactory.buildAuditLogPage(request.webContext, limit, offset)
                    )
                },
            "/admin/audit/export" meta
                {
                    summary = "Export audit log as CSV"
                } bindContract
                GET to
                { _: org.http4k.core.Request ->
                    val entries = securityService.getAuditLog(limit = Int.MAX_VALUE)
                    Response(Status.OK)
                        .header("Content-Type", "text/csv; charset=utf-8")
                        .header("Content-Disposition", "attachment; filename=\"audit.csv\"")
                        .body(auditAsCsv(entries))
                },
            "/admin/users" / userIdPath / "toggle-role" meta
                {
                    summary = "Toggle user role between USER and ADMIN"
                } bindContract
                POST to
                { userId, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        val admin =
                            ctx.user ?: throw InsufficientPermissionException("ADMIN role required")
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

    companion object {
        fun usersAsCsv(users: List<UserSummary>): String {
            val sb = StringBuilder()
            sb.appendLine("Username,Email,Role,Enabled")
            users.forEach { u ->
                sb.appendLine(
                    "${escapeCsv(u.username)},${escapeCsv(u.email)},${u.role},${u.enabled}"
                )
            }
            return sb.toString()
        }

        fun auditAsCsv(entries: List<dev.outerstellar.starter.model.AuditEntry>): String {
            val sb = StringBuilder()
            sb.appendLine("Timestamp,Actor,Action,Target,Detail")
            entries.forEach { e ->
                sb.appendLine(
                    "${escapeCsv(e.createdAt.toString())}," +
                        "${escapeCsv(e.actorUsername ?: "")}," +
                        "${escapeCsv(e.action)}," +
                        "${escapeCsv(e.targetUsername ?: "")}," +
                        escapeCsv(e.detail ?: "")
                )
            }
            return sb.toString()
        }

        private fun escapeCsv(value: String): String {
            return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        }
    }
}
