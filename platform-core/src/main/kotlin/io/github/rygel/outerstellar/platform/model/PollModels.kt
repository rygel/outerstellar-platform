package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    val id: Long = 0,
    val syncId: String,
    @Serializable(with = UuidSerializer::class) val creatorId: UUID,
    val question: String,
    val multiChoice: Boolean = false,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val deadline: Instant? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = Instant.now(),
)

@Serializable data class PollOption(val id: Long = 0, val pollId: Long, val position: Int, val optionText: String)

@Serializable
data class PollVote(
    val id: Long = 0,
    val pollId: Long,
    val optionId: Long,
    @Serializable(with = UuidSerializer::class) val userId: UUID,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.now(),
)

@Serializable
data class PollWithResults(
    val poll: Poll,
    val options: List<PollOption>,
    val voteCounts: Map<Long, Int>,
    val totalVotes: Int,
    val userVotedOptionIds: Set<Long> = emptySet(),
)

@Serializable
data class CreatePollRequest(
    val question: String,
    val options: List<String>,
    val multiChoice: Boolean = false,
    val deadline: String? = null,
)
