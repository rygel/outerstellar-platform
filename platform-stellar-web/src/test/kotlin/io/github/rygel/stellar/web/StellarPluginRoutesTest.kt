package io.github.rygel.stellar.web

import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.SavedItemRepository
import io.github.rygel.stellar.service.CommentService
import io.github.rygel.stellar.service.CommunityService
import io.github.rygel.stellar.service.FeedService
import io.github.rygel.stellar.service.PostService
import io.github.rygel.stellar.service.VoteService
import io.mockk.mockk
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Verifies that the route classes can be instantiated and wired together via the StellarPlugin. Uses a minimal Koin
 * context with mocked services so no real database or server is needed.
 */
class StellarPluginRoutesTest {

    private val feedService = mockk<FeedService>(relaxed = true)
    private val communityService = mockk<CommunityService>(relaxed = true)
    private val postService = mockk<PostService>(relaxed = true)
    private val commentService = mockk<CommentService>(relaxed = true)
    private val voteService = mockk<VoteService>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val karmaRepository = mockk<KarmaRepository>(relaxed = true)
    private val savedItemRepository = mockk<SavedItemRepository>(relaxed = true)
    private val commentRepository = mockk<CommentRepository>(relaxed = true)

    private val context = mockk<PluginContext>(relaxed = true)

    @BeforeEach
    fun setUp() {
        startKoin {
            modules(
                module {
                    single { feedService }
                    single { communityService }
                    single { postService }
                    single { commentService }
                    single { voteService }
                    single { userRepository }
                    single { karmaRepository }
                    single { savedItemRepository }
                    single { commentRepository }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `routes returns a non-empty list`() {
        val plugin = StellarPlugin()
        val routes = plugin.routes(context)
        assertTrue(routes.isNotEmpty(), "StellarPlugin.routes() should return at least one route")
    }

    @Test
    fun `routes includes feed routes`() {
        val feedRoutes = FeedRoutes(context).routes
        assertTrue(feedRoutes.isNotEmpty(), "FeedRoutes should contribute routes")
    }

    @Test
    fun `routes includes community routes`() {
        val communityRoutes = CommunityRoutes(context).routes
        assertTrue(communityRoutes.isNotEmpty(), "CommunityRoutes should contribute routes")
    }

    @Test
    fun `routes includes post routes`() {
        val postRoutes = PostRoutes(context).routes
        assertTrue(postRoutes.isNotEmpty(), "PostRoutes should contribute routes")
    }

    @Test
    fun `routes includes vote routes`() {
        val voteRoutes = VoteRoutes(context).routes
        assertTrue(voteRoutes.isNotEmpty(), "VoteRoutes should contribute routes")
    }

    @Test
    fun `FeedRoutes contributes exactly 3 routes`() {
        val feedRoutes = FeedRoutes(context).routes
        kotlin.test.assertEquals(3, feedRoutes.size, "FeedRoutes should have home, popular, and feed fragment routes")
    }

    @Test
    fun `CommunityRoutes contributes exactly 5 routes`() {
        val communityRoutes = CommunityRoutes(context).routes
        kotlin.test.assertEquals(
            5,
            communityRoutes.size,
            "CommunityRoutes should have directory, community page, subscribe, create form, and create routes",
        )
    }

    @Test
    fun `PostRoutes contributes exactly 4 routes`() {
        val postRoutes = PostRoutes(context).routes
        kotlin.test.assertEquals(
            4,
            postRoutes.size,
            "PostRoutes should have submit form, create post, post detail, and create comment routes",
        )
    }

    @Test
    fun `VoteRoutes contributes exactly 1 route`() {
        val voteRoutes = VoteRoutes(context).routes
        kotlin.test.assertEquals(1, voteRoutes.size, "VoteRoutes should have a single vote route")
    }
}
