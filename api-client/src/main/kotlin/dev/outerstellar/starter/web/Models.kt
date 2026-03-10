package dev.outerstellar.starter.web

data class LoginRequest(val username: String, val password: String)
data class AuthTokenResponse(val token: String, val username: String, val role: String)
