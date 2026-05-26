package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

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
