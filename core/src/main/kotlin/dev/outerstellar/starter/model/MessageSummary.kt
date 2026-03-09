package dev.outerstellar.starter.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MessageSummary(
  val syncId: String,
  val author: String,
  val content: String,
  val updatedAtEpochMs: Long,
  val dirty: Boolean,
) {
  fun updatedAtLabel(): String =
    messageTimestampFormatter.format(
      Instant.ofEpochMilli(updatedAtEpochMs).atZone(ZoneId.systemDefault())
    )

  fun syncStatusLabel(): String = if (dirty) "Pending sync" else "Synced"
}

private val messageTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
