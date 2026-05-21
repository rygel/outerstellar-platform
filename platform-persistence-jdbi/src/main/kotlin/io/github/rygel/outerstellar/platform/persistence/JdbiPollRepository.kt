package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollOption
import java.sql.Timestamp
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiPollRepository(private val jdbi: Jdbi) : PollRepository {

    override fun create(poll: Poll, options: List<String>): Poll {
        return jdbi.withHandle<Poll, Exception> { handle ->
            val pollId =
                handle
                    .createUpdate(
                        """
                    INSERT INTO plt_polls (sync_id, creator_id, question, multi_choice, closed_at, deadline, created_at, updated_at)
                    VALUES (:syncId, :creatorId, :question, :multiChoice, :closedAt, :deadline, :createdAt, :updatedAt)
                    """
                    )
                    .bind("syncId", poll.syncId)
                    .bind("creatorId", poll.creatorId)
                    .bind("question", poll.question)
                    .bind("multiChoice", poll.multiChoice)
                    .bind("closedAt", poll.closedAt?.let { Timestamp.from(it) })
                    .bind("deadline", poll.deadline?.let { Timestamp.from(it) })
                    .bind("createdAt", Timestamp.from(poll.createdAt))
                    .bind("updatedAt", Timestamp.from(poll.updatedAt))
                    .executeAndReturnGeneratedKeys("id")
                    .map { rs, _ -> rs.getLong("id") }
                    .one()

            options.forEachIndexed { index, optionText ->
                handle
                    .createUpdate(
                        """
                        INSERT INTO plt_poll_options (poll_id, position, option_text)
                        VALUES (:pollId, :position, :optionText)
                        """
                    )
                    .bind("pollId", pollId)
                    .bind("position", index)
                    .bind("optionText", optionText)
                    .execute()
            }

            poll.copy(id = pollId)
        }
    }

    override fun findById(syncId: String): Poll? {
        return jdbi.withHandle<Poll?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, sync_id, creator_id, question, multi_choice, closed_at, deadline, created_at, updated_at FROM plt_polls WHERE sync_id = :syncId"
                )
                .bind("syncId", syncId)
                .map { rs, _ -> mapPoll(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun findOptions(pollId: Long): List<PollOption> {
        return jdbi.withHandle<List<PollOption>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, poll_id, position, option_text FROM plt_poll_options WHERE poll_id = :pollId ORDER BY position"
                )
                .bind("pollId", pollId)
                .map { rs, _ -> mapOption(rs) }
                .list()
        }
    }

    override fun findOptionById(optionId: Long): PollOption? {
        return jdbi.withHandle<PollOption?, Exception> { handle ->
            handle
                .createQuery("SELECT id, poll_id, position, option_text FROM plt_poll_options WHERE id = :optionId")
                .bind("optionId", optionId)
                .map { rs, _ -> mapOption(rs) }
                .findOne()
                .orElse(null)
        }
    }

    override fun castVote(pollId: Long, optionId: Long, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_poll_votes (poll_id, option_id, user_id, created_at)
                    VALUES (:pollId, :optionId, :userId, :createdAt)
                    """
                )
                .bind("pollId", pollId)
                .bind("optionId", optionId)
                .bind("userId", userId)
                .bind("createdAt", Timestamp.from(java.time.Instant.now()))
                .execute()
        }
    }

    override fun removeVote(pollId: Long, optionId: Long, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "DELETE FROM plt_poll_votes WHERE poll_id = :pollId AND option_id = :optionId AND user_id = :userId"
                )
                .bind("pollId", pollId)
                .bind("optionId", optionId)
                .bind("userId", userId)
                .execute()
        }
    }

    override fun getUserVotes(pollId: Long, userId: UUID): Set<Long> {
        return jdbi.withHandle<Set<Long>, Exception> { handle ->
            handle
                .createQuery("SELECT option_id FROM plt_poll_votes WHERE poll_id = :pollId AND user_id = :userId")
                .bind("pollId", pollId)
                .bind("userId", userId)
                .map { rs, _ -> rs.getLong("option_id") }
                .list()
                .toSet()
        }
    }

    override fun getVoteCounts(pollId: Long): Map<Long, Int> {
        return jdbi.withHandle<Map<Long, Int>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT option_id, COUNT(*) as cnt FROM plt_poll_votes WHERE poll_id = :pollId GROUP BY option_id"
                )
                .bind("pollId", pollId)
                .map { rs, _ -> rs.getLong("option_id") to rs.getInt("cnt") }
                .list()
                .toMap()
        }
    }

    override fun closePoll(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE plt_polls SET closed_at = :closedAt, updated_at = :updatedAt WHERE sync_id = :syncId"
                )
                .bind("closedAt", Timestamp.from(java.time.Instant.now()))
                .bind("updatedAt", Timestamp.from(java.time.Instant.now()))
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun listOpen(limit: Int, offset: Int): List<Poll> {
        return jdbi.withHandle<List<Poll>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, sync_id, creator_id, question, multi_choice, closed_at, deadline, created_at, updated_at FROM plt_polls WHERE closed_at IS NULL ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
                )
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPoll(rs) }
                .list()
        }
    }

    override fun listByCreator(userId: UUID, limit: Int, offset: Int): List<Poll> {
        return jdbi.withHandle<List<Poll>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT id, sync_id, creator_id, question, multi_choice, closed_at, deadline, created_at, updated_at FROM plt_polls WHERE creator_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPoll(rs) }
                .list()
        }
    }

    override fun delete(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM plt_polls WHERE sync_id = :syncId").bind("syncId", syncId).execute()
        }
    }

    private fun mapPoll(rs: java.sql.ResultSet): Poll {
        val closedAt = rs.getTimestamp("closed_at")
        val deadline = rs.getTimestamp("deadline")
        val createdAt = rs.getTimestamp("created_at")
        val updatedAt = rs.getTimestamp("updated_at")
        return Poll(
            id = rs.getLong("id"),
            syncId = rs.getString("sync_id"),
            creatorId = rs.getObject("creator_id", UUID::class.java),
            question = rs.getString("question"),
            multiChoice = rs.getBoolean("multi_choice"),
            closedAt = closedAt?.toInstant(),
            deadline = deadline?.toInstant(),
            createdAt = createdAt?.toInstant() ?: error("plt_polls.created_at is unexpectedly null"),
            updatedAt = updatedAt?.toInstant() ?: error("plt_polls.updated_at is unexpectedly null"),
        )
    }

    private fun mapOption(rs: java.sql.ResultSet): PollOption {
        return PollOption(
            id = rs.getLong("id"),
            pollId = rs.getLong("poll_id"),
            position = rs.getInt("position"),
            optionText = rs.getString("option_text"),
        )
    }
}
