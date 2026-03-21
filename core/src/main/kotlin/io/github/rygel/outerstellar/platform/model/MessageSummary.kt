package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MessageSummary(
    val syncId: String,
    val author: String,
    val content: String,
    val updatedAtEpochMs: Long,
    val dirty: Boolean,
    val version: Long = 1,
    val hasConflict: Boolean = false,
) {
    fun updatedAtLabel(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return Instant.ofEpochMilli(updatedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
}
