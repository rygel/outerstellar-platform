package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Session
import java.time.Instant
import java.util.UUID

interface SessionRepository {
    fun save(session: Session)

    fun findByTokenHash(tokenHash: String): Session?

    fun findByTokenHashIncludingExpired(tokenHash: String): Session?

    fun updateExpiresAt(tokenHash: String, expiresAt: Instant)

    fun deleteByTokenHash(tokenHash: String)

    fun deleteByUserId(userId: UUID)

    fun deleteExpired()
}
