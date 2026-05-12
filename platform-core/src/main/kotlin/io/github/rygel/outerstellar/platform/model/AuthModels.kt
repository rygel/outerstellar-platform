package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

enum class UserRole {
    USER,
    ADMIN,
}

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class RegisterRequest(val username: String, val password: String)

@Serializable data class AuthTokenResponse(val token: String, val username: String, val role: String)

@Serializable data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

@Serializable
data class UserSummary(
    val id: String,
    val username: String,
    val email: String,
    val role: UserRole,
    val enabled: Boolean,
    val failedLoginAttempts: Int = 0,
    @Contextual val lockedUntil: Instant? = null,
)

@Serializable data class SetUserEnabledRequest(val enabled: Boolean)

@Serializable data class SetUserRoleRequest(val role: String)

@Serializable
data class PasswordResetToken(
    val id: Long = 0,
    @Contextual val userId: java.util.UUID,
    val token: String,
    @Contextual val expiresAt: java.time.Instant,
    val used: Boolean = false,
)

@Serializable data class PasswordResetRequest(val email: String)

@Serializable data class PasswordResetConfirm(val token: String, val newPassword: String)

@Serializable
data class AuditEntry(
    val id: Long = 0,
    val actorId: String?,
    val actorUsername: String?,
    val targetId: String?,
    val targetUsername: String?,
    val action: String,
    val detail: String?,
    @Contextual val createdAt: java.time.Instant = java.time.Instant.now(),
)

@Serializable
data class ApiKey(
    val id: Long = 0,
    @Contextual val userId: java.util.UUID,
    val keyHash: String,
    val keyPrefix: String,
    val name: String,
    val enabled: Boolean = true,
    @Contextual val createdAt: java.time.Instant = java.time.Instant.now(),
    @Contextual val lastUsedAt: java.time.Instant? = null,
)

@Serializable
data class ApiKeySummary(
    val id: Long,
    val keyPrefix: String,
    val name: String,
    val enabled: Boolean,
    val createdAt: String,
    val lastUsedAt: String?,
)

@Serializable data class CreateApiKeyRequest(val name: String)

@Serializable data class CreateApiKeyResponse(val key: String, val name: String, val keyPrefix: String)

@Serializable
data class UpdateProfileRequest(val email: String, val username: String? = null, val avatarUrl: String? = null)

@Serializable data class UpdateNotificationPrefsRequest(val emailEnabled: Boolean, val pushEnabled: Boolean)

@Serializable
data class NotificationSummary(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val read: Boolean,
    val createdAt: String,
)

@Serializable
data class UserProfileResponse(
    val username: String,
    val email: String,
    val avatarUrl: String?,
    val emailNotificationsEnabled: Boolean,
    val pushNotificationsEnabled: Boolean,
)
