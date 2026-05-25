package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiVoteRepository(private val jdbi: Jdbi) : VoteRepository {

    override fun save(vote: MessageVote) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_message_votes (message_sync_id, user_id, direction, created_at)
                    VALUES (:messageSyncId, :userId, :direction, :createdAt)
                    """
                )
                .bind("messageSyncId", vote.messageSyncId)
                .bind("userId", vote.userId)
                .bind("direction", vote.direction)
                .bind("createdAt", vote.createdAt)
                .execute()
        }
    }

    override fun updateDirection(userId: UUID, messageSyncId: String, direction: Int) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE plt_message_votes SET direction = :direction WHERE user_id = :userId AND message_sync_id = :messageSyncId"
                )
                .bind("direction", direction)
                .bind("userId", userId)
                .bind("messageSyncId", messageSyncId)
                .execute()
        }
    }

    override fun delete(userId: UUID, messageSyncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "DELETE FROM plt_message_votes WHERE user_id = :userId AND message_sync_id = :messageSyncId"
                )
                .bind("userId", userId)
                .bind("messageSyncId", messageSyncId)
                .execute()
        }
    }

    override fun findByUserAndMessage(userId: UUID, messageSyncId: String): MessageVote? {
        return jdbi.withHandle<MessageVote?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, message_sync_id, user_id, direction, created_at FROM plt_message_votes WHERE user_id = :userId AND message_sync_id = :messageSyncId"
                )
                .bind("userId", userId)
                .bind("messageSyncId", messageSyncId)
                .map { rs, _ -> mapVote(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findScoresByMessages(messageSyncIds: List<String>, userId: UUID?): Map<String, VoteScore> {
        if (messageSyncIds.isEmpty()) return emptyMap()

        return jdbi.withHandle<Map<String, VoteScore>, Exception> { handle ->
            val placeholders = messageSyncIds.indices.joinToString(", ") { ":id_$it" }

            val scores = mutableMapOf<String, MutablePair>()
            for (msgId in messageSyncIds) {
                scores[msgId] = MutablePair(0, 0)
            }

            val aggregateQuery =
                """
                SELECT message_sync_id,
                       SUM(CASE WHEN direction = 1 THEN 1 ELSE 0 END) as upvotes,
                       SUM(CASE WHEN direction = -1 THEN 1 ELSE 0 END) as downvotes
                FROM plt_message_votes
                WHERE message_sync_id IN ($placeholders)
                GROUP BY message_sync_id
                """
            var aggQuery = handle.createQuery(aggregateQuery)
            messageSyncIds.forEachIndexed { index, id -> aggQuery = aggQuery.bind("id_$index", id) }
            aggQuery
                .map { rs, _ ->
                    val msgId = rs.getString("message_sync_id")
                    val up = rs.getInt("upvotes")
                    val down = rs.getInt("downvotes")
                    scores[msgId] = MutablePair(up, down)
                    null
                }
                .list()

            val userVotes = mutableMapOf<String, Int>()
            if (userId != null) {
                val userQuery =
                    """
                    SELECT message_sync_id, direction FROM plt_message_votes
                    WHERE user_id = :userId AND message_sync_id IN ($placeholders)
                    """
                var uq = handle.createQuery(userQuery).bind("userId", userId)
                messageSyncIds.forEachIndexed { index, id -> uq = uq.bind("id_$index", id) }
                uq.map { rs, _ ->
                        val msgId = rs.getString("message_sync_id")
                        val dir = rs.getInt("direction")
                        userVotes[msgId] = dir
                        null
                    }
                    .list()
            }

            val result = mutableMapOf<String, VoteScore>()
            for (msgId in messageSyncIds) {
                val pair = scores[msgId] ?: MutablePair(0, 0)
                result[msgId] =
                    VoteScore(
                        messageSyncId = msgId,
                        upvotes = pair.upvotes,
                        downvotes = pair.downvotes,
                        netScore = pair.upvotes - pair.downvotes,
                        userVote = userVotes[msgId],
                    )
            }
            result
        }
    }

    override fun findScoreByMessage(messageSyncId: String, userId: UUID?): VoteScore {
        return findScoresByMessages(listOf(messageSyncId), userId)[messageSyncId]
            ?: VoteScore(messageSyncId, 0, 0, 0, null)
    }

    private fun mapVote(rs: java.sql.ResultSet): MessageVote {
        return MessageVote(
            id = rs.getLong("id"),
            messageSyncId = rs.getString("message_sync_id"),
            userId = rs.getObject("user_id", UUID::class.java),
            direction = rs.getInt("direction"),
            createdAt = rs.getRequiredInstant("created_at"),
        )
    }

    private data class MutablePair(var upvotes: Int, var downvotes: Int)
}
