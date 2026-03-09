package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.MESSAGES
import dev.outerstellar.starter.jooq.tables.references.SYNC_STATE
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.sync.SyncMessage
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

@Suppress("TooManyFunctions")
class JooqMessageRepository(
  private val primaryDsl: DSLContext,
  private val replicaDsl: DSLContext = primaryDsl
) : MessageRepository {
  override fun listMessages(query: String?, year: Int?, limit: Int, offset: Int): List<MessageSummary> {
    val results = replicaDsl
      .select(
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
      )
      .from(MESSAGES)
      .where(MESSAGES.DELETED.eq(false))
      .and(MESSAGES.field("deleted_at")!!.isNull)
      .let {
        if (!query.isNullOrBlank()) {
          // Use H2 Full-Text Search if possible, falling back to LIKE
          // FT_SEARCH_DATA returns a result set with SCHEMA, TABLE, COLUMNS, KEYS
          // We can join with it.
          try {
            val ftsTable = DSL.table("FT_SEARCH_DATA({0}, 0, 0)", DSL.`val`(query))
            val keyField = DSL.field("KEYS[1]", String::class.java)
            it.and(
              MESSAGES.SYNC_ID.`in`(
                replicaDsl.select(keyField).from(ftsTable)
              )
            )
          } catch (e: Exception) {
            // Fallback to standard LIKE if FT_SEARCH is not available or fails
            it.and(MESSAGES.CONTENT.containsIgnoreCase(query).or(MESSAGES.AUTHOR.containsIgnoreCase(query)))
          }
        } else {
          it
        }
      }
      .let {
        if (year != null) {
          // In H2/SQLite/standard SQL we can use strftime or just divide the epoch.
          // Since it's epoch ms, we can calculate the range for the year.
          val startOfYear = java.time.ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
          val endOfYear = java.time.ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli() - 1
          it.and(MESSAGES.UPDATED_AT_EPOCH_MS.between(startOfYear, endOfYear))
        } else {
          it
        }
      }
      .orderBy(MESSAGES.UPDATED_AT_EPOCH_MS.desc(), MESSAGES.ID.desc())
      .limit(limit)
      .offset(offset)
      .fetch(::toStoredMessage)
    
    return results.map(StoredMessage::toSummary)
  }

  override fun listDirtyMessages(): List<StoredMessage> =
    primaryDsl
      .select(
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
      )
      .from(MESSAGES)
      .where(MESSAGES.DIRTY.eq(true))
      .and(MESSAGES.field("deleted_at")!!.isNull)
      .fetch(::toStoredMessage)

  override fun findBySyncId(syncId: String): StoredMessage? =
    replicaDsl
      .select(
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
      )
      .from(MESSAGES)
      .where(MESSAGES.SYNC_ID.eq(syncId))
      .and(MESSAGES.field("deleted_at")!!.isNull)
      .fetchOne(::toStoredMessage)

  override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
    replicaDsl
      .select(
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
      )
      .from(MESSAGES)
      .where(MESSAGES.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs))
      .and(MESSAGES.field("deleted_at")!!.isNull)
      .fetch(::toStoredMessage)

  override fun createServerMessage(author: String, content: String): StoredMessage =
    insertMessage(author = author, content = content, dirty = false)

  override fun createLocalMessage(author: String, content: String): StoredMessage =
    insertMessage(author = author, content = content, dirty = true)

  override fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage {
    val existing = findBySyncId(message.syncId)
    if (existing == null) {
      primaryDsl
        .insertInto(MESSAGES)
        .set(MESSAGES.SYNC_ID, message.syncId)
        .set(MESSAGES.AUTHOR, message.author)
        .set(MESSAGES.CONTENT, message.content)
        .set(MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
        .set(MESSAGES.DIRTY, dirty)
        .set(MESSAGES.DELETED, message.deleted)
        .execute()
    } else {
      primaryDsl
        .update(MESSAGES)
        .set(MESSAGES.AUTHOR, message.author)
        .set(MESSAGES.CONTENT, message.content)
        .set(MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
        .set(MESSAGES.DIRTY, dirty)
        .set(MESSAGES.DELETED, message.deleted)
        .where(MESSAGES.SYNC_ID.eq(message.syncId))
        .execute()
    }

    return requireNotNull(findBySyncId(message.syncId))
  }

  override fun markClean(syncIds: Collection<String>) {
    if (syncIds.isEmpty()) {
      return
    }

    primaryDsl.update(MESSAGES).set(MESSAGES.DIRTY, false).where(MESSAGES.SYNC_ID.`in`(syncIds)).execute()
  }

  override fun getLastSyncEpochMs(): Long =
    replicaDsl
      .select(SYNC_STATE.STATE_VALUE)
      .from(SYNC_STATE)
      .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
      .fetchOne(SYNC_STATE.STATE_VALUE) ?: 0L

  override fun setLastSyncEpochMs(value: Long) {
    val hasState = primaryDsl.fetchCount(SYNC_STATE, SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
    if (hasState) {
      primaryDsl
        .update(SYNC_STATE)
        .set(SYNC_STATE.STATE_VALUE, value)
        .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
        .execute()
    } else {
      primaryDsl
        .insertInto(SYNC_STATE)
        .set(SYNC_STATE.STATE_KEY, lastSyncStateKey)
        .set(SYNC_STATE.STATE_VALUE, value)
        .execute()
    }
  }

  override fun seedStarterMessages() {
    if (replicaDsl.fetchCount(MESSAGES, MESSAGES.DELETED.eq(false).and(MESSAGES.field("deleted_at")!!.isNull)) > 0) {
      return
    }

    createServerMessage("Outerstellar", "Flyway + jOOQ are already wired up for this starter.")
    createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
  }

  override fun softDelete(syncId: String) {
    primaryDsl.update(MESSAGES)
      .set(MESSAGES.field("deleted_at", java.time.LocalDateTime::class.java), java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toLocalDateTime())
      .where(MESSAGES.SYNC_ID.eq(syncId))
      .execute()
  }

  private fun insertMessage(author: String, content: String, dirty: Boolean): StoredMessage {
    val syncId = UUID.randomUUID().toString()
    primaryDsl
      .insertInto(MESSAGES)
      .set(MESSAGES.SYNC_ID, syncId)
      .set(MESSAGES.AUTHOR, author)
      .set(MESSAGES.CONTENT, content)
      .set(MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
      .set(MESSAGES.DIRTY, dirty)
      .set(MESSAGES.DELETED, false)
      .execute()
    return requireNotNull(findBySyncId(syncId))
  }

  private fun toStoredMessage(record: Record): StoredMessage =
    StoredMessage(
      syncId = record.get(MESSAGES.SYNC_ID)!!,
      author = record.get(MESSAGES.AUTHOR)!!,
      content = record.get(MESSAGES.CONTENT)!!,
      updatedAtEpochMs = record.get(MESSAGES.UPDATED_AT_EPOCH_MS)!!,
      dirty = record.get(MESSAGES.DIRTY)!!,
      deleted = record.get(MESSAGES.DELETED)!!,
    )

  companion object {
    private const val lastSyncStateKey = "last_sync_epoch_ms"
  }
}
