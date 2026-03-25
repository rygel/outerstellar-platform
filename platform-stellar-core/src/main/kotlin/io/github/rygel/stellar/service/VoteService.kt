package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Vote
import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.StellarTransactionManager
import io.github.rygel.stellar.persistence.VoteRepository
import java.util.UUID

class VoteService(
    private val voteRepo: VoteRepository,
    private val postRepo: PostRepository,
    private val commentRepo: CommentRepository,
    private val karmaRepo: KarmaRepository,
    private val txManager: StellarTransactionManager? = null,
) {
    fun vote(userId: UUID, targetId: UUID, targetType: VoteTarget, value: Int): Int {
        require(value == 1 || value == -1) { "Vote value must be +1 or -1" }

        return txManager?.inTransaction { doVote(userId, targetId, targetType, value) }
            ?: doVote(userId, targetId, targetType, value)
    }

    private fun doVote(userId: UUID, targetId: UUID, targetType: VoteTarget, value: Int): Int {
        val existing = voteRepo.findVote(userId, targetId, targetType)
        val scoreDelta: Int

        if (existing != null) {
            if (existing.value == value) {
                // Undo vote
                voteRepo.delete(userId, targetId, targetType)
                scoreDelta = -existing.value
            } else {
                // Change vote direction
                voteRepo.upsert(Vote(userId = userId, targetId = targetId, targetType = targetType, value = value))
                scoreDelta = value - existing.value
            }
        } else {
            // New vote
            voteRepo.upsert(Vote(userId = userId, targetId = targetId, targetType = targetType, value = value))
            scoreDelta = value
        }

        when (targetType) {
            VoteTarget.POST -> {
                postRepo.updateScore(targetId, scoreDelta)
                karmaRepo.updatePostKarma(userId, scoreDelta)
            }
            VoteTarget.COMMENT -> {
                commentRepo.updateScore(targetId, scoreDelta)
                karmaRepo.updateCommentKarma(userId, scoreDelta)
            }
        }

        return scoreDelta
    }

    fun getUserVote(userId: UUID, targetId: UUID, targetType: VoteTarget): Int? =
        voteRepo.findVote(userId, targetId, targetType)?.value

    fun getUserVotesForTargets(userId: UUID, targetIds: List<UUID>, targetType: VoteTarget): Map<UUID, Int> =
        if (targetIds.isEmpty()) emptyMap() else voteRepo.findUserVotes(userId, targetIds, targetType)

    fun getScore(targetId: UUID, targetType: VoteTarget): Int =
        when (targetType) {
            VoteTarget.POST -> postRepo.findById(targetId)?.score ?: 0
            VoteTarget.COMMENT -> commentRepo.findById(targetId)?.score ?: 0
        }
}
