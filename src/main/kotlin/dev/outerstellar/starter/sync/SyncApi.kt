package dev.outerstellar.starter.sync

import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

class SyncApi(private val repository: MessageRepository) {
  private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
  private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
  private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()

  val routes: RoutingHttpHandler =
    routes(
      "/api/v1/sync" bind
        GET to
        { request ->
          val since = request.query("since")?.toLongOrNull() ?: 0L
          val response =
            SyncPullResponse(
              messages = repository.findChangesSince(since).map { it.toSyncMessage() },
              serverTimestamp = System.currentTimeMillis(),
            )
          Response(Status.OK).with(pullResponseLens of response)
        },
      "/api/v1/sync" bind
        POST to
        { request ->
          val syncRequest = pushRequestLens(request)
          val conflicts = mutableListOf<SyncConflict>()
          var appliedCount = 0

          syncRequest.messages.forEach { incoming ->
            val current = repository.findBySyncId(incoming.syncId)
            when {
              current == null || incoming.updatedAtEpochMs > current.updatedAtEpochMs -> {
                repository.upsertSyncedMessage(incoming, dirty = false)
                appliedCount++
              }

              incoming.updatedAtEpochMs < current.updatedAtEpochMs -> {
                conflicts +=
                  SyncConflict(
                    syncId = incoming.syncId,
                    reason = "Server has a newer version of this message.",
                    serverMessage = current.toSyncMessage(),
                  )
              }
            }
          }

          Response(Status.OK)
            .with(
              pushResponseLens of
                SyncPushResponse(appliedCount = appliedCount, conflicts = conflicts)
            )
        },
    )
}
