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
