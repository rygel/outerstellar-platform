package dev.outerstellar.platform.web

import dev.outerstellar.platform.model.InsufficientPermissionException
import dev.outerstellar.platform.model.SetUserEnabledRequest
import dev.outerstellar.platform.model.SetUserRoleRequest
import dev.outerstellar.platform.model.UserNotFoundException
import dev.outerstellar.platform.model.UserSummary
import dev.outerstellar.platform.security.SecurityRules
import dev.outerstellar.platform.security.SecurityService
import dev.outerstellar.platform.security.UserRole
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.Path
import org.http4k.lens.string

class UserAdminApi(private val securityService: SecurityService) : ServerRoutes {
    private val userSummaryListLens = Body.auto<List<UserSummary>>().toLens()
    private val setUserEnabledLens = Body.auto<SetUserEnabledRequest>().toLens()
    private val setUserRoleLens = Body.auto<SetUserRoleRequest>().toLens()
    private val userIdPath = Path.string().of("userId")

    override val routes: List<ContractRoute> =
        listOf(
            "/api/v1/admin/users" meta
                {
                    summary = "List all users (admin only)"
                    returning(Status.OK, userSummaryListLens to emptyList())
                } bindContract
                GET to
                { request ->
                    val users = securityService.listUsers()
                    Response(Status.OK).with(userSummaryListLens of users)
                },
            "/api/v1/admin/users" / userIdPath / "enabled" meta
                {
                    summary = "Enable or disable a user (admin only)"
                    receiving(setUserEnabledLens)
                } bindContract
                PUT to
                { userId, _ ->
                    { request ->
                        val admin = SecurityRules.USER_KEY(request)!!
                        try {
                            val body = setUserEnabledLens(request)
                            securityService.setUserEnabled(
                                admin.id,
                                UUID.fromString(userId),
                                body.enabled,
                            )
                            Response(Status.OK).body("User updated")
                        } catch (e: UserNotFoundException) {
                            Response(Status.NOT_FOUND).body(e.message ?: "User not found")
                        } catch (e: InsufficientPermissionException) {
                            Response(Status.BAD_REQUEST).body(e.message ?: "Not allowed")
                        }
                    }
                },
            "/api/v1/admin/users" / userIdPath / "role" meta
                {
                    summary = "Change a user's role (admin only)"
                    receiving(setUserRoleLens)
                } bindContract
                PUT to
                { userId, _ ->
                    { request ->
                        val admin = SecurityRules.USER_KEY(request)!!
                        try {
                            val body = setUserRoleLens(request)
                            val role = UserRole.valueOf(body.role.uppercase())
                            securityService.setUserRole(admin.id, UUID.fromString(userId), role)
                            Response(Status.OK).body("User role updated")
                        } catch (e: UserNotFoundException) {
                            Response(Status.NOT_FOUND).body(e.message ?: "User not found")
                        } catch (e: InsufficientPermissionException) {
                            Response(Status.BAD_REQUEST).body(e.message ?: "Not allowed")
                        } catch (e: IllegalArgumentException) {
                            Response(Status.BAD_REQUEST).body("Invalid role")
                        }
                    }
                },
        )
}
