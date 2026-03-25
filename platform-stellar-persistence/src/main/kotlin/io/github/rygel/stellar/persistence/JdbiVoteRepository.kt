package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Vote
import io.github.rygel.stellar.model.VoteTarget
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiVoteRepository(private val jdbi: Jdbi) : VoteRepository {

    override fun findVote(userId: UUID, targetId: UUID, targetType: VoteTarget): Vote? =
        jdbi.withHandle<Vote?, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT * FROM stl_votes
                WHERE user_id = :userId AND target_id = :targetId AND target_type = :targetType
                """
                )
                .bind("userId", userId)
                .bind("targetId", targetId)
                .bind("targetType", targetType.name)
                .map { rs, _ ->
                    Vote(
                        id = rs.getLong("id"),
                        userId = rs.getObject("user_id", UUID::class.java),
                        targetId = rs.getObject("target_id", UUID::class.java),
                        targetType = VoteTarget.valueOf(rs.getString("target_type")),
                        value = rs.getInt("value"),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .findOne()
                .orElse(null)
        }

    override fun upsert(vote: Vote) {
        jdbi.useHandle<Exception> { handle ->
            // Try update first, then insert
            val updated =
                handle
                    .createUpdate(
                        """
                UPDATE stl_votes SET value = :value
                WHERE user_id = :userId AND target_id = :targetId AND target_type = :targetType
                """
                    )
                    .bind("userId", vote.userId)
                    .bind("targetId", vote.targetId)
                    .bind("targetType", vote.targetType.name)
                    .bind("value", vote.value)
                    .execute()

            if (updated == 0) {
                handle
                    .createUpdate(
                        """
                    INSERT INTO stl_votes (user_id, target_id, target_type, value, created_at)
                    VALUES (:userId, :targetId, :targetType, :value, :createdAt)
                    """
                    )
                    .bind("userId", vote.userId)
                    .bind("targetId", vote.targetId)
                    .bind("targetType", vote.targetType.name)
                    .bind("value", vote.value)
                    .bind("createdAt", OffsetDateTime.ofInstant(vote.createdAt, ZoneOffset.UTC))
                    .execute()
            }
        }
    }

    override fun delete(userId: UUID, targetId: UUID, targetType: VoteTarget) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "DELETE FROM stl_votes WHERE user_id = :userId AND target_id = :targetId AND target_type = :targetType"
                )
                .bind("userId", userId)
                .bind("targetId", targetId)
                .bind("targetType", targetType.name)
                .execute()
        }
    }

    override fun findUserVotes(userId: UUID, targetIds: List<UUID>, targetType: VoteTarget): Map<UUID, Int> {
        if (targetIds.isEmpty()) return emptyMap()
        return jdbi.withHandle<Map<UUID, Int>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT target_id, value FROM stl_votes
                WHERE user_id = :userId AND target_type = :targetType
                AND target_id IN (<targetIds>)
                """
                )
                .bind("userId", userId)
                .bind("targetType", targetType.name)
                .bindList("targetIds", targetIds)
                .map { rs, _ -> rs.getObject("target_id", UUID::class.java) to rs.getInt("value") }
                .list()
                .toMap()
        }
    }
}
