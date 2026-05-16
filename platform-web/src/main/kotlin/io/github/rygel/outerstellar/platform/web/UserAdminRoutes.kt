package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.export.CsvUtils
import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.model.InsufficientPermissionException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.security.SecurityService
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
private const val MAX_PAGE_LIMIT = 100

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
                    val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, MAX_PAGE_LIMIT) ?: DEFAULT_PAGE_LIMIT
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(pageFactory.buildUserAdminPage(request.webContext, limit, offset))
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
                        val admin = ctx.user ?: throw InsufficientPermissionException("ADMIN role required")
                        val target = securityService.findUserSummary(UUID.fromString(userId))
                        if (target != null) {
                            securityService.setUserEnabled(admin.id, UUID.fromString(userId), !target.enabled)
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
                    val limit = request.query("limit")?.toIntOrNull()?.coerceIn(1, MAX_PAGE_LIMIT) ?: DEFAULT_PAGE_LIMIT
                    val offset = request.query("offset")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    renderer.render(pageFactory.buildAuditLogPage(request.webContext, limit, offset))
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
                        val admin = ctx.user ?: throw InsufficientPermissionException("ADMIN role required")
                        val target = securityService.findUserSummary(UUID.fromString(userId))
                        if (target != null) {
                            val newRole = if (target.role == UserRole.ADMIN) UserRole.USER else UserRole.ADMIN
                            securityService.setUserRole(admin.id, UUID.fromString(userId), newRole)
                        }
                        renderer.render(pageFactory.buildUserAdminPage(ctx))
                    }
                },
            "/admin/users" / userIdPath / "unlock" meta
                {
                    summary = "Unlock a locked user account"
                } bindContract
                POST to
                { userId, _ ->
                    { request: org.http4k.core.Request ->
                        val ctx = request.webContext
                        val admin = ctx.user ?: throw InsufficientPermissionException("ADMIN role required")
                        securityService.unlockAccount(admin.id, java.util.UUID.fromString(userId))
                        renderer.render(pageFactory.buildUserAdminPage(ctx))
                    }
                },
        )

    companion object {
        fun usersAsCsv(users: List<UserSummary>): String {
            val sb = StringBuilder()
            sb.appendLine(CsvUtils.toCsvRow(listOf("Username", "Email", "Role", "Enabled")))
            users.forEach { u ->
                sb.appendLine(CsvUtils.toCsvRow(listOf(u.username, u.email, u.role.name, u.enabled.toString())))
            }
            return sb.toString()
        }

        fun auditAsCsv(entries: List<io.github.rygel.outerstellar.platform.model.AuditEntry>): String {
            val sb = StringBuilder()
            sb.appendLine(CsvUtils.toCsvRow(listOf("Timestamp", "Actor", "Action", "Target", "Detail")))
            entries.forEach { e ->
                sb.appendLine(
                    CsvUtils.toCsvRow(
                        listOf(
                            e.createdAt.toString(),
                            e.actorUsername ?: "",
                            e.action,
                            e.targetUsername ?: "",
                            e.detail ?: "",
                        )
                    )
                )
            }
            return sb.toString()
        }
    }
}
