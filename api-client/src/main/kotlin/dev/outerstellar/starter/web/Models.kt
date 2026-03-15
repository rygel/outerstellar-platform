package dev.outerstellar.starter.web

data class LoginRequest(val username: String, val password: String)

data class RegisterRequest(val username: String, val password: String)

data class AuthTokenResponse(val token: String, val username: String, val role: String)

data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

data class UserSummary(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val enabled: Boolean,
)

data class SetUserEnabledRequest(val enabled: Boolean)

data class SetUserRoleRequest(val role: String)

data class PasswordResetRequest(val email: String)

data class PasswordResetConfirm(val token: String, val newPassword: String)

data class CreateApiKeyRequest(val name: String)

data class CreateApiKeyResponse(val key: String, val name: String, val keyPrefix: String)

data class ApiKeySummary(
    val id: Long,
    val keyPrefix: String,
    val name: String,
    val enabled: Boolean,
    val createdAt: String,
    val lastUsedAt: String?,
)

data class NotificationSummary(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val read: Boolean,
    val createdAt: String,
)

data class UpdateProfileRequest(
    val email: String,
    val username: String? = null,
    val avatarUrl: String? = null,
)

data class UpdateNotificationPrefsRequest(val emailEnabled: Boolean, val pushEnabled: Boolean)

data class UserProfileResponse(
    val username: String,
    val email: String,
    val avatarUrl: String?,
    val emailNotificationsEnabled: Boolean,
    val pushNotificationsEnabled: Boolean,
)
