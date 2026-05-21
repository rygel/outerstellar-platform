package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.CreatePollRequest
import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollWithResults
import io.github.rygel.outerstellar.platform.service.PollService
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.long

class PollApi(private val pollService: PollService) : ServerRoutes {

    @Serializable private data class CastVoteRequest(val optionId: Long)

    @Serializable private data class PollListResponse(val polls: List<@Contextual Poll>)

    private val createPollLens = Body.auto<CreatePollRequest>().toLens()
    private val castVoteLens = Body.auto<CastVoteRequest>().toLens()
    private val pollResultsLens = Body.auto<PollWithResults>().toLens()
    private val pollListLens = Body.auto<PollListResponse>().toLens()

    private val stubPoll = Poll(syncId = "stub", creatorId = UUID(0, 0), question = "stub")

    override val routes: List<ContractRoute> =
        listOf(
            "/api/v1/polls" meta
                {
                    summary = "Create a new poll"
                    receiving(createPollLens)
                    returning(Status.CREATED, pollResultsLens to PollWithResults(stubPoll, emptyList(), emptyMap(), 0))
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
                        Response(Status.CREATED).with(pollResultsLens of result)
                    } catch (e: IllegalArgumentException) {
                        Response(Status.BAD_REQUEST).body(e.message ?: "Invalid input")
                    }
                },
            "/api/v1/polls" meta
                {
                    summary = "List open polls"
                    returning(Status.OK, pollListLens to PollListResponse(emptyList()))
                } bindContract
                Method.GET to
                { request: Request ->
                    val limit = Query.int().defaulted("limit", 20)(request)
                    val offset = Query.int().defaulted("offset", 0)(request)
                    val polls = pollService.listOpen(limit, offset)
                    Response(Status.OK).with(pollListLens of PollListResponse(polls))
                },
            "/api/v1/polls/{syncId}" meta
                {
                    summary = "Get poll with results"
                    returning(Status.OK, pollResultsLens to PollWithResults(stubPoll, emptyList(), emptyMap(), 0))
                    returning(Status.NOT_FOUND to "Poll not found")
                } bindContract
                Method.GET to
                { request: Request ->
                    val syncId =
                        extractSyncId(request, "") ?: return@to Response(Status.BAD_REQUEST).body("Missing syncId")
                    val ctx = request.webContext
                    val result = pollService.getPoll(syncId, ctx.user?.id)
                    if (result != null) {
                        Response(Status.OK).with(pollResultsLens of result)
                    } else {
                        Response(Status.NOT_FOUND).body("Poll not found")
                    }
                },
            "/api/v1/polls/{syncId}/vote" meta
                {
                    summary = "Cast a vote on a poll"
                    receiving(castVoteLens)
                    returning(Status.OK, pollResultsLens to PollWithResults(stubPoll, emptyList(), emptyMap(), 0))
                    returning(Status.NOT_FOUND to "Poll not found")
                    returning(Status.CONFLICT to "Vote conflict")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.POST to
                { request: Request ->
                    val syncId =
                        extractSyncId(request, PATH_SUFFIX_VOTE)
                            ?: return@to Response(Status.BAD_REQUEST).body("Missing syncId")
                    val ctx = request.webContext
                    val user = ctx.user
                    if (user == null) {
                        return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                    }

                    val body = castVoteLens(request)
                    try {
                        val result = pollService.castVote(syncId, body.optionId, user.id)
                        if (result != null) {
                            Response(Status.OK).with(pollResultsLens of result)
                        } else {
                            Response(Status.NOT_FOUND).body("Poll not found")
                        }
                    } catch (e: IllegalStateException) {
                        Response(Status.CONFLICT).body(e.message ?: "Vote conflict")
                    }
                },
            "/api/v1/polls/{syncId}/vote" meta
                {
                    summary = "Remove a vote from a poll"
                    returning(Status.NO_CONTENT to "Vote removed")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.DELETE to
                { request: Request ->
                    val syncId =
                        extractSyncId(request, PATH_SUFFIX_VOTE)
                            ?: return@to Response(Status.BAD_REQUEST).body("Missing syncId")
                    val ctx = request.webContext
                    val user = ctx.user
                    if (user == null) {
                        return@to Response(Status.UNAUTHORIZED).body("Authentication required")
                    }

                    val optionId = Query.long().required("optionId")(request)
                    pollService.removeVote(syncId, optionId, user.id)
                    Response(Status.NO_CONTENT)
                },
            "/api/v1/polls/{syncId}/close" meta
                {
                    summary = "Close a poll"
                    returning(Status.OK to "Poll closed")
                    returning(Status.FORBIDDEN to "Not the creator")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.POST to
                { request: Request ->
                    val syncId =
                        extractSyncId(request, PATH_SUFFIX_CLOSE)
                            ?: return@to Response(Status.BAD_REQUEST).body("Missing syncId")
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
                },
            "/api/v1/polls/{syncId}" meta
                {
                    summary = "Delete a poll"
                    returning(Status.NO_CONTENT to "Poll deleted")
                    returning(Status.FORBIDDEN to "Not the creator")
                    returning(Status.UNAUTHORIZED to "Authentication required")
                } bindContract
                Method.DELETE to
                { request: Request ->
                    val syncId =
                        extractSyncId(request, "") ?: return@to Response(Status.BAD_REQUEST).body("Missing syncId")
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
                },
        )

    companion object {
        private const val PATH_PREFIX = "/api/v1/polls/"
        private const val PATH_SUFFIX_VOTE = "/vote"
        private const val PATH_SUFFIX_CLOSE = "/close"

        fun extractSyncId(request: Request, suffix: String): String? {
            val path = request.uri.path
            if (!path.startsWith(PATH_PREFIX)) return null
            if (suffix.isNotEmpty() && !path.endsWith(suffix)) return null
            val syncId =
                if (suffix.isNotEmpty()) {
                    path.removePrefix(PATH_PREFIX).removeSuffix(suffix)
                } else {
                    path.removePrefix(PATH_PREFIX)
                }
            return syncId.ifBlank { null }
        }
    }
}
