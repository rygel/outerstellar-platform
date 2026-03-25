package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentNotFoundException
import io.github.rygel.stellar.model.CommentSort
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.StellarTransactionManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommentServiceTest {

    private lateinit var commentRepo: CommentRepository
    private lateinit var postRepo: PostRepository
    private lateinit var txManager: StellarTransactionManager
    private lateinit var service: CommentService

    @BeforeEach
    fun setUp() {
        commentRepo = mockk()
        postRepo = mockk()
        txManager = mockk()
        every { txManager.inTransaction(any<() -> Any>()) } answers { firstArg<() -> Any>().invoke() }
        service = CommentService(commentRepo, postRepo, txManager = txManager)
    }

    @Test
    fun `getComment returns comment when found`() {
        val commentId = UUID.randomUUID()
        val comment = comment(id = commentId, body = "Great post!")
        every { commentRepo.findById(commentId) } returns comment

        val result = service.getComment(commentId)

        assertEquals("Great post!", result.body)
    }

    @Test
    fun `getComment throws CommentNotFoundException when not found`() {
        val commentId = UUID.randomUUID()
        every { commentRepo.findById(commentId) } returns null

        assertThrows<CommentNotFoundException> { service.getComment(commentId) }
    }

    @Test
    fun `createComment creates top-level comment with depth 0`() {
        val postId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        every { commentRepo.create(any()) } answers { firstArg() }
        every { postRepo.incrementCommentCount(postId, 1) } just runs

        val result = service.createComment(postId, authorId, "Top-level comment")

        assertEquals(0, result.depth)
        assertNull(result.parentCommentId)
        assertEquals("Top-level comment", result.body)
        verify { postRepo.incrementCommentCount(postId, 1) }
    }

    @Test
    fun `createComment creates reply with parent depth plus one`() {
        val postId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val parent = comment(id = parentId, postId = postId, depth = 2)

        every { commentRepo.findById(parentId) } returns parent
        every { commentRepo.create(any()) } answers { firstArg() }
        every { postRepo.incrementCommentCount(postId, 1) } just runs

        val result = service.createComment(postId, authorId, "A reply", parentCommentId = parentId)

        assertEquals(3, result.depth)
        assertEquals(parentId, result.parentCommentId)
    }

    @Test
    fun `createComment trims body text`() {
        val postId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        every { commentRepo.create(any()) } answers { firstArg() }
        every { postRepo.incrementCommentCount(postId, 1) } just runs

        val result = service.createComment(postId, authorId, "  padded text  ")

        assertEquals("padded text", result.body)
    }

    @Test
    fun `getCommentTree builds correct tree structure`() {
        val postId = UUID.randomUUID()
        val rootId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        val rootComment = comment(id = rootId, postId = postId, authorId = authorId, body = "root", depth = 0)
        val childComment =
            comment(
                id = childId,
                postId = postId,
                authorId = authorId,
                body = "child",
                depth = 1,
                parentCommentId = rootId,
            )

        every { commentRepo.listByPost(postId, CommentSort.BEST, any(), any()) } returns
            listOf(rootComment, childComment)

        val tree = service.getCommentTree(postId)

        assertEquals(1, tree.size)
        assertEquals("root", tree[0].comment.body)
        assertEquals(1, tree[0].children.size)
        assertEquals("child", tree[0].children[0].comment.body)
    }

    @Test
    fun `getCommentTree returns empty list when no comments`() {
        val postId = UUID.randomUUID()
        every { commentRepo.listByPost(postId, CommentSort.BEST, any(), any()) } returns emptyList()

        val tree = service.getCommentTree(postId)

        assertEquals(0, tree.size)
    }

    @Test
    fun `getCommentTree handles multiple root comments`() {
        val postId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        val root1 = comment(postId = postId, authorId = authorId, body = "first", depth = 0)
        val root2 = comment(postId = postId, authorId = authorId, body = "second", depth = 0)

        every { commentRepo.listByPost(postId, CommentSort.BEST, any(), any()) } returns listOf(root1, root2)

        val tree = service.getCommentTree(postId)

        assertEquals(2, tree.size)
    }

    private fun comment(
        id: UUID = UUID.randomUUID(),
        postId: UUID = UUID.randomUUID(),
        authorId: UUID = UUID.randomUUID(),
        body: String = "test comment",
        depth: Int = 0,
        parentCommentId: UUID? = null,
    ) =
        Comment(
            id = id,
            postId = postId,
            authorId = authorId,
            body = body,
            depth = depth,
            parentCommentId = parentCommentId,
        )
}
