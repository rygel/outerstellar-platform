package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse

interface AuthClient {
    fun login(username: String, password: String): AuthTokenResponse

    fun register(username: String, password: String): AuthTokenResponse

    fun logout()

    fun changePassword(currentPassword: String, newPassword: String)

    fun requestPasswordReset(email: String)

    fun resetPassword(token: String, newPassword: String)
}
