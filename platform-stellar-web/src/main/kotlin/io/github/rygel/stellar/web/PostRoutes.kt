package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.webContext
import io.github.rygel.stellar.model.CommentSort
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.service.CommentService
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.PostService
import io.github.rygel.stellar.service.StellarRateLimiter
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
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.routing.path
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PostRoutes(private val context: PluginContext) : KoinComponent {
    private val postService: PostService by inject()
    private val commentService: CommentService by inject()
    private val communityService: CommunityService by inject()
    private val rateLimiter: StellarRateLimiter by inject()
    private val renderer = context.renderer

    private val communityNamePath = Path.string().of("community")
    private val postIdPath = Path.string().of("postId")
    private val commentSortLens = Query.string().defaulted("sort", "best")

    val routes: List<ContractRoute> =
        listOf(
            "/r" / communityNamePath / "submit" meta
                {
                    summary = "Submit post form"
                } bindContract
                GET to
                { communityName: String, _ ->
                    { request: Request ->
                        val community = communityService.getCommunity(communityName)
                        val page =
                            SubmitPostPage(
                                communityName = community.name,
                                communityDisplayName = community.displayName,
                                communityId = community.id,
                            )
                        Response(Status.OK)
                            .header("content-type", "text/html; charset=utf-8")
                            .body(
                                renderer(
                                    context.buildPage(
                                        request,
                                        "Submit to r/${community.name}",
                                        "/r/${community.name}",
                                        page,
                                    )
                                )
                            )
                    }
                },
            "/r" / communityNamePath / "posts" meta
                {
                    summary = "Create post"
                } bindContract
                POST to
                { communityName: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        if (!rateLimiter.checkLimit("create:$userId")) {
                            return@to Response(Status.TOO_MANY_REQUESTS)
                                .body("Rate limit exceeded. Please wait before creating another post.")
                        }
                        val community = communityService.getCommunity(communityName)

                        val title = request.form("title") ?: return@to Response(Status.BAD_REQUEST)
                        val body = request.form("body")
                        val url = request.form("url")
                        val postType =
                            when {
                                !url.isNullOrBlank() -> PostType.LINK
                                else -> PostType.TEXT
                            }

                        val post = postService.createPost(community.id, userId, title, body, url, postType)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community.name}/posts/${post.id}")
                    }
                },
            "/r/{community}/posts" / postIdPath meta
                {
                    summary = "Post detail with comments"
                } bindContract
                GET to
                { postIdStr: String ->
                    { request: Request ->
                        val communityName = request.path("community") ?: ""
                        val postId = UUID.fromString(postIdStr)
                        val post = postService.getPost(postId)
                        val sort = parseCommentSort(commentSortLens(request))
                        val commentTree = commentService.getCommentTree(postId, sort)

                        val page =
                            PostDetailPage(
                                post =
                                    PostDetailViewModel(
                                        id = post.id,
                                        communityName = communityName,
                                        authorUsername = "",
                                        title = post.title,
                                        body = post.body,
                                        url = post.url,
                                        postType = post.postType,
                                        score = post.score,
                                        commentCount = post.commentCount,
                                        userVote = null,
                                        isLocked = post.isLocked,
                                        timeAgo = TimeFormatting.formatTimeAgo(post.createdAt),
                                    ),
                                comments = commentTree,
                                commentSort = commentSortLens(request),
                                communityName = communityName,
                            )
                        Response(Status.OK)
                            .header("content-type", "text/html; charset=utf-8")
                            .body(renderer(context.buildPage(request, post.title, "/r/$communityName", page)))
                    }
                },
            "/posts" / postIdPath / "comments" meta
                {
                    summary = "Create comment"
                } bindContract
                POST to
                { postIdStr: String, _ ->
                    { request: Request ->
                        val userId = request.webContext.user?.id ?: return@to Response(Status.UNAUTHORIZED)
                        if (!rateLimiter.checkLimit("create:$userId")) {
                            return@to Response(Status.TOO_MANY_REQUESTS)
                                .body("Rate limit exceeded. Please wait before commenting again.")
                        }
                        val postId = UUID.fromString(postIdStr)
                        val body = request.form("body") ?: return@to Response(Status.BAD_REQUEST)
                        val parentId =
                            request.form("parentCommentId")?.takeIf { it.isNotBlank() }?.let { UUID.fromString(it) }

                        commentService.createComment(postId, userId, body, parentId)

                        val post = postService.getPost(postId)
                        val community = communityService.getCommunityById(post.communityId)
                        Response(Status.SEE_OTHER).header("Location", "/r/${community?.name}/posts/$postId")
                    }
                },
        )

    private fun parseCommentSort(s: String): CommentSort =
        when (s.lowercase()) {
            "new" -> CommentSort.NEW
            "top" -> CommentSort.TOP
            "controversial" -> CommentSort.CONTROVERSIAL
            else -> CommentSort.BEST
        }
}
