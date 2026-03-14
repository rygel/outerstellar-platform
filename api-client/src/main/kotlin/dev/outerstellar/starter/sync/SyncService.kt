package dev.outerstellar.starter.sync

import dev.outerstellar.starter.model.SessionExpiredException
import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.service.SyncProvider
import dev.outerstellar.starter.web.AuthTokenResponse
import dev.outerstellar.starter.web.ChangePasswordRequest
import dev.outerstellar.starter.web.LoginRequest
import dev.outerstellar.starter.web.PasswordResetConfirm
import dev.outerstellar.starter.web.PasswordResetRequest
import dev.outerstellar.starter.web.RegisterRequest
import dev.outerstellar.starter.web.SetUserEnabledRequest
import dev.outerstellar.starter.web.SetUserRoleRequest
import dev.outerstellar.starter.web.UserSummary
import org.http4k.client.JavaHttpClient
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto

class SyncService(
    private val baseUrl: String,
    private val repository: MessageRepository,
    private val transactionManager: TransactionManager,
    private val client: HttpHandler = JavaHttpClient(),
) : SyncProvider {
    @Volatile private var apiToken: String? = null
    @Volatile
    var userRole: String? = null
        private set

    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val registerRequestLens = Body.auto<RegisterRequest>().toLens()
    private val authTokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()
    private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
    private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
    private val changePasswordLens = Body.auto<ChangePasswordRequest>().toLens()
    private val userSummaryListLens = Body.auto<List<UserSummary>>().toLens()
    private val setUserEnabledLens = Body.auto<SetUserEnabledRequest>().toLens()
    private val setUserRoleLens = Body.auto<SetUserRoleRequest>().toLens()
    private val resetRequestLens = Body.auto<PasswordResetRequest>().toLens()
    private val resetConfirmLens = Body.auto<PasswordResetConfirm>().toLens()

    fun login(username: String, pass: String): AuthTokenResponse {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/login")
                .with(loginRequestLens of LoginRequest(username, pass))

        val response = client(request)

        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            this.apiToken = auth.token
            this.userRole = auth.role
            return auth
        } else {
            throw SyncException("Login failed: ${response.status}")
        }
    }

    fun register(username: String, pass: String): AuthTokenResponse {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/register")
                .with(registerRequestLens of RegisterRequest(username, pass))

        val response = client(request)

        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            this.apiToken = auth.token
            this.userRole = auth.role
            return auth
        } else {
            throw SyncException("Registration failed: ${response.status}")
        }
    }

    fun logout() {
        this.apiToken = null
        this.userRole = null
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val request =
            Request(PUT, "$baseUrl/api/v1/auth/password")
                .with(changePasswordLens of ChangePasswordRequest(currentPassword, newPassword))
        val response = authenticatedRequest(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Password change failed: ${response.bodyString()}")
        }
    }

    fun listUsers(): List<UserSummary> {
        val request = Request(GET, "$baseUrl/api/v1/admin/users")
        val response = authenticatedRequest(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to list users: ${response.status}")
        }
        return userSummaryListLens(response)
    }

    fun setUserEnabled(userId: String, enabled: Boolean) {
        val request =
            Request(PUT, "$baseUrl/api/v1/admin/users/$userId/enabled")
                .with(setUserEnabledLens of SetUserEnabledRequest(enabled))
        val response = authenticatedRequest(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user enabled: ${response.bodyString()}")
        }
    }

    fun setUserRole(userId: String, role: String) {
        val request =
            Request(PUT, "$baseUrl/api/v1/admin/users/$userId/role")
                .with(setUserRoleLens of SetUserRoleRequest(role))
        val response = authenticatedRequest(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user role: ${response.bodyString()}")
        }
    }

    fun requestPasswordReset(email: String) {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/reset-request")
                .with(resetRequestLens of PasswordResetRequest(email))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset request failed: ${response.status}")
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/reset-confirm")
                .with(resetConfirmLens of PasswordResetConfirm(token, newPassword))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset failed: ${response.bodyString()}")
        }
    }

    private fun authenticatedRequest(request: Request): Response {
        val authed = apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request
        return client(authed)
    }

    private fun checkSessionExpired(response: Response) {
        if (
            response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true"
        ) {
            throw SessionExpiredException()
        }
    }

    override fun sync(): SyncStats {
        val lastSync = repository.getLastSyncEpochMs()
        val pullRequest = Request(GET, "$baseUrl/api/v1/sync?since=$lastSync")
        val authenticatedPullRequest =
            apiToken?.let { pullRequest.header("Authorization", "Bearer $it") } ?: pullRequest

        val pullResponse = client(authenticatedPullRequest)
        if (pullResponse.status != Status.OK) {
            throw SyncException("Pull failed: ${pullResponse.status}")
        }

        val pullBody = pullResponseLens(pullResponse)

        val dirtyMessages = repository.listDirtyMessages()
        val pushRequestData = SyncPushRequest(dirtyMessages.map { it.toSyncMessage() })

        val httpRequest =
            Request(POST, "$baseUrl/api/v1/sync").with(pushRequestLens of pushRequestData)

        val authPushRequest =
            apiToken?.let { httpRequest.header("Authorization", "Bearer $it") } ?: httpRequest

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
            repository.setLastSyncEpochMs(pullBody.serverTimestamp)
        }

        return SyncStats(
            pushedCount = pushBody.appliedCount,
            pulledCount = pullBody.messages.size,
            conflictCount = pushBody.conflicts.size,
        )
    }
}
