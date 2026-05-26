package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserProfileResponse

interface ProfileClient {
    fun fetchProfile(): UserProfileResponse

    fun updateProfile(email: String, username: String?, avatarUrl: String?)

    fun deleteAccount(currentPassword: String)

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean)
}
