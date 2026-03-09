package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.sync.SyncMessage

interface MessageRepository {
  fun listMessages(query: String? = null, year: Int? = null, limit: Int = 100, offset: Int = 0): List<MessageSummary>

  fun listDirtyMessages(): List<StoredMessage>

  fun findBySyncId(syncId: String): StoredMessage?

  fun findChangesSince(updatedAtEpochMs: Long): List<StoredMessage>

  fun createServerMessage(author: String, content: String): StoredMessage

  fun createLocalMessage(author: String, content: String): StoredMessage

  fun upsertSyncedMessage(message: SyncMessage, dirty: Boolean = false): StoredMessage

  fun markClean(syncIds: Collection<String>)

  fun getLastSyncEpochMs(): Long

  fun setLastSyncEpochMs(value: Long)

  fun seedStarterMessages()

  fun softDelete(syncId: String)
  
  fun updateMessage(message: StoredMessage): StoredMessage
}
