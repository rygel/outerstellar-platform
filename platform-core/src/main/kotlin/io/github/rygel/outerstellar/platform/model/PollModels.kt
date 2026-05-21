package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class Poll(
    val id: Long = 0,
    val syncId: String,
    @Contextual val creatorId: UUID,
    val question: String,
    val multiChoice: Boolean = false,
    @Contextual val closedAt: Instant? = null,
    @Contextual val deadline: Instant? = null,
    @Contextual val createdAt: Instant = Instant.now(),
    @Contextual val updatedAt: Instant = Instant.now(),
)

@Serializable data class PollOption(val id: Long = 0, val pollId: Long, val position: Int, val optionText: String)

@Serializable
data class PollVote(
    val id: Long = 0,
    val pollId: Long,
    val optionId: Long,
    @Contextual val userId: UUID,
    @Contextual val createdAt: Instant = Instant.now(),
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
