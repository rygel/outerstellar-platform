package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentSort
import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.Post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiCommentRepositoryTest : H2StellarTest() {

    private lateinit var commentRepo: JdbiCommentRepository
    private lateinit var postRepo: JdbiPostRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var userId: UUID
    private lateinit var postId: UUID
    private lateinit var communityId: UUID

    @BeforeEach
    fun setUp() {
        commentRepo = JdbiCommentRepository(jdbi)
        postRepo = JdbiPostRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("commenter")
        communityId = UUID.randomUUID()
        communityRepo.create(
            Community(id = communityId, name = "testcommunity", displayName = "Test Community", creatorId = userId)
        )
        postId = UUID.randomUUID()
        postRepo.create(
            Post(id = postId, communityId = communityId, authorId = userId, title = "Test Post", body = "Post body")
        )
    }

    private fun makeComment(
        body: String = "This is a comment",
        parentCommentId: UUID? = null,
        depth: Int = 0,
        authorId: UUID = userId,
        score: Int = 0,
    ): Comment =
        Comment(
            id = UUID.randomUUID(),
            postId = postId,
            authorId = authorId,
            parentCommentId = parentCommentId,
            body = body,
            depth = depth,
            score = score,
        )

    @Test
    fun `create top-level comment and findById`() {
        val comment = makeComment(body = "Top-level comment")
        commentRepo.create(comment)

        val found = commentRepo.findById(comment.id)
        assertNotNull(found)
        assertEquals(comment.id, found.id)
        assertEquals("Top-level comment", found.body)
        assertEquals(postId, found.postId)
        assertEquals(userId, found.authorId)
        assertNull(found.parentCommentId)
        assertEquals(0, found.depth)
    }

    @Test
    fun `create reply with parent`() {
        val parent = makeComment(body = "Parent comment")
        commentRepo.create(parent)

        val reply = makeComment(body = "Reply comment", parentCommentId = parent.id, depth = 1)
        commentRepo.create(reply)

        val found = commentRepo.findById(reply.id)
        assertNotNull(found)
        assertEquals(parent.id, found.parentCommentId)
        assertEquals(1, found.depth)
        assertEquals("Reply comment", found.body)
    }

    @Test
    fun `listByPost returns all comments`() {
        commentRepo.create(makeComment(body = "Comment 1"))
        commentRepo.create(makeComment(body = "Comment 2"))
        commentRepo.create(makeComment(body = "Comment 3"))

        val comments = commentRepo.listByPost(postId, CommentSort.NEW)
        assertEquals(3, comments.size)
    }

    @Test
    fun `updateScore`() {
        val comment = makeComment(score = 0)
        commentRepo.create(comment)

        commentRepo.updateScore(comment.id, 10)
        val found = commentRepo.findById(comment.id)
        assertNotNull(found)
        assertEquals(10, found.score)

        commentRepo.updateScore(comment.id, -3)
        val updated = commentRepo.findById(comment.id)
        assertNotNull(updated)
        assertEquals(7, updated.score)
    }

    @Test
    fun `countByPost`() {
        assertEquals(0, commentRepo.countByPost(postId))

        commentRepo.create(makeComment(body = "Comment 1"))
        commentRepo.create(makeComment(body = "Comment 2"))

        assertEquals(2, commentRepo.countByPost(postId))
    }

    @Test
    fun `listByUser`() {
        val otherUserId = createTestUser("othercommenter")
        commentRepo.create(makeComment(body = "My comment 1", authorId = userId))
        commentRepo.create(makeComment(body = "My comment 2", authorId = userId))
        commentRepo.create(makeComment(body = "Other comment", authorId = otherUserId))

        val myComments = commentRepo.listByUser(userId, limit = 10, offset = 0)
        assertEquals(2, myComments.size)
    }
}
