package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import java.util.UUID

interface VoteRepository {
    fun save(vote: MessageVote)

    fun updateDirection(userId: UUID, messageSyncId: String, direction: Int)

    fun delete(userId: UUID, messageSyncId: String)

    fun findByUserAndMessage(userId: UUID, messageSyncId: String): MessageVote?

    fun findScoresByMessages(messageSyncIds: List<String>, userId: UUID?): Map<String, VoteScore>

    fun findScoreByMessage(messageSyncId: String, userId: UUID?): VoteScore
}
