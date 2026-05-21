package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SetUserEnabledRequest
import io.github.rygel.outerstellar.platform.model.SetUserRoleRequest
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserSummary
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto

class HttpAdminClient(private val baseUrl: String, private val session: ApiSession, private val client: HttpHandler) :
    AdminClient {

    private val userSummaryListLens = Body.auto<List<UserSummary>>().toLens()
    private val setUserEnabledLens = Body.auto<SetUserEnabledRequest>().toLens()
    private val setUserRoleLens = Body.auto<SetUserRoleRequest>().toLens()

    override fun listUsers(): List<UserSummary> {
        val request = Request(GET, "$baseUrl/api/v1/admin/users")
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to list users: ${response.status}")
        }
        return userSummaryListLens(response)
    }

    override fun setUserEnabled(userId: String, enabled: Boolean) {
        val request =
            Request(PUT, "$baseUrl/api/v1/admin/users/$userId/enabled")
                .with(setUserEnabledLens of SetUserEnabledRequest(enabled))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user enabled: ${response.bodyString()}")
        }
    }

    override fun setUserRole(userId: String, role: String) {
        val request =
            Request(PUT, "$baseUrl/api/v1/admin/users/$userId/role").with(setUserRoleLens of SetUserRoleRequest(role))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user role: ${response.bodyString()}")
        }
    }

    private fun authenticated(request: Request): Response {
        val authed = session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request
        return client(authed)
    }

    private fun checkSessionExpired(response: Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw SessionExpiredException()
        }
    }
}
