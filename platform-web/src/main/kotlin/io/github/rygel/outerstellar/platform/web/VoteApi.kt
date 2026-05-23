package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.VoteScore
import io.github.rygel.outerstellar.platform.service.VoteService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.lens.Path
import org.http4k.lens.string

class VoteApi(private val voteService: VoteService) : ServerRoutes {

    private data class VoteRequest(val direction: Int)

    private val voteRequestLens = Body.auto<VoteRequest>().toLens()
    private val voteScoreLens = Body.auto<VoteScore>().toLens()
    private val syncIdPath = Path.string().of("syncId")

    override val routes =
        listOf(
            "/api/v1/messages" / syncIdPath / "vote" meta
                {
                    summary = "Vote on a message"
                    receiving(voteRequestLens)
                    returning(Status.OK, voteScoreLens to VoteScore("", 0, 0, 0, null))
                    returning(Status.BAD_REQUEST to "Invalid direction")
                    returning(Status.NOT_FOUND to "Message not found")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.POST to
                { syncId, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        val user = ctx.user
                        if (user == null) {
                            return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                        }

                        val body = voteRequestLens(request)
                        if (body.direction != 1 && body.direction != -1) {
                            return@to Response(Status.BAD_REQUEST).body("Direction must be 1 or -1")
                        }

                        val score = voteService.vote(syncId, user.id, body.direction)
                        if (score == null) {
                            return@to Response(Status.NOT_FOUND).body("Message not found")
                        } else {
                            Response(Status.OK).with(voteScoreLens of score)
                        }
                    }
                },
            "/api/v1/messages" / syncIdPath / "vote" meta
                {
                    summary = "Remove vote from a message"
                    returning(Status.NO_CONTENT to "Vote removed")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.DELETE to
                { syncId, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        val user = ctx.user
                        if (user == null) {
                            return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                        }

                        voteService.removeVote(syncId, user.id)
                        Response(Status.NO_CONTENT)
                    }
                },
            "/api/v1/messages" / syncIdPath / "vote" meta
                {
                    summary = "Get vote score for a message"
                    returning(Status.OK, voteScoreLens to VoteScore("", 0, 0, 0, null))
                } bindContract
                Method.GET to
                { syncId, _ ->
                    { request: Request ->
                        val ctx = request.webContext
                        val score = voteService.getScore(syncId, ctx.user?.id)
                        Response(Status.OK).with(voteScoreLens of score)
                    }
                },
        )
}
