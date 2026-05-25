package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollOption
import java.util.UUID

interface PollRepository {
    fun create(poll: Poll, options: List<String>): Poll

    fun findById(syncId: String): Poll?

    fun findOptions(pollId: Long): List<PollOption>

    fun findOptionById(optionId: Long): PollOption?

    fun castVote(pollId: Long, optionId: Long, userId: UUID)

    fun removeVote(pollId: Long, optionId: Long, userId: UUID)

    fun getUserVotes(pollId: Long, userId: UUID): Set<Long>

    fun getVoteCounts(pollId: Long): Map<Long, Int>

    fun closePoll(syncId: String)

    fun listOpen(limit: Int, offset: Int): List<Poll>

    fun listByCreator(userId: UUID, limit: Int, offset: Int): List<Poll>

    fun delete(syncId: String)
}
