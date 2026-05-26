package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollWithResults
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import java.time.Instant
import java.util.UUID

class PollService(private val pollRepository: PollRepository) {

    fun createPoll(
        question: String,
        options: List<String>,
        multiChoice: Boolean,
        deadline: Instant?,
        creatorId: UUID,
    ): PollWithResults {
        require(question.isNotBlank()) { "Question must not be blank" }
        require(question.length <= 500) { "Question must be at most 500 characters" }
        require(options.size in 2..10) { "Poll must have between 2 and 10 options" }
        options.forEach { opt ->
            require(opt.isNotBlank()) { "Option text must not be blank" }
            require(opt.length <= 200) { "Option text must be at most 200 characters" }
        }

        val syncId = UUID.randomUUID().toString()
        val poll =
            Poll(
                syncId = syncId,
                creatorId = creatorId,
                question = question,
                multiChoice = multiChoice,
                deadline = deadline,
            )
        val created = pollRepository.create(poll, options)
        return getPoll(created.syncId, creatorId)!!
    }

    fun castVote(pollSyncId: String, optionId: Long, userId: UUID): PollWithResults? {
        val poll = pollRepository.findById(pollSyncId) ?: return null
        check(poll.closedAt == null) { "Poll is closed" }
        check(poll.deadline == null || !Instant.now().isAfter(poll.deadline)) { "Poll deadline has passed" }
        val option = pollRepository.findOptionById(optionId) ?: throw IllegalArgumentException("Option not found")
        require(option.pollId == poll.id) { "Option does not belong to this poll" }

        if (!poll.multiChoice) {
            val userVotes = pollRepository.getUserVotes(poll.id, userId)
            check(optionId in userVotes || userVotes.isEmpty()) {
                "Already voted on a different option in this single-choice poll"
            }
        }

        pollRepository.castVote(poll.id, optionId, userId)
        return getPoll(pollSyncId, userId)
    }

    fun removeVote(pollSyncId: String, optionId: Long, userId: UUID) {
        val poll = pollRepository.findById(pollSyncId) ?: return
        pollRepository.removeVote(poll.id, optionId, userId)
    }

    fun getPoll(syncId: String, userId: UUID?): PollWithResults? {
        val poll = pollRepository.findById(syncId) ?: return null
        val options = pollRepository.findOptions(poll.id)
        val voteCounts = pollRepository.getVoteCounts(poll.id)
        val totalVotes = voteCounts.values.sum()
        val userVotedOptionIds = if (userId != null) pollRepository.getUserVotes(poll.id, userId) else emptySet()
        return PollWithResults(poll, options, voteCounts, totalVotes, userVotedOptionIds)
    }

    fun closePoll(syncId: String, creatorId: UUID) {
        val poll = pollRepository.findById(syncId) ?: throw IllegalArgumentException("Poll not found")
        check(poll.creatorId == creatorId) { "Only the creator can close this poll" }
        pollRepository.closePoll(syncId)
    }

    fun deletePoll(syncId: String, creatorId: UUID) {
        val poll = pollRepository.findById(syncId) ?: throw IllegalArgumentException("Poll not found")
        check(poll.creatorId == creatorId) { "Only the creator can delete this poll" }
        pollRepository.delete(syncId)
    }

    fun listOpen(limit: Int = 20, offset: Int = 0): List<Poll> = pollRepository.listOpen(limit, offset)

    fun listByCreator(userId: UUID, limit: Int = 20, offset: Int = 0): List<Poll> =
        pollRepository.listByCreator(userId, limit, offset)
}
