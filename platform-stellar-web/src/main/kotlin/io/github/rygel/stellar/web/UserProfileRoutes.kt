package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.SavedItemType
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.SavedItemRepository
import io.github.rygel.stellar.service.FeedService
import io.github.rygel.stellar.web.TimeFormatting.toCardViewModel
import java.util.UUID
import org.http4k.contract.ContractRoute
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.template.ViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UserProfileRoutes(private val context: PluginContext) : KoinComponent {
    private val userRepo: UserRepository by inject()
    private val feedService: FeedService by inject()
    private val karmaRepo: KarmaRepository by inject()
    private val savedRepo: SavedItemRepository by inject()
    private val commentRepo: CommentRepository by inject()
    private val renderer = context.renderer

    private val usernamePath = Path.string().of("username")
    private val limitLens = Query.int().defaulted("limit", 25)
    private val offsetLens = Query.int().defaulted("offset", 0)

    val routes: List<ContractRoute> =
        listOf(
            "/u" / usernamePath meta
                {
                    summary = "User profile"
                } bindContract
                GET to
                { username ->
                    { request ->
                        val user =
                            userRepo.findByUsername(username)
                                ?: return@to Response(Status.NOT_FOUND)
                                    .header("content-type", "text/html; charset=utf-8")
                                    .body("User not found")

                        val limit = limitLens(request)
                        val offset = offsetLens(request)
                        val viewerId = request.webContext.user?.id
                        val karma = karmaRepo.getKarma(user.id)
                        val posts = feedService.userPosts(user.id, limit, offset, viewerId)

                        val page =
                            UserProfilePage(
                                username = user.username,
                                karma = karma,
                                posts = posts.map { it.toCardViewModel() },
                                tab = "posts",
                            )
                        renderPage(request, "u/${user.username}", "/u/${user.username}", page)
                    }
                },
            "/saved" meta
                {
                    summary = "Saved items"
                } bindContract
                GET to
                { request ->
                    val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                    val limit = limitLens(request)
                    val offset = offsetLens(request)
                    val savedItems = savedRepo.listSaved(userId, limit, offset)

                    val page = SavedPage(savedItems = savedItems)
                    renderPage(request, "Saved", "/saved", page)
                },
            "/save" meta
                {
                    summary = "Toggle save item"
                } bindContract
                POST to
                { request ->
                    val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)

                    val targetId = UUID.fromString(request.form("targetId") ?: return@to Response(Status.BAD_REQUEST))
                    val targetType =
                        SavedItemType.valueOf(request.form("targetType") ?: return@to Response(Status.BAD_REQUEST))

                    if (savedRepo.isSaved(userId, targetId, targetType)) {
                        savedRepo.unsave(userId, targetId, targetType)
                    } else {
                        savedRepo.save(userId, targetId, targetType)
                    }

                    Response(Status.OK).header("HX-Trigger", "refresh").body("")
                },
        )

    private fun renderPage(
        request: org.http4k.core.Request,
        title: String,
        section: String,
        data: ViewModel,
    ): Response =
        Response(Status.OK)
            .header("content-type", "text/html; charset=utf-8")
            .body(renderer(context.buildPage(request, title, section, data)))
}
