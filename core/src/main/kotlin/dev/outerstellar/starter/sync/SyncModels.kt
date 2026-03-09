package dev.outerstellar.starter.sync

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncMessage(
  val syncId: String,
  val author: String,
  val content: String,
  val updatedAtEpochMs: Long,
  val deleted: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushRequest(val messages: List<SyncMessage> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncConflict(
  val syncId: String,
  val reason: String,
  val serverMessage: SyncMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushResponse(
  val appliedCount: Int = 0,
  val conflicts: List<SyncConflict> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPullResponse(
  val messages: List<SyncMessage> = emptyList(),
  val serverTimestamp: Long = 0,
)

data class SyncStats(
  val pushedCount: Int = 0,
  val pulledCount: Int = 0,
  val conflictCount: Int = 0,
)

/**
 * Annotation to ignore unknown properties during JSON deserialization.
 * Replaces Jackson's JsonIgnoreProperties to avoid dependency conflicts with http4k 6.x.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonIgnoreProperties(val ignoreUnknown: Boolean = false)
