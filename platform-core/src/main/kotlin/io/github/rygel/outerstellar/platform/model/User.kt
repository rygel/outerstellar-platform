package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val enabled: Boolean = true,
    val failedLoginAttempts: Int = 0,
    val failedTotpAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val lastActivityAt: Instant? = null,
    val avatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val language: String? = null,
    val theme: String? = null,
    val layout: String? = null,
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val totpBackupCodes: String? = null,
)
