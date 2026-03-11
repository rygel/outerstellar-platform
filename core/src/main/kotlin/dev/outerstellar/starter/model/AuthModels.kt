package dev.outerstellar.starter.model

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val password: String)
data class AuthTokenResponse(val token: String, val username: String, val role: String)
