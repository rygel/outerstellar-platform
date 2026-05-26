package io.github.rygel.outerstellar.platform.security

data class SecurityConfig(
    val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    val sessionTimeoutSeconds: Long = 1800L,
    val maxFailedLoginAttempts: Int = 10,
    val lockoutDurationSeconds: Long = 900,
    val sessionAbsoluteTimeoutSeconds: Long = 86400L,
    val registrationEnabled: Boolean = true,
)
