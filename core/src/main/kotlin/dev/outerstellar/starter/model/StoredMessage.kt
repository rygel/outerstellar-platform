package dev.outerstellar.starter.model

import dev.outerstellar.starter.sync.SyncMessage

data class StoredMessage(
  val syncId: String,
  val author: String,
  val content: String,
  val updatedAtEpochMs: Long,
  val dirty: Boolean,
  val deleted: Boolean,
  val version: Long = 1,
  val syncConflict: String? = null,
) {
  fun toSummary(): MessageSummary = MessageSummary(
    syncId = syncId, 
    author = author, 
    content = content, 
    updatedAtEpochMs = updatedAtEpochMs, 
    dirty = dirty, 
    version = version,
    hasConflict = syncConflict != null
  )

  fun toSyncMessage(): SyncMessage =
    SyncMessage(
      syncId = syncId,
      author = author,
      content = content,
      updatedAtEpochMs = updatedAtEpochMs,
      deleted = deleted,
    )
}
