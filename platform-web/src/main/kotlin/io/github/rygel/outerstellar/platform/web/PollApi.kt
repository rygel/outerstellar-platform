package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.CreatePollRequest
import io.github.rygel.outerstellar.platform.model.PollWithResults
import io.github.rygel.outerstellar.platform.service.PollService
import kotlinx.serialization.Serializable
import org.http4k.contract.ContractRoute
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
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.long
import org.http4k.lens.string

class PollApi(private val pollService: PollService) : ServerRoutes {

    @Serializable private data class CastVoteRequest(val optionId: Long)

    @Serializable private data class PollOptionResponse(val id: Long, val position: Int, val text: String)

    @Serializable
    private data class PollResultsResponse(
        val syncId: String,
        val question: String,
        val multiChoice: Boolean,
        val closed: Boolean,
        val deadline: String? = null,
        val options: List<PollOptionResponse>,
        val voteCounts: Map<Long, Int>,
        val totalVotes: Int,
        val userVotedOptionIds: Set<Long> = emptySet(),
    )

    @Serializable
    private data class PollSummary(
        val syncId: String,
        val question: String,
        val multiChoice: Boolean,
        val closed: Boolean,
        val deadline: String? = null,
        val totalVotes: Int = 0,
    )

    private val syncIdPath = Path.string().of("syncId")
    private val createPollLens = Body.auto<CreatePollRequest>().toLens()
    private val castVoteLens = Body.auto<CastVoteRequest>().toLens()
    private val pollResultsLens = Body.auto<PollResultsResponse>().toLens()
    private val pollListLens = Body.auto<List<PollSummary>>().toLens()

    private fun PollWithResults.toResponse() =
        PollResultsResponse(
            syncId = poll.syncId,
            question = poll.question,
            multiChoice = poll.multiChoice,
            closed = poll.closedAt != null,
            deadline = poll.deadline?.toString(),
            options = options.map { PollOptionResponse(it.id, it.position, it.optionText) },
            voteCounts = voteCounts,
            totalVotes = totalVotes,
            userVotedOptionIds = userVotedOptionIds,
        )

    override val routes: List<ContractRoute> =
        listOf(
            "/api/v1/polls" meta
                {
                    summary = "Create a new poll"
                    receiving(createPollLens)
                    returning(Status.CREATED to "Poll created")
                    returning(Status.BAD_REQUEST to "Invalid input")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.POST to
                { request: Request ->
                    val ctx = request.webContext
                    val user = ctx.user
                    if (user == null) {
                        return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                    }

                    val body = createPollLens(request)
                    val deadline = body.deadline?.let { java.time.Instant.parse(it) }
                    try {
                        val result =
                            pollService.createPoll(body.question, body.options, body.multiChoice, deadline, user.id)
                        Response(Status.CREATED).with(pollResultsLens of result.toResponse())
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid input")
                    }
                },
            "/api/v1/polls" meta
                {
                    summary = "List open polls"
                    returning(Status.OK, pollListLens to emptyList<PollSummary>())
                } bindContract
                Method.GET to
                { request: Request ->
                    val limit = Query.int().defaulted("limit", 20)(request)
                    val offset = Query.int().defaulted("offset", 0)(request)
                    val polls =
                        pollService.listOpen(limit, offset).map { p ->
                            PollSummary(
                                syncId = p.syncId,
                                question = p.question,
                                multiChoice = p.multiChoice,
                                closed = p.closedAt != null,
                                deadline = p.deadline?.toString(),
                            )
                        }
                    Response(Status.OK).with(pollListLens of polls)
                },
            "/api/v1/polls" / syncIdPath meta
                {
                    summary = "Get poll with results"
                    returning(Status.OK to "Poll with results")
                    returning(Status.NOT_FOUND to "Poll not found")
                } bindContract
                Method.GET to
                { syncId ->
                    { request: Request ->
                        val ctx = request.webContext
                        val result = pollService.getPoll(syncId, ctx.user?.id)
                        if (result != null) {
                            Response(Status.OK).with(pollResultsLens of result.toResponse())
                        } else {
                            Response(Status.NOT_FOUND).body("Poll not found")
                        }
                    }
                },
            "/api/v1/polls" / syncIdPath / "vote" meta
                {
                    summary = "Cast a vote on a poll"
                    receiving(castVoteLens)
                    returning(Status.OK to "Updated poll with results")
                    returning(Status.NOT_FOUND to "Poll not found")
                    returning(Status.CONFLICT to "Vote conflict")
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

                        val body = castVoteLens(request)
                        try {
                            val result = pollService.castVote(syncId, body.optionId, user.id)
                            if (result != null) {
                                Response(Status.OK).with(pollResultsLens of result.toResponse())
                            } else {
                                Response(Status.NOT_FOUND).body("Poll not found")
                            }
                        } catch (e: IllegalStateException) {
                            Response(Status.CONFLICT).body(e.message ?: "Vote conflict")
                        }
                    }
                },
            "/api/v1/polls" / syncIdPath / "vote" meta
                {
                    summary = "Remove a vote from a poll"
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

                        val optionId = Query.long().required("optionId")(request)
                        pollService.removeVote(syncId, optionId, user.id)
                        Response(Status.NO_CONTENT)
                    }
                },
            "/api/v1/polls" / syncIdPath / "close" meta
                {
                    summary = "Close a poll"
                    returning(Status.OK to "Poll closed")
                    returning(Status.FORBIDDEN to "Not the creator")
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

                        try {
                            pollService.closePoll(syncId, user.id)
                            Response(Status.OK).body("Poll closed")
                        } catch (e: IllegalStateException) {
                            Response(Status.FORBIDDEN).body(e.message ?: "Not the creator")
                        }
                    }
                },
            "/api/v1/polls" / syncIdPath meta
                {
                    summary = "Delete a poll"
                    returning(Status.NO_CONTENT to "Poll deleted")
                    returning(Status.FORBIDDEN to "Not the creator")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.DELETE to
                { syncId ->
                    { request: Request ->
                        val ctx = request.webContext
                        val user = ctx.user
                        if (user == null) {
                            return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                        }

                        try {
                            pollService.deletePoll(syncId, user.id)
                            Response(Status.NO_CONTENT)
                        } catch (e: IllegalStateException) {
                            Response(Status.FORBIDDEN).body(e.message ?: "Not the creator")
                        }
                    }
                },
        )
}
