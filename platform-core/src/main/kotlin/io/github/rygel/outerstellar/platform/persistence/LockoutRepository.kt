package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.util.UUID

interface LockoutRepository {
    fun incrementFailedLoginAttempts(userId: UUID): Int

    fun resetFailedLoginAttempts(userId: UUID)

    fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)
}
