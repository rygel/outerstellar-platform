package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.DeleteAccountRequest
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UpdateNotificationPrefsRequest
import io.github.rygel.outerstellar.platform.model.UpdateProfileRequest
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto

class HttpProfileClient(private val baseUrl: String, private val session: ApiSession, private val client: HttpHandler) :
    ProfileClient {

    private val userProfileResponseLens = Body.auto<UserProfileResponse>().toLens()
    private val updateProfileLens = Body.auto<UpdateProfileRequest>().toLens()
    private val updateNotifPrefsLens = Body.auto<UpdateNotificationPrefsRequest>().toLens()
    private val deleteAccountLens = Body.auto<DeleteAccountRequest>().toLens()

    override fun fetchProfile(): UserProfileResponse {
        val response = authenticated(Request(GET, "$baseUrl/api/v1/auth/profile"))
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to fetch profile: ${response.status}")
        }
        return userProfileResponseLens(response)
    }

    override fun updateProfile(email: String, username: String?, avatarUrl: String?) {
        val request =
            Request(PUT, "$baseUrl/api/v1/auth/profile")
                .with(updateProfileLens of UpdateProfileRequest(email, username, avatarUrl))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException(response.bodyString().ifBlank { "Profile update failed" })
        }
    }

    override fun deleteAccount(currentPassword: String) {
        val response =
            authenticated(
                Request(DELETE, "$baseUrl/api/v1/auth/account")
                    .with(deleteAccountLens of DeleteAccountRequest(currentPassword))
            )
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException(response.bodyString().ifBlank { "Account deletion failed" })
        }
    }

    override fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean) {
        val request =
            Request(PUT, "$baseUrl/api/v1/auth/notification-preferences")
                .with(updateNotifPrefsLens of UpdateNotificationPrefsRequest(emailEnabled, pushEnabled))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update notification preferences: ${response.status}")
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
