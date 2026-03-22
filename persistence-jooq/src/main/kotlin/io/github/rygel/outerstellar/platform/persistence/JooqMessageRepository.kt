package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_MESSAGES
import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_SYNC_STATE
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.OptimisticLockException
import io.github.rygel.outerstellar.platform.model.StoredMessage
import io.github.rygel.outerstellar.platform.sync.SyncMessage
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.http4k.format.Jackson
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record

@Suppress("TooManyFunctions")
class JooqMessageRepository(private val dsl: DSLContext) : MessageRepository {
    private fun notSoftDeleted(): Condition = PLT_MESSAGES.DELETED.eq(false).and(PLT_MESSAGES.DELETED_AT.isNull)

    private fun softDeleted(): Condition = PLT_MESSAGES.DELETED_AT.isNotNull

    private fun getFilterConditions(query: String?, year: Int?, includeDeleted: Boolean = false): Condition {
        var condition = if (includeDeleted) softDeleted() else notSoftDeleted()

        if (!query.isNullOrBlank()) {
            condition =
                condition.and(
                    PLT_MESSAGES.CONTENT.containsIgnoreCase(query).or(PLT_MESSAGES.AUTHOR.containsIgnoreCase(query))
                )
        }

        if (year != null) {
            val startOfYear =
                java.time.ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
            val endOfYear =
                java.time.ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli() - 1
            condition = condition.and(PLT_MESSAGES.UPDATED_AT_EPOCH_MS.between(startOfYear, endOfYear))
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
                    PLT_MESSAGES.ID,
                    PLT_MESSAGES.SYNC_ID,
                    PLT_MESSAGES.AUTHOR,
                    PLT_MESSAGES.CONTENT,
                    PLT_MESSAGES.UPDATED_AT_EPOCH_MS,
                    PLT_MESSAGES.DIRTY,
                    PLT_MESSAGES.DELETED,
                    PLT_MESSAGES.VERSION,
                    PLT_MESSAGES.SYNC_CONFLICT,
                )
                .from(MESSAGES)
                .where(getFilterConditions(query, year, includeDeleted))
                .orderBy(PLT_MESSAGES.UPDATED_AT_EPOCH_MS.desc(), PLT_MESSAGES.ID.desc())
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
        dsl.select(PLT_MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(PLT_MESSAGES.DIRTY.eq(true).and(PLT_MESSAGES.DELETED_AT.isNull))
            .fetch(::toStoredMessage)

    override fun countDirtyMessages(): Long =
        dsl.selectCount()
            .from(MESSAGES)
            .where(PLT_MESSAGES.DIRTY.eq(true).and(PLT_MESSAGES.DELETED_AT.isNull))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun findBySyncId(syncId: String): StoredMessage? =
        dsl.select(PLT_MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(PLT_MESSAGES.SYNC_ID.eq(syncId))
            .fetchOne(::toStoredMessage)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
        dsl.select(PLT_MESSAGES.fields().toList())
            .from(MESSAGES)
            .where(PLT_MESSAGES.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs).and(PLT_MESSAGES.DELETED_AT.isNull))
            .fetch(::toStoredMessage)

    override fun createServerMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = false)

    override fun createLocalMessage(author: String, content: String): StoredMessage =
        insertMessage(author = author, content = content, dirty = true)

    override fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage {
        val existing = findBySyncId(message.syncId)
        if (existing == null) {
            dsl.insertInto(MESSAGES)
                .set(PLT_MESSAGES.SYNC_ID, message.syncId)
                .set(PLT_MESSAGES.AUTHOR, message.author)
                .set(PLT_MESSAGES.CONTENT, message.content)
                .set(PLT_MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
                .set(PLT_MESSAGES.DIRTY, dirty)
                .set(PLT_MESSAGES.DELETED, message.deleted)
                .set(PLT_MESSAGES.VERSION, 1L)
                .execute()
        } else {
            dsl.update(MESSAGES)
                .set(PLT_MESSAGES.AUTHOR, message.author)
                .set(PLT_MESSAGES.CONTENT, message.content)
                .set(PLT_MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
                .set(PLT_MESSAGES.DIRTY, dirty)
                .set(PLT_MESSAGES.DELETED, message.deleted)
                .set(PLT_MESSAGES.VERSION, existing.version + 1)
                .where(PLT_MESSAGES.SYNC_ID.eq(message.syncId))
                .execute()
        }

        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) {
            return
        }

        dsl.update(MESSAGES).set(PLT_MESSAGES.DIRTY, false).where(PLT_MESSAGES.SYNC_ID.`in`(syncIds)).execute()
    }

    override fun getLastSyncEpochMs(): Long =
        dsl.select(PLT_SYNC_STATE.STATE_VALUE)
            .from(SYNC_STATE)
            .where(PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
            .fetchOne(PLT_SYNC_STATE.STATE_VALUE) ?: 0L

    override fun setLastSyncEpochMs(value: Long) {
        val hasState = dsl.fetchCount(SYNC_STATE, PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
        if (hasState) {
            dsl.update(SYNC_STATE)
                .set(PLT_SYNC_STATE.STATE_VALUE, value)
                .where(PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
                .execute()
        } else {
            dsl.insertInto(SYNC_STATE)
                .set(PLT_SYNC_STATE.STATE_KEY, lastSyncStateKey)
                .set(PLT_SYNC_STATE.STATE_VALUE, value)
                .execute()
        }
    }

    override fun seedMessages() {
        if (dsl.fetchCount(MESSAGES, notSoftDeleted()) > 0) {
            return
        }

        createServerMessage("Outerstellar", "Flyway + jOOQ are already wired up for this platform.")
        createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
    }

    override fun softDelete(syncId: String) {
        dsl.update(MESSAGES)
            .set(PLT_MESSAGES.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
            .set(PLT_MESSAGES.VERSION, PLT_MESSAGES.VERSION.plus(1))
            .where(PLT_MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun restore(syncId: String) {
        dsl.update(MESSAGES)
            .setNull(PLT_MESSAGES.DELETED_AT)
            .set(PLT_MESSAGES.VERSION, PLT_MESSAGES.VERSION.plus(1))
            .where(PLT_MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun updateMessage(message: StoredMessage): StoredMessage {
        val rows =
            dsl.update(MESSAGES)
                .set(PLT_MESSAGES.AUTHOR, message.author)
                .set(PLT_MESSAGES.CONTENT, message.content)
                .set(PLT_MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                .set(PLT_MESSAGES.DIRTY, message.dirty)
                .set(PLT_MESSAGES.DELETED, message.deleted)
                .set(PLT_MESSAGES.VERSION, message.version + 1)
                .where(PLT_MESSAGES.SYNC_ID.eq(message.syncId))
                .and(PLT_MESSAGES.VERSION.eq(message.version))
                .execute()

        if (rows == 0) throw OptimisticLockException("Message", message.syncId)

        return requireNotNull(findBySyncId(message.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncMessage) {
        val json = Jackson.asFormatString(serverVersion)
        dsl.update(MESSAGES).set(PLT_MESSAGES.SYNC_CONFLICT, json).where(PLT_MESSAGES.SYNC_ID.eq(syncId)).execute()
    }

    override fun resolveConflict(syncId: String, resolvedMessage: StoredMessage) {
        dsl.update(MESSAGES)
            .set(PLT_MESSAGES.AUTHOR, resolvedMessage.author)
            .set(PLT_MESSAGES.CONTENT, resolvedMessage.content)
            .set(PLT_MESSAGES.UPDATED_AT_EPOCH_MS, resolvedMessage.updatedAtEpochMs)
            .set(PLT_MESSAGES.DIRTY, resolvedMessage.dirty)
            .set(PLT_MESSAGES.VERSION, PLT_MESSAGES.VERSION.plus(1))
            .setNull(PLT_MESSAGES.SYNC_CONFLICT)
            .where(PLT_MESSAGES.SYNC_ID.eq(syncId))
            .execute()
    }

    private fun insertMessage(author: String, content: String, dirty: Boolean): StoredMessage {
        val syncId = UUID.randomUUID().toString()
        dsl.insertInto(MESSAGES)
            .set(PLT_MESSAGES.SYNC_ID, syncId)
            .set(PLT_MESSAGES.AUTHOR, author)
            .set(PLT_MESSAGES.CONTENT, content)
            .set(PLT_MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .set(PLT_MESSAGES.DIRTY, dirty)
            .set(PLT_MESSAGES.DELETED, false)
            .set(PLT_MESSAGES.VERSION, 1L)
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
            syncId = record.get(PLT_MESSAGES.SYNC_ID) ?: "unknown",
            author = record.get(PLT_MESSAGES.AUTHOR) ?: "unknown",
            content = record.get(PLT_MESSAGES.CONTENT) ?: "unknown",
            updatedAtEpochMs = record.get(PLT_MESSAGES.UPDATED_AT_EPOCH_MS) ?: 0L,
            dirty = record.get(PLT_MESSAGES.DIRTY) ?: false,
            deleted = record.get(PLT_MESSAGES.DELETED) ?: false,
            version = record.get(PLT_MESSAGES.VERSION) ?: 1L,
            syncConflict = record.get(PLT_MESSAGES.SYNC_CONFLICT),
        )
    }

    companion object {
        private const val lastSyncStateKey = "last_sync_epoch_ms"
    }
}
