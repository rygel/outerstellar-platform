package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqVoteRepository(private val dsl: DSLContext) : VoteRepository {

    private val table = DSL.table("plt_message_votes")
    private val idField = DSL.field(DSL.name("id"), SQLDataType.BIGINT)
    private val messageSyncIdField = DSL.field(DSL.name("message_sync_id"), SQLDataType.VARCHAR)
    private val userIdField = DSL.field(DSL.name("user_id"), SQLDataType.UUID)
    private val directionField = DSL.field(DSL.name("direction"), SQLDataType.INTEGER)
    private val createdAtField = DSL.field(DSL.name("created_at"), SQLDataType.TIMESTAMP)

    private fun mapRecord(record: Record): MessageVote =
        MessageVote(
            id = record.get(idField)!!,
            messageSyncId = record.get(messageSyncIdField)!!,
            userId = record.get(userIdField)!!,
            direction = record.get(directionField)!!,
            createdAt = record.get(createdAtField)?.toInstant() ?: Instant.now(),
        )

    override fun save(vote: MessageVote) {
        dsl.insertInto(table)
            .set(messageSyncIdField, vote.messageSyncId)
            .set(userIdField, vote.userId)
            .set(directionField, vote.direction)
            .set(createdAtField, Timestamp.from(vote.createdAt))
            .execute()
    }

    override fun updateDirection(userId: UUID, messageSyncId: String, direction: Int) {
        dsl.update(table)
            .set(directionField, direction)
            .where(userIdField.eq(userId).and(messageSyncIdField.eq(messageSyncId)))
            .execute()
    }

    override fun delete(userId: UUID, messageSyncId: String) {
        dsl.deleteFrom(table).where(userIdField.eq(userId).and(messageSyncIdField.eq(messageSyncId))).execute()
    }

    override fun findByUserAndMessage(userId: UUID, messageSyncId: String): MessageVote? =
        dsl.select()
            .from(table)
            .where(userIdField.eq(userId).and(messageSyncIdField.eq(messageSyncId)))
            .fetchOne()
            ?.let { mapRecord(it) }

    override fun findScoresByMessages(messageSyncIds: List<String>, userId: UUID?): Map<String, VoteScore> {
        if (messageSyncIds.isEmpty()) return emptyMap()
        val upvote = DSL.field("upvotes", SQLDataType.INTEGER)
        val downvote = DSL.field("downvotes", SQLDataType.INTEGER)
        val syncId = messageSyncIdField
        val upvoteExpr =
            DSL.field("sum(case when direction = 1 then 1 else 0 end)", SQLDataType.INTEGER).`as`("upvotes")
        val downvoteExpr =
            DSL.field("sum(case when direction = -1 then 1 else 0 end)", SQLDataType.INTEGER).`as`("downvotes")

        val results =
            dsl.select(syncId, upvoteExpr, downvoteExpr)
                .from(table)
                .where(messageSyncIdField.`in`(messageSyncIds))
                .groupBy(messageSyncIdField)
                .fetch()

        val scoreMap = mutableMapOf<String, VoteScore>()
        for (record in results) {
            val msgId = record.get(messageSyncIdField)!!
            val ups = record.get(upvote) ?: 0
            val downs = record.get(downvote) ?: 0
            scoreMap[msgId] = VoteScore(msgId, ups, downs, ups - downs, null)
        }

        if (userId != null) {
            val userVotes =
                dsl.select(messageSyncIdField, directionField)
                    .from(table)
                    .where(userIdField.eq(userId).and(messageSyncIdField.`in`(messageSyncIds)))
                    .fetch()
            for (record in userVotes) {
                val msgId = record.get(messageSyncIdField)!!
                val dir = record.get(directionField)!!
                val existing = scoreMap[msgId]
                if (existing != null) {
                    scoreMap[msgId] = existing.copy(userVote = dir)
                }
            }
        }

        for (msgId in messageSyncIds) {
            if (!scoreMap.containsKey(msgId)) {
                scoreMap[msgId] = VoteScore(msgId, 0, 0, 0, null)
            }
        }

        return scoreMap
    }

    override fun findScoreByMessage(messageSyncId: String, userId: UUID?): VoteScore {
        val scores = findScoresByMessages(listOf(messageSyncId), userId)
        return scores[messageSyncId] ?: VoteScore(messageSyncId, 0, 0, 0, null)
    }
}
