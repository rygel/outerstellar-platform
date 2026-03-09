package dev.outerstellar.starter.sync

import dev.outerstellar.starter.service.MessageService
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

class SyncApi(private val messageService: MessageService) {
  private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
  private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
  private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()

  val routes: RoutingHttpHandler =
    routes(
      "/api/v1/sync" bind
        GET to
        { request ->
          val since = request.query("since")?.toLongOrNull() ?: 0L
          val response = messageService.getChangesSince(since)
          Response(Status.OK).with(pullResponseLens of response)
        },
      "/api/v1/sync" bind
        POST to
        { request ->
          val syncRequest = pushRequestLens(request)
          val syncResponse = messageService.processPushRequest(syncRequest)

          Response(Status.OK)
            .with(pushResponseLens of syncResponse)
        },
    )
}
