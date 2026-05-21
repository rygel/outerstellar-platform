package io.github.rygel.outerstellar.platform.sync.engine.module

data class ProfileState(
    val userEmail: String = "",
    val userAvatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
)

interface ProfileListener {
    fun onProfileStateChanged(state: ProfileState) {}

    fun onSessionExpired() {}
}

interface ProfileModule {
    val profileState: ProfileState

    fun addListener(listener: ProfileListener)

    fun removeListener(listener: ProfileListener)

    fun loadProfile()

    fun updateProfile(email: String, username: String? = null, avatarUrl: String? = null): Result<Unit>

    fun deleteAccount(currentPassword: String): Result<Unit>

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit>
}
