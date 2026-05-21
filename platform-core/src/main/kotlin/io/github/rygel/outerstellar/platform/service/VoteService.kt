package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class VoteService(private val voteRepository: VoteRepository, private val messageRepository: MessageRepository) {
    private val logger = LoggerFactory.getLogger(VoteService::class.java)

    fun vote(messageSyncId: String, userId: UUID, direction: Int): VoteScore? {
        if (messageRepository.findBySyncId(messageSyncId) == null) {
            logger.warn("Vote attempted on non-existent message: {}", messageSyncId)
            return null
        }
        val existing = voteRepository.findByUserAndMessage(userId, messageSyncId)
        if (existing == null) {
            voteRepository.save(MessageVote(messageSyncId = messageSyncId, userId = userId, direction = direction))
        } else if (existing.direction == direction) {
            voteRepository.delete(userId, messageSyncId)
        } else {
            voteRepository.updateDirection(userId, messageSyncId, direction)
        }
        return voteRepository.findScoreByMessage(messageSyncId, userId)
    }

    fun removeVote(messageSyncId: String, userId: UUID) {
        voteRepository.delete(userId, messageSyncId)
    }

    fun getScore(messageSyncId: String, userId: UUID?): VoteScore =
        voteRepository.findScoreByMessage(messageSyncId, userId)

    fun getScores(messageSyncIds: List<String>, userId: UUID?): Map<String, VoteScore> =
        voteRepository.findScoresByMessages(messageSyncIds, userId)
}
