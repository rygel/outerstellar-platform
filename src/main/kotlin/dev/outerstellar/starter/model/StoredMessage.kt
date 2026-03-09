package dev.outerstellar.starter.model

import dev.outerstellar.starter.sync.SyncMessage

data class StoredMessage(
  val syncId: String,
  val author: String,
  val content: String,
  val updatedAtEpochMs: Long,
  val dirty: Boolean,
  val deleted: Boolean,
) {
  fun toSummary(): MessageSummary = MessageSummary(syncId, author, content, updatedAtEpochMs, dirty)

  fun toSyncMessage(): SyncMessage =
    SyncMessage(
      syncId = syncId,
      author = author,
      content = content,
      updatedAtEpochMs = updatedAtEpochMs,
      deleted = deleted,
    )
}
