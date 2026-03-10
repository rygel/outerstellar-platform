package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.MESSAGES
import dev.outerstellar.starter.jooq.tables.references.SYNC_STATE
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.sync.SyncMessage
import java.util.UUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

@Suppress("TooManyFunctions")
class JooqMessageRepository(
  private val primaryDsl: DSLContext,
  private val replicaDsl: DSLContext = primaryDsl
) : MessageRepository {

  private fun getFilterConditions(query: String?, year: Int?, includeDeleted: Boolean = false): Condition {
    var condition = DSL.noCondition()
      .let { 
         val deletedField = MESSAGES.field("DELETED", Boolean::class.java)
         if (deletedField != null) it.and(deletedField.eq(false)) else it 
      }
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT")
        if (deletedAtField != null && !includeDeleted) {
          it.and(deletedAtField.isNull)
        } else if (deletedAtField != null && includeDeleted) {
          it.and(deletedAtField.isNotNull)
        } else {
          it
        }
      }

    if (!query.isNullOrBlank()) {
      try {
        val ftsTable = DSL.table("FT_SEARCH_DATA({0}, 0, 0)", DSL.`val`(query))
        val keyField = DSL.field("KEYS[1]", String::class.java)
        condition = condition.and(
          MESSAGES.SYNC_ID.`in`(
            replicaDsl.select(keyField).from(ftsTable)
          )
        )
      } catch (e: Exception) {
        condition = condition.and(MESSAGES.CONTENT.containsIgnoreCase(query).or(MESSAGES.AUTHOR.containsIgnoreCase(query)))
      }
    }

    if (year != null) {
      val startOfYear = java.time.ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
      val endOfYear = java.time.ZonedDateTime.of(year + 1, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli() - 1
      val updatedAtField = MESSAGES.field("UPDATED_AT_EPOCH_MS", Long::class.java)
      if (updatedAtField != null) {
        condition = condition.and(updatedAtField.between(startOfYear, endOfYear))
      }
    }

    return condition
  }

  override fun listMessages(query: String?, year: Int?, limit: Int, offset: Int): List<MessageSummary> {
    return listMessagesInternal(query, year, limit, offset, includeDeleted = false)
  }

  fun listDeletedMessages(query: String?, year: Int?, limit: Int, offset: Int): List<MessageSummary> {
    return listMessagesInternal(query, year, limit, offset, includeDeleted = true)
  }

  private fun listMessagesInternal(query: String?, year: Int?, limit: Int, offset: Int, includeDeleted: Boolean): List<MessageSummary> {
    val results = replicaDsl
      .select(
        MESSAGES.ID,
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
        MESSAGES.VERSION
      )
      .from(MESSAGES)
      .where(getFilterConditions(query, year, includeDeleted))
      .orderBy(
        (MESSAGES.field("UPDATED_AT_EPOCH_MS") ?: MESSAGES.field("ID") ?: DSL.field("ID")).desc(),
        (MESSAGES.field("ID") ?: DSL.field("ID")).desc()
      )
      .limit(limit)
      .offset(offset)
      .fetch(::toStoredMessage)
    
    return results.map(StoredMessage::toSummary)
  }

  override fun countMessages(query: String?, year: Int?): Long {
    return replicaDsl.fetchCount(MESSAGES, getFilterConditions(query, year, false)).toLong()
  }

  fun countDeletedMessages(query: String?, year: Int?): Long {
    return replicaDsl.fetchCount(MESSAGES, getFilterConditions(query, year, true)).toLong()
  }

  override fun listDirtyMessages(): List<StoredMessage> =
    primaryDsl
      .select(
        DSL.field("SYNC_ID"),
        DSL.field("AUTHOR"),
        DSL.field("CONTENT"),
        DSL.field("UPDATED_AT_EPOCH_MS"),
        DSL.field("DIRTY"),
        DSL.field("DELETED"),
        DSL.field("VERSION")
      )
      .from(MESSAGES)
      .where(
        DSL.noCondition()
          .let {
            val dirtyField = MESSAGES.field("DIRTY", Boolean::class.java)
            if (dirtyField != null) it.and(dirtyField.eq(true)) else it
          }
      )
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT")
        if (deletedAtField != null) {
          it.and(deletedAtField.isNull)
        } else {
          it
        }
      }
      .fetch(::toStoredMessage)

  override fun findBySyncId(syncId: String): StoredMessage? =
    replicaDsl
      .select(
        DSL.field("SYNC_ID"),
        DSL.field("AUTHOR"),
        DSL.field("CONTENT"),
        DSL.field("UPDATED_AT_EPOCH_MS"),
        DSL.field("DIRTY"),
        DSL.field("DELETED"),
        DSL.field("VERSION")
      )
      .from(MESSAGES)
      .where(
        DSL.noCondition()
          .let {
            val syncIdField = MESSAGES.field("SYNC_ID", String::class.java)
            if (syncIdField != null) it.and(syncIdField.eq(syncId)) else it
          }
      )
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT")
        if (deletedAtField != null) {
          it.and(deletedAtField.isNull)
        } else {
          it
        }
      }
      .fetchOne(::toStoredMessage)

  override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
    replicaDsl
      .select(
        DSL.field("SYNC_ID"),
        DSL.field("AUTHOR"),
        DSL.field("CONTENT"),
        DSL.field("UPDATED_AT_EPOCH_MS"),
        DSL.field("DIRTY"),
        DSL.field("DELETED"),
        DSL.field("VERSION")
      )
      .from(MESSAGES)
      .where(
        DSL.noCondition()
          .let {
            val updatedAtField = MESSAGES.field("UPDATED_AT_EPOCH_MS", Long::class.java)
            if (updatedAtField != null) it.and(updatedAtField.gt(updatedAtEpochMs)) else it
          }
      )
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT")
        if (deletedAtField != null) {
          it.and(deletedAtField.isNull)
        } else {
          it
        }
      }
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
        .set(MESSAGES.VERSION, 1L)
        .execute()
    } else {
      primaryDsl
        .update(MESSAGES)
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
    if (replicaDsl.fetchCount(MESSAGES, DSL.noCondition()
        .let {
          val deletedField = MESSAGES.field("DELETED", Boolean::class.java)
          if (deletedField != null) it.and(deletedField.eq(false)) else it
        }
        .let {
          val deletedAtField = MESSAGES.field("DELETED_AT")
          if (deletedAtField != null) {
            it.and(deletedAtField.isNull)
          } else {
            it
          }
        }) > 0) {
      return
    }

    createServerMessage("Outerstellar", "Flyway + jOOQ are already wired up for this starter.")
    createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
  }

  override fun softDelete(syncId: String) {
    primaryDsl.update(MESSAGES)
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT", java.time.LocalDateTime::class.java)
        if (deletedAtField != null) {
          it.set(deletedAtField, java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toLocalDateTime())
        } else {
          it
        }
      }
      .set(MESSAGES.VERSION, MESSAGES.VERSION.plus(1))
      .where(MESSAGES.SYNC_ID.eq(syncId))
      .execute()
  }

  override fun restore(syncId: String) {
    primaryDsl.update(MESSAGES)
      .let {
        val deletedAtField = MESSAGES.field("DELETED_AT", java.time.LocalDateTime::class.java)
        if (deletedAtField != null) {
          it.setNull(deletedAtField)
        } else {
          it
        }
      }
      .set(MESSAGES.VERSION, MESSAGES.VERSION.plus(1))
      .where(MESSAGES.SYNC_ID.eq(syncId))
      .execute()
  }

  override fun updateMessage(message: StoredMessage): StoredMessage {
    val rows = primaryDsl.update(MESSAGES)
      .set(MESSAGES.AUTHOR, message.author)
      .set(MESSAGES.CONTENT, message.content)
      .set(MESSAGES.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
      .set(MESSAGES.DIRTY, message.dirty)
      .set(MESSAGES.DELETED, message.deleted)
      .set(MESSAGES.VERSION, message.version + 1)
      .where(MESSAGES.SYNC_ID.eq(message.syncId))
      .and(MESSAGES.VERSION.eq(message.version))
      .execute()

    if (rows == 0) {
      throw IllegalStateException("Optimistic locking failure for message ${message.syncId}")
    }

    return requireNotNull(findBySyncId(message.syncId))
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
      .set(MESSAGES.VERSION, 1L)
      .execute()
    return requireNotNull(findBySyncId(syncId))
  }

  private fun toStoredMessage(record: Record): StoredMessage =
    StoredMessage(
      syncId = record.get(MESSAGES.field("SYNC_ID", String::class.java)) ?: "unknown",
      author = record.get(MESSAGES.field("AUTHOR", String::class.java)) ?: "unknown",
      content = record.get(MESSAGES.field("CONTENT", String::class.java)) ?: "unknown",
      updatedAtEpochMs = record.get(MESSAGES.field("UPDATED_AT_EPOCH_MS", Long::class.java)) ?: 0L,
      dirty = record.get(MESSAGES.field("DIRTY", Boolean::class.java)) ?: false,
      deleted = record.get(MESSAGES.field("DELETED", Boolean::class.java)) ?: false,
      version = record.get(MESSAGES.field("VERSION", Long::class.java)) ?: 1L,
    )

  companion object {
    private const val lastSyncStateKey = "last_sync_epoch_ms"
  }
}
