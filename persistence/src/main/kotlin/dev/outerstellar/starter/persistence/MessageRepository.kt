package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.MESSAGES
import dev.outerstellar.starter.jooq.tables.references.SYNC_STATE
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.sync.SyncMessage
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record

@Suppress("TooManyFunctions")
class JooqMessageRepository(private val dsl: DSLContext) : MessageRepository {
  override fun listMessages(query: String?, limit: Int, offset: Int): List<MessageSummary> {
    val results = dsl
      .select(
        MESSAGES.SYNC_ID,
        MESSAGES.AUTHOR,
        MESSAGES.CONTENT,
        MESSAGES.UPDATED_AT_EPOCH_MS,
        MESSAGES.DIRTY,
        MESSAGES.DELETED,
      )
      .from(MESSAGES)
      .let { 
        if (!query.isNullOrBlank()) {
          it.where(MESSAGES.DELETED.eq(false).and(MESSAGES.CONTENT.containsIgnoreCase(query).or(MESSAGES.AUTHOR.containsIgnoreCase(query))))
        } else {
          it.where(MESSAGES.DELETED.eq(false))
        }
      }
      .orderBy(MESSAGES.UPDATED_AT_EPOCH_MS.desc(), MESSAGES.ID.desc())
      .limit(limit)
      .offset(offset)
      .fetch(::toStoredMessage)
    
    return results.map(StoredMessage::toSummary)
  }

  override fun listDirtyMessages(): List<StoredMessage> =
    dsl
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
      .fetch(::toStoredMessage)

  override fun findBySyncId(syncId: String): StoredMessage? =
    dsl
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
      .fetchOne(::toStoredMessage)

  override fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage> =
    dsl
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
      .fetch(::toStoredMessage)

  override fun createServerMessage(author: String, content: String): StoredMessage =
    insertMessage(author = author, content = content, dirty = false)

  override fun createLocalMessage(author: String, content: String): StoredMessage =
    insertMessage(author = author, content = content, dirty = true)

  override fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean): StoredMessage {
    val existing = findBySyncId(message.syncId)
    if (existing == null) {
      dsl
        .insertInto(MESSAGES)
        .set(MESSAGES.SYNC_ID, message.syncId)
        .set(MESSAGES.AUTHOR, message.author)
        .set(MESSAGES.CONTENT, message.content)
        .set(MESSAGES.UPDATED_AT_EPOCH_MS, message.updatedAtEpochMs)
        .set(MESSAGES.DIRTY, dirty)
        .set(MESSAGES.DELETED, message.deleted)
        .execute()
    } else {
      dsl
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

    dsl.update(MESSAGES).set(MESSAGES.DIRTY, false).where(MESSAGES.SYNC_ID.`in`(syncIds)).execute()
  }

  override fun getLastSyncEpochMs(): Long =
    dsl
      .select(SYNC_STATE.STATE_VALUE)
      .from(SYNC_STATE)
      .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
      .fetchOne(SYNC_STATE.STATE_VALUE) ?: 0L

  override fun setLastSyncEpochMs(value: Long) {
    val hasState = dsl.fetchCount(SYNC_STATE, SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
    if (hasState) {
      dsl
        .update(SYNC_STATE)
        .set(SYNC_STATE.STATE_VALUE, value)
        .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
        .execute()
    } else {
      dsl
        .insertInto(SYNC_STATE)
        .set(SYNC_STATE.STATE_KEY, lastSyncStateKey)
        .set(SYNC_STATE.STATE_VALUE, value)
        .execute()
    }
  }

  override fun seedStarterMessages() {
    if (dsl.fetchCount(MESSAGES, MESSAGES.DELETED.eq(false)) > 0) {
      return
    }

    createServerMessage("Outerstellar", "Flyway + jOOQ are already wired up for this starter.")
    createServerMessage("http4k", "The home page is rendered with a Kotlin .kte JTE template.")
  }

  private fun insertMessage(author: String, content: String, dirty: Boolean): StoredMessage {
    val syncId = UUID.randomUUID().toString()
    dsl
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
