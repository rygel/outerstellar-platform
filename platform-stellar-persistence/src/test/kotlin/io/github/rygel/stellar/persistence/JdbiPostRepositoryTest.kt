package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.model.TimeRange
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiPostRepositoryTest : H2StellarTest() {

    private lateinit var postRepo: JdbiPostRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var userId: UUID
    private lateinit var communityId: UUID

    @BeforeEach
    fun setUp() {
        postRepo = JdbiPostRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("postauthor")
        communityId = createCommunity("testcommunity")
    }

    private fun createCommunity(name: String): UUID {
        val community =
            Community(
                id = UUID.randomUUID(),
                name = name,
                displayName = "Test Community",
                description = "A test community",
                creatorId = userId,
            )
        communityRepo.create(community)
        return community.id
    }

    private fun makePost(
        title: String = "Test Post",
        body: String? = "This is a test post body",
        communityId: UUID = this.communityId,
        authorId: UUID = this.userId,
        score: Int = 0,
        commentCount: Int = 0,
        postType: PostType = PostType.TEXT,
    ): Post =
        Post(
            id = UUID.randomUUID(),
            communityId = communityId,
            authorId = authorId,
            title = title,
            body = body,
            postType = postType,
            score = score,
            commentCount = commentCount,
        )

    @Test
    fun `create and findById round-trip`() {
        val post = makePost()
        postRepo.create(post)

        val found = postRepo.findById(post.id)
        assertNotNull(found)
        assertEquals(post.id, found.id)
        assertEquals("Test Post", found.title)
        assertEquals("This is a test post body", found.body)
        assertEquals(communityId, found.communityId)
        assertEquals(userId, found.authorId)
        assertEquals(PostType.TEXT, found.postType)
    }

    @Test
    fun `findById returns null for unknown`() {
        assertNull(postRepo.findById(UUID.randomUUID()))
    }

    @Test
    fun `listByCommunity returns posts`() {
        postRepo.create(makePost(title = "First Post"))
        postRepo.create(makePost(title = "Second Post"))

        val posts = postRepo.listByCommunity(communityId, PostSort.NEW, TimeRange.ALL, limit = 10, offset = 0)
        assertEquals(2, posts.size)
    }

    @Test
    fun `listAll returns posts across communities`() {
        val community2Id = createCommunity("othercommunity")
        postRepo.create(makePost(title = "Post in community 1"))
        postRepo.create(makePost(title = "Post in community 2", communityId = community2Id))

        val allPosts = postRepo.listAll(PostSort.NEW, TimeRange.ALL, limit = 10, offset = 0)
        assertEquals(2, allPosts.size)
    }

    @Test
    fun `updateScore changes score`() {
        val post = makePost(score = 0)
        postRepo.create(post)

        postRepo.updateScore(post.id, 5)
        val found = postRepo.findById(post.id)
        assertNotNull(found)
        assertEquals(5, found.score)

        postRepo.updateScore(post.id, -2)
        val updated = postRepo.findById(post.id)
        assertNotNull(updated)
        assertEquals(3, updated.score)
    }

    @Test
    fun `incrementCommentCount`() {
        val post = makePost(commentCount = 0)
        postRepo.create(post)

        postRepo.incrementCommentCount(post.id, 1)
        postRepo.incrementCommentCount(post.id, 1)

        val found = postRepo.findById(post.id)
        assertNotNull(found)
        assertEquals(2, found.commentCount)
    }

    @Test
    fun `search by title`() {
        postRepo.create(makePost(title = "Kotlin coroutines tutorial"))
        postRepo.create(makePost(title = "Java streams guide"))
        postRepo.create(makePost(title = "Advanced Kotlin patterns"))

        val results = postRepo.search("kotlin", limit = 10, offset = 0)
        assertEquals(2, results.size)
    }

    @Test
    fun `countByCommunity`() {
        assertEquals(0, postRepo.countByCommunity(communityId))

        postRepo.create(makePost(title = "Post 1"))
        postRepo.create(makePost(title = "Post 2"))

        assertEquals(2, postRepo.countByCommunity(communityId))
    }

    @Test
    fun `listByUser`() {
        val otherUserId = createTestUser("otheruser")
        postRepo.create(makePost(title = "My post 1", authorId = userId))
        postRepo.create(makePost(title = "My post 2", authorId = userId))
        postRepo.create(makePost(title = "Other post", authorId = otherUserId))

        val myPosts = postRepo.listByUser(userId, limit = 10, offset = 0)
        assertEquals(2, myPosts.size)
    }
}
