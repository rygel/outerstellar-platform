package dev.outerstellar.starter.web

import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncConflict
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPullResponse
import dev.outerstellar.starter.sync.SyncPushRequest
import dev.outerstellar.starter.sync.SyncPushResponse
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.long

class SyncApi(private val messageService: MessageService) : ServerRoutes {
  private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
  private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
  private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()
  private val sinceLens = Query.long().optional("since")

  override val routes: List<ContractRoute> = listOf(
    "/api/v1/sync" meta {
      summary = "Pull changes from server"
      queries += sinceLens
      returning(Status.OK, pullResponseLens to SyncPullResponse(emptyList<SyncMessage>(), 0L))
    } bindContract GET to { request ->
      val since = sinceLens(request) ?: 0L
      val response = messageService.getChangesSince(since)
      Response(Status.OK).with(pullResponseLens of response)
    },
    "/api/v1/sync" meta {
      summary = "Push changes to server"
      receiving(pushRequestLens)
      returning(Status.OK, pushResponseLens to SyncPushResponse(0, emptyList<SyncConflict>()))
    } bindContract POST to { request ->
      val syncRequest = pushRequestLens(request)
      val syncResponse = messageService.processPushRequest(syncRequest)
      Response(Status.OK).with(pushResponseLens of syncResponse)
    }
  )
}
