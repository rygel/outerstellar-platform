package io.github.rygel.outerstellar.platform.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

data class MessageVote(
    val id: Long = 0,
    val messageSyncId: String,
    val userId: UUID,
    val direction: Int,
    val createdAt: Instant = Instant.now(),
)

@Serializable
data class VoteScore(
    val messageSyncId: String,
    val upvotes: Int,
    val downvotes: Int,
    val netScore: Int,
    val userVote: Int? = null,
)
