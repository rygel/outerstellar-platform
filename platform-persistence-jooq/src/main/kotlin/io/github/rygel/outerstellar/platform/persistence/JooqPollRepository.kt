package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollOption
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

class JooqPollRepository(private val dsl: DSLContext) : PollRepository {

    private val pollsTable = DSL.table("plt_polls")
    private val pollIdField = DSL.field(DSL.name("id"), SQLDataType.BIGINT)
    private val syncIdField = DSL.field(DSL.name("sync_id"), SQLDataType.VARCHAR)
    private val creatorIdField = DSL.field(DSL.name("creator_id"), SQLDataType.UUID)
    private val questionField = DSL.field(DSL.name("question"), SQLDataType.VARCHAR)
    private val multiChoiceField = DSL.field(DSL.name("multi_choice"), SQLDataType.BOOLEAN)
    private val closedAtField = DSL.field(DSL.name("closed_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
    private val deadlineField = DSL.field(DSL.name("deadline"), SQLDataType.TIMESTAMPWITHTIMEZONE)
    private val pollCreatedAtField = DSL.field(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)
    private val pollUpdatedAtField = DSL.field(DSL.name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE)

    private val optionsTable = DSL.table("plt_poll_options")
    private val optionIdField = DSL.field(DSL.name("id"), SQLDataType.BIGINT)
    private val optionPollIdField = DSL.field(DSL.name("poll_id"), SQLDataType.BIGINT)
    private val positionField = DSL.field(DSL.name("position"), SQLDataType.INTEGER)
    private val optionTextField = DSL.field(DSL.name("option_text"), SQLDataType.VARCHAR)

    private val votesTable = DSL.table("plt_poll_votes")
    private val votePollIdField = DSL.field(DSL.name("poll_id"), SQLDataType.BIGINT)
    private val voteOptionIdField = DSL.field(DSL.name("option_id"), SQLDataType.BIGINT)
    private val voteUserIdField = DSL.field(DSL.name("user_id"), SQLDataType.UUID)

    private fun mapPollRecord(record: Record): Poll =
        Poll(
            id = record.get(pollIdField)!!,
            syncId = record.get(syncIdField)!!,
            creatorId = record.get(creatorIdField)!!,
            question = record.get(questionField)!!,
            multiChoice = record.get(multiChoiceField) ?: false,
            closedAt = record.get(closedAtField, OffsetDateTime::class.java)?.toInstant(),
            deadline = record.get(deadlineField, OffsetDateTime::class.java)?.toInstant(),
            createdAt = record.get(pollCreatedAtField, OffsetDateTime::class.java)?.toInstant() ?: Instant.now(),
            updatedAt = record.get(pollUpdatedAtField, OffsetDateTime::class.java)?.toInstant() ?: Instant.now(),
        )

    private fun mapOptionRecord(record: Record): PollOption =
        PollOption(
            id = record.get(optionIdField)!!,
            pollId = record.get(optionPollIdField)!!,
            position = record.get(positionField)!!,
            optionText = record.get(optionTextField)!!,
        )

    override fun create(poll: Poll, options: List<String>): Poll {
        val generatedId =
            dsl.insertInto(pollsTable)
                .set(syncIdField, poll.syncId)
                .set(creatorIdField, poll.creatorId)
                .set(questionField, poll.question)
                .set(multiChoiceField, poll.multiChoice)
                .set(deadlineField, poll.deadline?.let { it.atOffset(ZoneOffset.UTC) })
                .set(pollCreatedAtField, poll.createdAt.atOffset(ZoneOffset.UTC))
                .set(pollUpdatedAtField, poll.updatedAt.atOffset(ZoneOffset.UTC))
                .returning(pollIdField)
                .fetchOne()!!

        val pollId = generatedId.get(pollIdField)!!

        for ((index, optionText) in options.withIndex()) {
            dsl.insertInto(optionsTable)
                .set(optionPollIdField, pollId)
                .set(positionField, index)
                .set(optionTextField, optionText)
                .execute()
        }

        return poll.copy(id = pollId)
    }

    override fun findById(syncId: String): Poll? =
        dsl.select().from(pollsTable).where(syncIdField.eq(syncId)).fetchOne()?.let { mapPollRecord(it) }

    override fun findOptions(pollId: Long): List<PollOption> =
        dsl.select().from(optionsTable).where(optionPollIdField.eq(pollId)).orderBy(positionField.asc()).fetch().map {
            mapOptionRecord(it)
        }

    override fun findOptionById(optionId: Long): PollOption? =
        dsl.select().from(optionsTable).where(optionIdField.eq(optionId)).fetchOne()?.let { mapOptionRecord(it) }

    override fun castVote(pollId: Long, optionId: Long, userId: UUID) {
        dsl.insertInto(votesTable)
            .set(votePollIdField, pollId)
            .set(voteOptionIdField, optionId)
            .set(voteUserIdField, userId)
            .execute()
    }

    override fun removeVote(pollId: Long, optionId: Long, userId: UUID) {
        dsl.deleteFrom(votesTable)
            .where(votePollIdField.eq(pollId).and(voteOptionIdField.eq(optionId)).and(voteUserIdField.eq(userId)))
            .execute()
    }

    override fun getUserVotes(pollId: Long, userId: UUID): Set<Long> =
        dsl.select(voteOptionIdField)
            .from(votesTable)
            .where(votePollIdField.eq(pollId).and(voteUserIdField.eq(userId)))
            .fetch()
            .map { it.get(voteOptionIdField)!! }
            .toSet()

    override fun getVoteCounts(pollId: Long): Map<Long, Int> {
        val cntField = DSL.field("cnt", SQLDataType.INTEGER)
        return dsl.select(voteOptionIdField, DSL.count().`as`("cnt"))
            .from(votesTable)
            .where(votePollIdField.eq(pollId))
            .groupBy(voteOptionIdField)
            .fetch()
            .associate { it.get(voteOptionIdField)!! to (it.get(cntField) ?: 0) }
    }

    override fun closePoll(syncId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.update(pollsTable)
            .set(closedAtField, now)
            .set(pollUpdatedAtField, now)
            .where(syncIdField.eq(syncId))
            .execute()
    }

    override fun listOpen(limit: Int, offset: Int): List<Poll> =
        dsl.select()
            .from(pollsTable)
            .where(closedAtField.isNull)
            .orderBy(pollCreatedAtField.desc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map { mapPollRecord(it) }

    override fun listByCreator(userId: UUID, limit: Int, offset: Int): List<Poll> =
        dsl.select()
            .from(pollsTable)
            .where(creatorIdField.eq(userId))
            .orderBy(pollCreatedAtField.desc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map { mapPollRecord(it) }

    override fun delete(syncId: String) {
        dsl.deleteFrom(pollsTable).where(syncIdField.eq(syncId)).execute()
    }
}
