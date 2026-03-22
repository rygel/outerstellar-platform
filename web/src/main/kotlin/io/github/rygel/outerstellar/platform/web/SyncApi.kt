package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.security.SecurityRules
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncConflict
import io.github.rygel.outerstellar.platform.sync.SyncContact
import io.github.rygel.outerstellar.platform.sync.SyncContactConflict
import io.github.rygel.outerstellar.platform.sync.SyncMessage
import io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushContactRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.long

class SyncApi(
    private val messageService: MessageService?,
    private val contactService: ContactService?,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
) : ServerRoutes {
    private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
    private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()
    private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()

    private val pushContactRequestLens = Body.auto<SyncPushContactRequest>().toLens()
    private val pushContactResponseLens = Body.auto<SyncPushContactResponse>().toLens()
    private val pullContactResponseLens = Body.auto<SyncPullContactResponse>().toLens()

    private val sinceLens = Query.long().optional("since")

    override val routes: List<ContractRoute> = buildList {
        if (messageService != null) {
            add(
                "/api/v1/sync" meta
                    {
                        summary = "Pull changes from server"
                        queries += sinceLens
                        returning(Status.OK, pullResponseLens to SyncPullResponse(emptyList<SyncMessage>(), 0L))
                    } bindContract
                    GET to
                    { request ->
                        val since = sinceLens(request) ?: 0L
                        val response = messageService.getChangesSince(since)
                        Response(Status.OK).with(pullResponseLens of response)
                    }
            )
            add(
                "/api/v1/sync" meta
                    {
                        summary = "Push changes to server"
                        receiving(pushRequestLens)
                        returning(Status.OK, pushResponseLens to SyncPushResponse(0, emptyList<SyncConflict>()))
                    } bindContract
                    POST to
                    { request ->
                        val syncRequest = pushRequestLens(request)
                        val syncResponse = messageService.processPushRequest(syncRequest)
                        val userId = SecurityRules.USER_KEY(request)?.id?.toString()
                        if (userId != null) {
                            analytics.track(
                                userId,
                                "Messages Synced",
                                mapOf("pushed" to syncRequest.messages.size, "conflicts" to syncResponse.conflicts.size),
                            )
                        }
                        Response(Status.OK).with(pushResponseLens of syncResponse)
                    }
            )
        }
        if (contactService != null) {
            add(
                "/api/v1/sync/contacts" meta
                    {
                        summary = "Pull contact changes from server"
                        queries += sinceLens
                        returning(
                            Status.OK,
                            pullContactResponseLens to SyncPullContactResponse(emptyList<SyncContact>(), 0L),
                        )
                    } bindContract
                    GET to
                    { request ->
                        val since = sinceLens(request) ?: 0L
                        val response = contactService.getChangesSince(since)
                        Response(Status.OK).with(pullContactResponseLens of response)
                    }
            )
            add(
                "/api/v1/sync/contacts" meta
                    {
                        summary = "Push contact changes to server"
                        receiving(pushContactRequestLens)
                        returning(
                            Status.OK,
                            pushContactResponseLens to SyncPushContactResponse(0, emptyList<SyncContactConflict>()),
                        )
                    } bindContract
                    POST to
                    { request ->
                        val syncRequest = pushContactRequestLens(request)
                        val syncResponse = contactService.processPushRequest(syncRequest)
                        Response(Status.OK).with(pushContactResponseLens of syncResponse)
                    }
            )
        }
    }
}
