package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

data class Poll(
    val id: Long = 0,
    val syncId: String,
    val creatorId: UUID,
    val question: String,
    val multiChoice: Boolean = false,
    val closedAt: Instant? = null,
    val deadline: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class PollOption(val id: Long = 0, val pollId: Long, val position: Int, val optionText: String)

data class PollVote(
    val id: Long = 0,
    val pollId: Long,
    val optionId: Long,
    val userId: UUID,
    val createdAt: Instant = Instant.now(),
)

@Serializable
data class PollWithResults(
    @Contextual val poll: Poll,
    val options: List<@Contextual PollOption>,
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
