package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ChangePasswordRequest
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.PasswordResetConfirm
import io.github.rygel.outerstellar.platform.model.PasswordResetRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SyncException
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto

class HttpAuthClient(private val baseUrl: String, private val session: ApiSession, private val client: HttpHandler) :
    AuthClient {

    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val registerRequestLens = Body.auto<RegisterRequest>().toLens()
    private val authTokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val changePasswordLens = Body.auto<ChangePasswordRequest>().toLens()
    private val resetRequestLens = Body.auto<PasswordResetRequest>().toLens()
    private val resetConfirmLens = Body.auto<PasswordResetConfirm>().toLens()

    override fun login(username: String, password: String): AuthTokenResponse {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/login").with(loginRequestLens of LoginRequest(username, password))
        val response = client(request)
        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            session.apiToken = auth.token
            session.userRole = auth.role
            return auth
        } else {
            throw SyncException("Login failed: ${response.status}")
        }
    }

    override fun register(username: String, password: String): AuthTokenResponse {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/register")
                .with(registerRequestLens of RegisterRequest(username, password))
        val response = client(request)
        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            session.apiToken = auth.token
            session.userRole = auth.role
            return auth
        } else {
            throw SyncException("Registration failed: ${response.status}")
        }
    }

    override fun logout() {
        session.apiToken?.let { token ->
            val request = Request(POST, "$baseUrl/api/v1/auth/logout").header("Authorization", "Bearer $token")
            runCatching { client(request) }
        }
        session.clear()
    }

    override fun changePassword(currentPassword: String, newPassword: String) {
        val request =
            Request(PUT, "$baseUrl/api/v1/auth/password")
                .with(changePasswordLens of ChangePasswordRequest(currentPassword, newPassword))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Password change failed: ${response.bodyString()}")
        }
    }

    override fun requestPasswordReset(email: String) {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/reset-request").with(resetRequestLens of PasswordResetRequest(email))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset request failed: ${response.status}")
        }
    }

    override fun resetPassword(token: String, newPassword: String) {
        val request =
            Request(POST, "$baseUrl/api/v1/auth/reset-confirm")
                .with(resetConfirmLens of PasswordResetConfirm(token, newPassword))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset failed: ${response.bodyString()}")
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
