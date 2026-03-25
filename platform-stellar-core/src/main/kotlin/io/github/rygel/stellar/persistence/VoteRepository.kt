package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Vote
import io.github.rygel.stellar.model.VoteTarget
import java.util.UUID

interface VoteRepository {
    fun findVote(userId: UUID, targetId: UUID, targetType: VoteTarget): Vote?

    fun upsert(vote: Vote)

    fun delete(userId: UUID, targetId: UUID, targetType: VoteTarget)

    fun findUserVotes(userId: UUID, targetIds: List<UUID>, targetType: VoteTarget): Map<UUID, Int>
}
