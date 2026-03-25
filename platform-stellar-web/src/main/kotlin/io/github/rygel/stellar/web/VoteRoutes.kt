package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.service.VoteService
import org.http4k.contract.ContractRoute
import org.http4k.contract.meta
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VoteRoutes(private val context: PluginContext) : KoinComponent {
    private val voteService: VoteService by inject()
    private val renderer = context.renderer

    val routes: List<ContractRoute> =
        listOf(
            "/vote" meta
                {
                    summary = "Cast vote"
                } bindContract
                POST to
                { request ->
                    val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                    val targetId =
                        (request.form("targetId") ?: return@to Response(Status.BAD_REQUEST)).toUUIDOrNull()
                            ?: return@to Response(Status.BAD_REQUEST).body("Invalid ID format")
                    val targetType =
                        VoteTarget.valueOf(request.form("targetType") ?: return@to Response(Status.BAD_REQUEST))
                    val value = (request.form("value") ?: return@to Response(Status.BAD_REQUEST)).toInt()

                    voteService.vote(userId, targetId, targetType, value)

                    // Get updated vote state for fragment
                    val userVote = voteService.getUserVote(userId, targetId, targetType)
                    val currentScore = voteService.getScore(targetId, targetType)
                    val fragment =
                        VoteButtonsFragment(
                            targetId = targetId,
                            targetType = targetType.name,
                            score = currentScore,
                            userVote = userVote,
                        )
                    Response(Status.OK).header("content-type", "text/html; charset=utf-8").body(renderer(fragment))
                }
        )
}
