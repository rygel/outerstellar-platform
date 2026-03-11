package dev.outerstellar.starter.sync

import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.web.AuthTokenResponse
import dev.outerstellar.starter.web.LoginRequest
import dev.outerstellar.starter.web.RegisterRequest
import org.http4k.client.JavaHttpClient
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto

class SyncService(
    private val baseUrl: String,
    private val repository: MessageRepository,
    private val transactionManager: TransactionManager,
    private val client: HttpHandler = JavaHttpClient(),
) : SyncProvider {
    private var apiToken: String? = null

    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val registerRequestLens = Body.auto<RegisterRequest>().toLens()
    private val authTokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()
    private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
    private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()

    fun login(username: String, pass: String): AuthTokenResponse {
        val request = Request(POST, "$baseUrl/api/v1/auth/login")
            .with(loginRequestLens of LoginRequest(username, pass))

        val response = client(request)

        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            this.apiToken = auth.token
            return auth
        } else {
            throw SyncException("Login failed: ${response.status}")
        }
    }

    fun register(username: String, pass: String): AuthTokenResponse {
        val request = Request(POST, "$baseUrl/api/v1/auth/register")
            .with(registerRequestLens of RegisterRequest(username, pass))

        val response = client(request)

        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            this.apiToken = auth.token
            return auth
        } else {
            throw SyncException("Registration failed: ${response.status}")
        }
    }

    fun logout() {
        this.apiToken = null
    }

    override fun sync(): SyncStats {
        val lastSync = repository.getLastSyncEpochMs()
        val pullRequest = Request(GET, "$baseUrl/api/v1/sync?since=$lastSync")
        val authenticatedPullRequest = apiToken?.let {
            pullRequest.header("Authorization", "Bearer $it")
        } ?: pullRequest

        val pullResponse = client(authenticatedPullRequest)
        if (pullResponse.status != Status.OK) {
            throw SyncException("Pull failed: ${pullResponse.status}")
        }

        val pullBody = pullResponseLens(pullResponse)

        val dirtyMessages = repository.listDirtyMessages()
        val pushRequestData = SyncPushRequest(dirtyMessages.map { it.toSyncMessage() })

        val httpRequest = Request(POST, "$baseUrl/api/v1/sync")
            .with(pushRequestLens of pushRequestData)

        val authPushRequest = apiToken?.let {
            httpRequest.header("Authorization", "Bearer $it")
        } ?: httpRequest

        val pushResponse = client(authPushRequest)
        if (pushResponse.status != Status.OK) {
            throw SyncException("Push failed: ${pushResponse.status}")
        }

        val pushBody = pushResponseLens(pushResponse)

        transactionManager.inTransaction {
            pullBody.messages.forEach { repository.upsertSyncedMessage(it, false) }
            pushBody.conflicts.forEach { conflict ->
                conflict.serverMessage?.let { repository.upsertSyncedMessage(it, false) }
            }
        }
        repository.setLastSyncEpochMs(pullBody.serverTimestamp)

        return SyncStats(
            pushedCount = pushBody.appliedCount,
            pulledCount = pullBody.messages.size,
            conflictCount = pushBody.conflicts.size,
        )
    }
}
