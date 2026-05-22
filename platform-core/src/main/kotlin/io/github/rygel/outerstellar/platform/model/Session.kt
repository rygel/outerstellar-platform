package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID

data class Session(
    val id: Long = 0,
    val tokenHash: String,
    val userId: UUID,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant,
)
