package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.jooq.tables.references.MESSAGES
import dev.outerstellar.platform.jooq.tables.references.SYNC_STATE
import dev.outerstellar.platform.model.MessageSummary
import dev.outerstellar.platform.model.OptimisticLockException
import dev.outerstellar.platform.model.StoredMessage
import dev.outerstellar.platform.sync.SyncMessage
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.http4k.format.Jackson
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record

@Suppress("TooManyFunctions")
class JooqMessageRepository(private val dsl: DSLContext) : MessageRepository {
    private fun notSoftDeleted(): Condition =
        MESSAGES.DELETED.eq(false).and(MESSAGES.DELETED_AT.isNull)

    private fun softDeleted(): Condition = MESSAGES.DELETED_AT.isNotNull

    private fun getFilterConditions(
        query: String?,
        year: Int?,
        includeDeleted: Boolean = false,
    ): Condition {
        var condition = if (includeDeleted) softDeleted() else notSoftDeleted()

        if (!query.isNullOrBlank()) {
            condition =
                condition.and(
                    MESSAGES.CONTENT.containsIgnoreCase(query)
                        .or(MESSAGES.AUTHOR.containsIgnoreCase(query))
                )
        }

        if (year != null) {
            val startOfYear =
                java.time.ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            val endOfYear =
                java.time.ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli() - 1
            condition = condition.and(MESSAGES.UPDATED_AT_EPOCH_MS.between(startOfYear, endOfYear))
        }

        return condition
    }

    override fun listMessages(
        query: String?,
        year: Int?,
        limit: Int,
        offset: Int,
        includeDeleted: Boolean,
    ): List<MessageSummary> {
        val results =
            dsl.select(
                    MESSAGES.ID,
                    MESSAGES.SYNC_ID,
                    MESSAGES.AUTHOR,
                    MESSAGES.CONTENT,
                    MESSAGES.UPDATED_AT_EPOCH_MS,
                    MESSAGES.DIRTY,
                    MESSAGES.DELETED,
                    MESSAGES.VERSION,
                    MESSAGES.SYNC_CONFLICT,
                )
                .from(MESSAGES)
                .where(getFilterConditions(query, year, includeDeleted))
                .orderBy(MESSAGES.UPDATED_AT_EPOCH_MS.desc(), MESSAGES.ID.desc())
                .limit(limit)
                .offset(offset)
                .fetch(::toStoredMessage)

        return results.map(StoredMessage::toSummary)
    }

    override fun countMessages(query: String?, year: Int?, includeDeleted: Boolean): Long {
        return dsl.fetchCount(MESSAGES, getFilterConditions(query, year, includeDeleted)).toLong()
    }

    fun countDeletedMessages(query: String?, year: Int?): Long {
        return countMessages(query, year, true)
    }

    override fun listDirtyMessages(): List<StoredMessage> =
        dsl.select(MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(MESSAGES.DIRTY.eq(true).and(MESSAGES.DELETED_AT.isNull))
            .fetch(::toStoredMessage)

    override fun findBySyncId(syncId: String): StoredMessage? =
        dsl.select(MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(MESSAGES.SYNC_ID.eq(syncId))
            .fetchOne(::toStoredMessage)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
        dsl.select(MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(
                MESSAGES.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs).and(MESSAGES.DELETED_AT.isNull)
            )
            .fetch(::toStoredMessage)

    override fun createServerMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = false)

    override fun createLocalMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = true)

    override fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage {
        val existing = findBySyncId(message.syncId)
        if (existing == null) {
            dsl.insertInto(MESSAGES)
                .set(MESSAGES.SYNC_ID, message.syncId)
                .set(MESSAGES.AUTHOR, message.author)
                .set(MESSAGES.CONTENT, message.content)
                .set(MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
                .set(MESSAGES.DIRTY, dirty)
                .set(MESSAGES.DELETED, message.deleted)
                .set(MESSAGES.VERSION, 1L)
                .execute()
        } else {
            dsl.update(MESSAGES)
                .set(MESSAGES.AUTHOR, message.author)
                .set(MESSAGES.CONTENT, message.content)
                .set(MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
                .set(MESSAGES.DIRTY, dirty)
                .set(MESSAGES.DELETED, message.deleted)
                .set(MESSAGES.VERSION, existing.version + 1)
                .where(MESSAGES.SYNC_ID.eq(message.syncId))
                .execute()
        }

        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) {
            return
        }

        dsl.update(MESSAGES)
            .set(MESSAGES.DIRTY, false)
            .where(MESSAGES.SYNC_ID.`in`(syncIds))
            .execute()
    }

    override fun getLastSyncEpochMs(): Long =
        dsl.select(SYNC_STATE.STATE_VALUE)
            .from(SYNC_STATE)
            .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
            .fetchOne(SYNC_STATE.STATE_VALUE) ?: 0L

    override fun setLastSyncEpochMs(value: Long) {
        val hasState = dsl.fetchCount(SYNC_STATE, SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
        if (hasState) {
            dsl.update(SYNC_STATE)
                .set(SYNC_STATE.STATE_VALUE, value)
                .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
                .execute()
        } else {
            dsl.insertInto(SYNC_STATE)
                .set(SYNC_STATE.STATE_KEY, lastSyncStateKey)
                .set(SYNC_STATE.STATE_VALUE, value)
                .execute()
        }
    }

    override fun seedStarterMessages() {
        if (dsl.fetchCount(MESSAGES, notSoftDeleted()) > 0) {
            return
        }

        createServerMessage("Outerstellar", "Flyway + jOOQ are already wired up for this starter.")
        createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
    }

    override fun softDelete(syncId: String) {
        dsl.update(MESSAGES)
            .set(MESSAGES.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .set(MESSAGES.VERSION, MESSAGES.VERSION.plus(1))
            .where(MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun restore(syncId: String) {
        dsl.update(MESSAGES)
            .setNull(MESSAGES.DELETED_AT)
            .set(MESSAGES.VERSION, MESSAGES.VERSION.plus(1))
            .where(MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun updateMessage(message: StoredMessage): StoredMessage {
        val rows =
            dsl.update(MESSAGES)
                .set(MESSAGES.AUTHOR, message.author)
                .set(MESSAGES.CONTENT, message.content)
                .set(MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                .set(MESSAGES.DIRTY, message.dirty)
                .set(MESSAGES.DELETED, message.deleted)
                .set(MESSAGES.VERSION, message.version + 1)
                .where(MESSAGES.SYNC_ID.eq(message.syncId))
                .and(MESSAGES.VERSION.eq(message.version))
                .execute()

        if (rows == 0) throw OptimisticLockException("Message", message.syncId)

        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncMessage) {
        val json = Jackson.asFormatString(serverVersion)
        dsl.update(MESSAGES)
            .set(MESSAGES.SYNC_CONFLICT, json)
            .where(MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun resolveConflict(syncId: String, resolvedMessage: StoredMessage) {
        dsl.update(MESSAGES)
            .set(MESSAGES.AUTHOR, resolvedMessage.author)
            .set(MESSAGES.CONTENT, resolvedMessage.content)
            .set(MESSAGES.UPDATED_AT_EPOCH_MS, resolvedMessage.updatedAtEpochMs)
            .set(MESSAGES.DIRTY, resolvedMessage.dirty)
            .set(MESSAGES.VERSION, MESSAGES.VERSION.plus(1))
            .setNull(MESSAGES.SYNC_CONFLICT)
            .where(MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    private fun insertMessage(author: String, content: String, dirty: Boolean): StoredMessage {
        val syncId = UUID.randomUUID().toString()
        dsl.insertInto(MESSAGES)
            .set(MESSAGES.SYNC_ID, syncId)
            .set(MESSAGES.AUTHOR, author)
            .set(MESSAGES.CONTENT, content)
            .set(MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .set(MESSAGES.DIRTY, dirty)
            .set(MESSAGES.DELETED, false)
            .set(MESSAGES.VERSION, 1L)
            .execute()
        return requireNotNull(findBySyncId(syncId))
    }

    private fun toStoredMessage(record: Record?): StoredMessage {
        if (record == null) {
            return StoredMessage(
                syncId = "unknown",
                author = "unknown",
                content = "unknown",
                updatedAtEpochMs = 0L,
                dirty = false,
                deleted = false,
                version = 1L,
                syncConflict = null,
            )
        }
        return StoredMessage(
            syncId = record.get(MESSAGES.SYNC_ID) ?: "unknown",
            author = record.get(MESSAGES.AUTHOR) ?: "unknown",
            content = record.get(MESSAGES.CONTENT) ?: "unknown",
            updatedAtEpochMs = record.get(MESSAGES.UPDATED_AT_EPOCH_MS) ?: 0L,
            dirty = record.get(MESSAGES.DIRTY) ?: false,
            deleted = record.get(MESSAGES.DELETED) ?: false,
            version = record.get(MESSAGES.VERSION) ?: 1L,
            syncConflict = record.get(MESSAGES.SYNC_CONFLICT),
        )
    }

    companion object {
        private const val lastSyncStateKey = "last_sync_epoch_ms"
    }
}
