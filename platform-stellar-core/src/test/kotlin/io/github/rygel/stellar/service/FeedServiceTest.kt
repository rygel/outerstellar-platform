package io.github.rygel.stellar.service

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.model.SavedItemType
import io.github.rygel.stellar.model.TimeRange
import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.SavedItemRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.github.rygel.stellar.persistence.VoteRepository
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedServiceTest {
    private val postRepo: PostRepository = mockk()
    private val voteRepo: VoteRepository = mockk()
    private val savedRepo: SavedItemRepository = mockk()
    private val subscriptionRepo: SubscriptionRepository = mockk()
    private val userRepo: UserRepository = mockk()

    private lateinit var feedService: FeedService

    private val userId = UUID.randomUUID()
    private val communityId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        clearMocks(postRepo, voteRepo, savedRepo, subscriptionRepo, userRepo)
        feedService = FeedService(postRepo, voteRepo, savedRepo, subscriptionRepo, userRepo)
    }

    private fun makePost(id: UUID = UUID.randomUUID(), authorUsername: String = "testuser"): PostSummary =
        PostSummary(
            id = id,
            communityName = "testsub",
            communityDisplayName = "Test Sub",
            authorUsername = authorUsername,
            title = "Test Post",
            postType = PostType.TEXT,
            score = 10,
            commentCount = 2,
            createdAt = Instant.now(),
        )

    // 1. homeFeed with logged-in user who has subscriptions
    @Test
    fun `homeFeed with logged-in user returns enriched posts from subscribed communities`() {
        val communityIds = listOf(communityId, UUID.randomUUID())
        val post1 = makePost()
        val post2 = makePost()
        val posts = listOf(post1, post2)

        every { subscriptionRepo.findSubscribedCommunityIds(userId) } returns communityIds
        every { postRepo.listFeed(communityIds, PostSort.HOT, TimeRange.ALL, 25, 0) } returns posts
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns mapOf(post1.id to 1)
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns setOf(post2.id)

        val result = feedService.homeFeed(userId, PostSort.HOT, TimeRange.ALL, 25, 0)

        assertEquals(2, result.size)
        assertEquals(1, result[0].userVote)
        assertFalse(result[0].isSaved)
        assertNull(result[1].userVote)
        assertTrue(result[1].isSaved)

        verify { subscriptionRepo.findSubscribedCommunityIds(userId) }
        verify { postRepo.listFeed(communityIds, PostSort.HOT, TimeRange.ALL, 25, 0) }
    }

    // 2. homeFeed with logged-in user but no subscriptions falls back to listAll
    @Test
    fun `homeFeed with logged-in user and no subscriptions falls back to listAll`() {
        val post = makePost()

        every { subscriptionRepo.findSubscribedCommunityIds(userId) } returns emptyList()
        every { postRepo.listAll(PostSort.NEW, TimeRange.WEEK, 10, 5) } returns listOf(post)
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns emptyMap()
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns emptySet()

        val result = feedService.homeFeed(userId, PostSort.NEW, TimeRange.WEEK, 10, 5)

        assertEquals(1, result.size)
        verify { postRepo.listAll(PostSort.NEW, TimeRange.WEEK, 10, 5) }
        verify(exactly = 0) { postRepo.listFeed(any(), any(), any(), any(), any()) }
    }

    // 3. homeFeed with anonymous user uses listAll with no vote/save enrichment
    @Test
    fun `homeFeed with anonymous user uses listAll without vote or save enrichment`() {
        val post = makePost()

        every { postRepo.listAll(PostSort.HOT, TimeRange.ALL, 25, 0) } returns listOf(post)

        val result = feedService.homeFeed(null, PostSort.HOT, TimeRange.ALL, 25, 0)

        assertEquals(1, result.size)
        assertNull(result[0].userVote)
        assertFalse(result[0].isSaved)

        verify(exactly = 0) { subscriptionRepo.findSubscribedCommunityIds(any()) }
        verify(exactly = 0) { voteRepo.findUserVotes(any(), any(), any()) }
        verify(exactly = 0) { savedRepo.findSavedIds(any(), any(), any()) }
    }

    // 4. popularFeed returns all posts with enrichment
    @Test
    fun `popularFeed returns all posts enriched for logged-in user`() {
        val post = makePost()

        every { postRepo.listAll(PostSort.TOP, TimeRange.DAY, 50, 0) } returns listOf(post)
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns mapOf(post.id to -1)
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns emptySet()

        val result = feedService.popularFeed(PostSort.TOP, TimeRange.DAY, 50, 0, userId)

        assertEquals(1, result.size)
        assertEquals(-1, result[0].userVote)
        assertFalse(result[0].isSaved)
    }

    // 5. communityFeed delegates to listByCommunity with enrichment
    @Test
    fun `communityFeed delegates to listByCommunity and enriches posts`() {
        val post = makePost()

        every { postRepo.listByCommunity(communityId, PostSort.NEW, TimeRange.ALL, 25, 0) } returns listOf(post)
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns emptyMap()
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns setOf(post.id)

        val result = feedService.communityFeed(communityId, PostSort.NEW, TimeRange.ALL, 25, 0, userId)

        assertEquals(1, result.size)
        assertTrue(result[0].isSaved)
        verify { postRepo.listByCommunity(communityId, PostSort.NEW, TimeRange.ALL, 25, 0) }
    }

    // 6. userPosts delegates to listByUser
    @Test
    fun `userPosts delegates to listByUser and enriches for viewer`() {
        val targetUserId = UUID.randomUUID()
        val post = makePost()

        every { postRepo.listByUser(targetUserId, 25, 0) } returns listOf(post)
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns emptyMap()
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns emptySet()

        val result = feedService.userPosts(targetUserId, 25, 0, userId)

        assertEquals(1, result.size)
        verify { postRepo.listByUser(targetUserId, 25, 0) }
    }

    // 7. searchPosts delegates to search
    @Test
    fun `searchPosts delegates to search and enriches results`() {
        val post = makePost()

        every { postRepo.search("kotlin", 10, 0) } returns listOf(post)
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns mapOf(post.id to 1)
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns emptySet()

        val result = feedService.searchPosts("kotlin", 10, 0, userId)

        assertEquals(1, result.size)
        assertEquals(1, result[0].userVote)
        verify { postRepo.search("kotlin", 10, 0) }
    }

    // 8. enrichPosts adds userVote and isSaved correctly
    @Test
    fun `enrichPosts sets userVote and isSaved on each post correctly`() {
        val post1 = makePost()
        val post2 = makePost()
        val post3 = makePost()
        val posts = listOf(post1, post2, post3)

        every { postRepo.listAll(PostSort.HOT, TimeRange.ALL, 25, 0) } returns posts
        every { voteRepo.findUserVotes(userId, any(), VoteTarget.POST) } returns mapOf(post1.id to 1, post3.id to -1)
        every { savedRepo.findSavedIds(userId, any(), SavedItemType.POST) } returns setOf(post1.id, post2.id)

        val result = feedService.popularFeed(PostSort.HOT, TimeRange.ALL, 25, 0, userId)

        assertEquals(3, result.size)
        // post1: upvoted and saved
        assertEquals(1, result[0].userVote)
        assertTrue(result[0].isSaved)
        // post2: no vote, saved
        assertNull(result[1].userVote)
        assertTrue(result[1].isSaved)
        // post3: downvoted, not saved
        assertEquals(-1, result[2].userVote)
        assertFalse(result[2].isSaved)
    }

    // 9. enrichPosts with empty post list returns empty
    @Test
    fun `enrichPosts with empty post list returns empty without calling vote or saved repos`() {
        every { postRepo.listAll(PostSort.HOT, TimeRange.ALL, 25, 0) } returns emptyList()

        val result = feedService.popularFeed(PostSort.HOT, TimeRange.ALL, 25, 0, userId)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { voteRepo.findUserVotes(any(), any(), any()) }
        verify(exactly = 0) { savedRepo.findSavedIds(any(), any(), any()) }
    }

    // 10. resolveUsername caches results
    @Test
    fun `resolveUsername caches results and only calls userRepo once per userId`() {
        val targetId = UUID.randomUUID()
        val user =
            User(
                id = targetId,
                username = "cacheduser",
                email = "cached@example.com",
                passwordHash = "hash",
                role = UserRole.USER,
            )

        every { userRepo.findById(targetId) } returns user

        val first = feedService.resolveUsername(targetId)
        val second = feedService.resolveUsername(targetId)

        assertEquals("cacheduser", first)
        assertEquals("cacheduser", second)
        verify(exactly = 1) { userRepo.findById(targetId) }
    }

    @Test
    fun `resolveUsername returns deleted for unknown user`() {
        val unknownId = UUID.randomUUID()
        every { userRepo.findById(unknownId) } returns null

        val result = feedService.resolveUsername(unknownId)

        assertEquals("[deleted]", result)
    }
}
