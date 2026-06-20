package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class VoteService(
    private val voteRepository: VoteRepository,
    private val messageRepository: MessageRepository,
    private val transactionManager: TransactionManager? = null,
) {
    private val logger = LoggerFactory.getLogger(VoteService::class.java)

    fun vote(messageSyncId: String, userId: UUID, direction: Int): VoteScore? {
        if (messageRepository.findBySyncId(messageSyncId) == null) {
            logger.warn("Vote attempted on non-existent message: {}", messageSyncId)
            return null
        }
        // Wrap the read-then-write in a transaction so two concurrent identical votes don't both pass
        // the findByUserAndMessage check and race on save (unique-constraint violation → 500).
        val doVote = {
            val existing = voteRepository.findByUserAndMessage(userId, messageSyncId)
            if (existing == null) {
                voteRepository.save(MessageVote(messageSyncId = messageSyncId, userId = userId, direction = direction))
            } else if (existing.direction == direction) {
                voteRepository.delete(userId, messageSyncId)
            } else {
                voteRepository.updateDirection(userId, messageSyncId, direction)
            }
        }
        transactionManager?.inTransaction { doVote() } ?: doVote()
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
