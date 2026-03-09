package dev.outerstellar.starter.sync

import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto

class SyncService(
  private val repository: MessageRepository,
  private val serverBaseUrl: String,
  private val httpClient: HttpHandler = ApacheClient(),
) {
  private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
  private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
  private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()

  fun sync(): SyncStats {
    val dirtyMessages = repository.listDirtyMessages().map { it.toSyncMessage() }
    var pushedCount = 0
    var conflictCount = 0

    if (dirtyMessages.isNotEmpty()) {
      val pushResponse =
        httpClient(
          Request(POST, "$serverBaseUrl/api/v1/sync")
            .with(pushRequestLens of SyncPushRequest(dirtyMessages))
        )
      require(pushResponse.status == Status.OK) { "Unexpected push status: ${pushResponse.status}" }

      val pushBody = pushResponseLens(pushResponse)
      val conflictIds = pushBody.conflicts.map { it.syncId }.toSet()
      repository.markClean(dirtyMessages.map { it.syncId }.filterNot { it in conflictIds })
      pushedCount = pushBody.appliedCount
      conflictCount = pushBody.conflicts.size
    }

    val pullResponse =
      httpClient(
        Request(GET, "$serverBaseUrl/api/v1/sync?since=${repository.getLastSyncEpochMs()}")
      )
    require(pullResponse.status == Status.OK) { "Unexpected pull status: ${pullResponse.status}" }

    val pullBody = pullResponseLens(pullResponse)
    pullBody.messages.forEach { incoming ->
      val current = repository.findBySyncId(incoming.syncId)
      if (
        current == null || incoming.updatedAtEpochMs >= current.updatedAtEpochMs || !current.dirty
      ) {
        repository.upsertSyncedMessage(incoming, dirty = false)
      }
    }
    repository.setLastSyncEpochMs(pullBody.serverTimestamp)

    return SyncStats(
      pushedCount = pushedCount,
      pulledCount = pullBody.messages.size,
      conflictCount = conflictCount,
    )
  }
}
