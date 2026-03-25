package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.FlairService
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.string
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FlairRoutes(private val context: PluginContext) : KoinComponent {
    private val flairService: FlairService by inject()
    private val communityService: CommunityService by inject()
    private val memberRepo: CommunityMemberRepository by inject()

    private val communityNamePath = Path.string().of("name")
    private val flairIdPath = Path.string().of("id")

    val routes: List<ContractRoute> =
        listOf(
            // GET /r/{name}/flairs — list all flairs for a community
            "/r" / communityNamePath / "flairs" meta
                {
                    summary = "List community flairs"
                } bindContract
                GET to
                { name: String, _ ->
                    { request: Request ->
                        val community = communityService.getCommunity(name)
                        val flairs =
                            flairService.listPostFlairs(community.id) + flairService.listUserFlairs(community.id)
                        val json = flairsToJson(flairs)
                        Response(Status.OK).header("content-type", "application/json; charset=utf-8").body(json)
                    }
                },
            // POST /r/{name}/flairs — create a flair (mod only)
            "/r" / communityNamePath / "flairs" meta
                {
                    summary = "Create community flair"
                } bindContract
                POST to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        if (!isModerator(userId, community.id)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        val flairName = request.form("name") ?: return@to Response(Status.BAD_REQUEST)
                        val color = request.form("color") ?: "#666666"
                        val typeStr = request.form("type") ?: "POST"
                        val type =
                            try {
                                FlairType.valueOf(typeStr.uppercase())
                            } catch (_: IllegalArgumentException) {
                                return@to Response(Status.BAD_REQUEST)
                            }

                        val flair = flairService.createFlair(community.id, flairName, color, type)
                        val json = flairToJson(flair)
                        Response(Status.CREATED).header("content-type", "application/json; charset=utf-8").body(json)
                    }
                },
            // POST /flairs/{id}/delete — delete a flair (mod only)
            "/flairs" / flairIdPath / "delete" meta
                {
                    summary = "Delete flair"
                } bindContract
                POST to
                { id: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val flairId =
                            try {
                                UUID.fromString(id)
                            } catch (_: IllegalArgumentException) {
                                return@to Response(Status.BAD_REQUEST)
                            }

                        // Look up the flair to verify ownership and mod status
                        val existingFlair = findFlairById(flairId) ?: return@to Response(Status.NOT_FOUND)

                        if (!isModerator(userId, existingFlair.communityId)) {
                            return@to Response(Status.FORBIDDEN)
                        }

                        flairService.deleteFlair(flairId)
                        Response(Status.OK)
                    }
                },
            // POST /r/{name}/user-flair — set user flair
            "/r" / communityNamePath / "user-flair" meta
                {
                    summary = "Set user flair"
                } bindContract
                POST to
                { name: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        val community = communityService.getCommunity(name)

                        val flairIdStr = request.form("flairId") ?: return@to Response(Status.BAD_REQUEST)
                        val flairId =
                            try {
                                UUID.fromString(flairIdStr)
                            } catch (_: IllegalArgumentException) {
                                return@to Response(Status.BAD_REQUEST)
                            }

                        flairService.setUserFlair(userId, community.id, flairId)
                        Response(Status.OK)
                    }
                },
        )

    private fun isModerator(userId: UUID, communityId: UUID): Boolean {
        val membership = memberRepo.findMembership(userId, communityId)
        return membership != null &&
            (membership.role == CommunityRole.MODERATOR || membership.role == CommunityRole.ADMIN)
    }

    private fun findFlairById(id: UUID): Flair? {
        // Use the repository directly via Koin to look up a flair by ID
        val flairRepo: io.github.rygel.stellar.persistence.FlairRepository by inject()
        return flairRepo.findById(id)
    }

    private fun flairsToJson(flairs: List<Flair>): String {
        val items = flairs.joinToString(",") { flairToJson(it) }
        return "[$items]"
    }

    private fun flairToJson(flair: Flair): String =
        """{"id":"${flair.id}","communityId":"${flair.communityId}","name":"${escapeJson(flair.name)}","color":"${escapeJson(flair.color)}","type":"${flair.type}"}"""

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
