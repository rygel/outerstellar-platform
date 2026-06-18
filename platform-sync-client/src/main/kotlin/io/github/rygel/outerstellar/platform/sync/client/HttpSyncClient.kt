package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto

class HttpSyncClient(private val baseUrl: String, private val session: ApiSession, private val client: HttpHandler) :
    SyncClient {

    private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()
    private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
    private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()

    override fun pull(since: Long): SyncPullResponse {
        val request = Request(GET, "$baseUrl/api/v1/sync?since=$since")
        val response = authenticated(request)
        if (response.status == Status.UNAUTHORIZED || response.status == Status.FORBIDDEN) {
            // A 401/403 means the bearer token is expired/revoked. Throw SessionExpiredException specifically
            // (not the generic SyncException) so SyncDataModuleImpl.sync()'s SessionExpiredException arm
            // catches it, clears state, and fires onSessionExpired — otherwise auto-sync loops forever on
            // the dead token. The two exceptions are siblings (both extend OuterstellarException), so the
            // generic Exception arm does NOT catch 401 as session-expiry. See issue #522.
            throw SessionExpiredException("Pull rejected: ${response.status}")
        }
        if (response.status != Status.OK) {
            throw SyncException("Pull failed: ${response.status}")
        }
        return pullResponseLens(response)
    }

    override fun push(request: SyncPushRequest): SyncPushResponse {
        val httpRequest = Request(POST, "$baseUrl/api/v1/sync").with(pushRequestLens of request)
        val response = authenticated(httpRequest)
        if (response.status == Status.UNAUTHORIZED || response.status == Status.FORBIDDEN) {
            throw SessionExpiredException("Push rejected: ${response.status}")
        }
        if (response.status != Status.OK) {
            throw SyncException("Push failed: ${response.status}")
        }
        return pushResponseLens(response)
    }

    private fun authenticated(request: Request): Response {
        val authed = session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request
        return client(authed)
    }
}
