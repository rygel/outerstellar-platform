package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.MessageSummary
import dev.outerstellar.platform.model.OptimisticLockException
import dev.outerstellar.platform.model.StoredMessage
import dev.outerstellar.platform.sync.SyncMessage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import org.http4k.format.Jackson
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi

@Suppress("TooManyFunctions")
class JdbiMessageRepository(private val jdbi: Jdbi) : MessageRepository {
    override fun listMessages(
        query: String?,
        year: Int?,
        limit: Int,
        offset: Int,
        includeDeleted: Boolean,
    ): List<MessageSummary> {
        return jdbi.withHandle<List<MessageSummary>, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(handle, query, year, includeDeleted)
            val sql =
                """
                SELECT sync_id, author, content, updated_at_epoch_ms, dirty, deleted, version, sync_conflict
                FROM messages
                WHERE $whereClause
                ORDER BY updated_at_epoch_ms DESC, id DESC
                LIMIT :limit OFFSET :offset
                """
            val q = handle.createQuery(sql)
            bindings(q)
            q.bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapMessage(rs) }
                .list()
                .map(StoredMessage::toSummary)
        }
    }

    override fun countMessages(query: String?, year: Int?, includeDeleted: Boolean): Long {
        return jdbi.withHandle<Long, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(handle, query, year, includeDeleted)
            val q = handle.createQuery("SELECT COUNT(*) FROM messages WHERE $whereClause")
            bindings(q)
            q.mapTo(Long::class.java).one()
        }
    }

    override fun listDirtyMessages(): List<StoredMessage> =
        jdbi.withHandle<List<StoredMessage>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM messages WHERE dirty = true AND deleted_at IS NULL")
                .map { rs, _ -> mapMessage(rs) }
                .list()
        }

    override fun countDirtyMessages(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT COUNT(*) FROM messages WHERE dirty = true AND deleted_at IS NULL"
                )
                .mapTo(Long::class.java)
                .one()
        }

    override fun findBySyncId(syncId: String): StoredMessage? =
        jdbi.withHandle<StoredMessage?, Exception> { handle -> findBySyncId(handle, syncId) }

    private fun findBySyncId(handle: Handle, syncId: String): StoredMessage? =
        handle
            .createQuery("SELECT * FROM messages WHERE sync_id = :syncId")
            .bind("syncId", syncId)
            .map { rs, _ -> mapMessage(rs) }
            .findOne()
            .orElse(null)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
        jdbi.withHandle<List<StoredMessage>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT * FROM messages
                    WHERE updated_at_epoch_ms > :since AND deleted_at IS NULL
                    """
                )
                .bind("since", updatedAtEpochMs)
                .map { rs, _ -> mapMessage(rs) }
                .list()
        }

    override fun createServerMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = false)

    override fun createLocalMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = true)

    override fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage {
        jdbi.useHandle<Exception> { handle ->
            val existing = findBySyncId(handle, message.syncId)
            if (existing == null) {
                handle
                    .createUpdate(
                        """
                        INSERT INTO messages (sync_id, author, content, updated_at_epoch_ms, dirty, deleted, version)
                        VALUES (:syncId, :author, :content, :updatedAtEpochMs, :dirty, :deleted, 1)
                        """
                    )
                    .bind("syncId", message.syncId)
                    .bind("author", message.author)
                    .bind("content", message.content)
                    .bind("updatedAtEpochMs", message.updatedAtEpochMs)
                    .bind("dirty", dirty)
                    .bind("deleted", message.deleted)
                    .execute()
            } else {
                handle
                    .createUpdate(
                        """
                        UPDATE messages
                        SET author = :author, content = :content, updated_at_epoch_ms = :updatedAtEpochMs,
                            dirty = :dirty, deleted = :deleted, version = :version
                        WHERE sync_id = :syncId
                        """
                    )
                    .bind("syncId", message.syncId)
                    .bind("author", message.author)
                    .bind("content", message.content)
                    .bind("updatedAtEpochMs", message.updatedAtEpochMs)
                    .bind("dirty", dirty)
                    .bind("deleted", message.deleted)
                    .bind("version", existing.version + 1)
                    .execute()
            }
        }
        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        jdbi.useHandle<Exception> { handle ->
            val batch =
                handle.prepareBatch("UPDATE messages SET dirty = false WHERE sync_id = :syncId")
            syncIds.forEach { batch.bind("syncId", it).add() }
            batch.execute()
        }
    }

    override fun getLastSyncEpochMs(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT state_value FROM sync_state WHERE state_key = :key")
                .bind("key", LAST_SYNC_STATE_KEY)
                .mapTo(Long::class.java)
                .findOne()
                .orElse(0L)
        }

    override fun setLastSyncEpochMs(value: Long) {
        jdbi.useHandle<Exception> { handle ->
            val updated =
                handle
                    .createUpdate(
                        "UPDATE sync_state SET state_value = :value WHERE state_key = :key"
                    )
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            if (updated == 0) {
                handle
                    .createUpdate(
                        "INSERT INTO sync_state (state_key, state_value) VALUES (:key, :value)"
                    )
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            }
        }
    }

    override fun seedMessages() {
        val count =
            jdbi.withHandle<Long, Exception> { handle ->
                handle
                    .createQuery(
                        "SELECT COUNT(*) FROM messages WHERE deleted = false AND deleted_at IS NULL"
                    )
                    .mapTo(Long::class.java)
                    .one()
            }
        if (count > 0) return

        createServerMessage("Outerstellar", "Flyway + JDBI are already wired up for this platform.")
        createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
    }

    override fun softDelete(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE messages SET deleted_at = CURRENT_TIMESTAMP(), version = version + 1
                    WHERE sync_id = :syncId
                    """
                )
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun restore(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE messages SET deleted_at = NULL, version = version + 1
                    WHERE sync_id = :syncId
                    """
                )
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun updateMessage(message: StoredMessage): StoredMessage {
        val rows =
            jdbi.withHandle<Int, Exception> { handle ->
                handle
                    .createUpdate(
                        """
                        UPDATE messages
                        SET author = :author, content = :content,
                            updated_at_epoch_ms = :updatedAtEpochMs, dirty = :dirty,
                            deleted = :deleted, version = :newVersion
                        WHERE sync_id = :syncId AND version = :version
                        """
                    )
                    .bind("author", message.author)
                    .bind("content", message.content)
                    .bind("updatedAtEpochMs", System.currentTimeMillis())
                    .bind("dirty", message.dirty)
                    .bind("deleted", message.deleted)
                    .bind("newVersion", message.version + 1)
                    .bind("syncId", message.syncId)
                    .bind("version", message.version)
                    .execute()
            }
        if (rows == 0) throw OptimisticLockException("Message", message.syncId)
        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncMessage) {
        val json = Jackson.asFormatString(serverVersion)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE messages SET sync_conflict = :json WHERE sync_id = :syncId")
                .bind("json", json)
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun resolveConflict(syncId: String, resolvedMessage: StoredMessage) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE messages
                    SET author = :author, content = :content,
                        updated_at_epoch_ms = :updatedAtEpochMs, dirty = :dirty,
                        version = version + 1, sync_conflict = NULL
                    WHERE sync_id = :syncId
                    """
                )
                .bind("author", resolvedMessage.author)
                .bind("content", resolvedMessage.content)
                .bind("updatedAtEpochMs", resolvedMessage.updatedAtEpochMs)
                .bind("dirty", resolvedMessage.dirty)
                .bind("syncId", syncId)
                .execute()
        }
    }

    private fun insertMessage(author: String, content: String, dirty: Boolean): StoredMessage {
        val syncId = UUID.randomUUID().toString()
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO messages (sync_id, author, content, updated_at_epoch_ms, dirty, deleted, version)
                    VALUES (:syncId, :author, :content, :updatedAtEpochMs, :dirty, false, 1)
                    """
                )
                .bind("syncId", syncId)
                .bind("author", author)
                .bind("content", content)
                .bind("updatedAtEpochMs", System.currentTimeMillis())
                .bind("dirty", dirty)
                .execute()
        }
        return requireNotNull(findBySyncId(syncId))
    }

    private data class FilterClause(
        val sql: String,
        val binder: (org.jdbi.v3.core.statement.Query) -> Unit,
    )

    private fun buildFilterClause(
        handle: Handle,
        query: String?,
        year: Int?,
        includeDeleted: Boolean,
    ): FilterClause {
        val conditions = mutableListOf<String>()
        val bindings = mutableMapOf<String, Any>()

        if (includeDeleted) {
            conditions.add("deleted_at IS NOT NULL")
        } else {
            conditions.add("deleted = false AND deleted_at IS NULL")
        }

        if (!query.isNullOrBlank()) {
            conditions.add(
                "(LOWER(content) LIKE :likeQuery ESCAPE '!' OR LOWER(author) LIKE :likeQuery ESCAPE '!')"
            )
            bindings["likeQuery"] = "%${query.lowercase().escapeLike()}%"
        }

        if (year != null) {
            val startOfYear =
                ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            val endOfYear =
                ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli() - 1
            conditions.add("updated_at_epoch_ms BETWEEN :yearStart AND :yearEnd")
            bindings["yearStart"] = startOfYear
            bindings["yearEnd"] = endOfYear
        }

        val whereClause = conditions.joinToString(" AND ")
        return FilterClause(whereClause) { q ->
            bindings.forEach { (key, value) -> q.bind(key, value) }
        }
    }

    private fun mapMessage(rs: java.sql.ResultSet): StoredMessage {
        return StoredMessage(
            syncId = rs.getString("sync_id") ?: "unknown",
            author = rs.getString("author") ?: "unknown",
            content = rs.getString("content") ?: "unknown",
            updatedAtEpochMs = rs.getLong("updated_at_epoch_ms"),
            dirty = rs.getBoolean("dirty"),
            deleted = rs.getBoolean("deleted"),
            version = rs.getLong("version"),
            syncConflict = rs.getString("sync_conflict"),
        )
    }

    companion object {
        private const val LAST_SYNC_STATE_KEY = "last_sync_epoch_ms"
    }
}

private fun String.escapeLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")
