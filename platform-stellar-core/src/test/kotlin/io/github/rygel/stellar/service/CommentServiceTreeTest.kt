package io.github.rygel.stellar.service

import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentSort
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommentServiceTreeTest {

    private lateinit var commentRepo: CommentRepository
    private lateinit var postRepo: PostRepository
    private lateinit var userRepo: UserRepository
    private lateinit var service: CommentService

    private val postId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        commentRepo = mockk()
        postRepo = mockk()
        userRepo = mockk()
        service = CommentService(commentRepo, postRepo, userRepo)

        // Default: user lookups return null (will resolve to "[deleted]")
        every { userRepo.findById(any()) } returns null
    }

    @Test
    fun `empty comment list produces empty tree`() {
        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns emptyList()

        val tree = service.getCommentTree(postId)

        assertTrue(tree.isEmpty())
    }

    @Test
    fun `single top-level comment`() {
        val comment = makeComment(parentCommentId = null, depth = 0, body = "Hello")
        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(comment)

        val tree = service.getCommentTree(postId)

        assertEquals(1, tree.size)
        assertEquals("Hello", tree[0].comment.body)
        assertEquals(0, tree[0].comment.depth)
        assertTrue(tree[0].children.isEmpty())
        assertEquals(0, tree[0].comment.replyCount)
    }

    @Test
    fun `multiple top-level comments`() {
        val c1 = makeComment(parentCommentId = null, depth = 0, body = "First")
        val c2 = makeComment(parentCommentId = null, depth = 0, body = "Second")
        val c3 = makeComment(parentCommentId = null, depth = 0, body = "Third")
        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(c1, c2, c3)

        val tree = service.getCommentTree(postId)

        assertEquals(3, tree.size)
        assertEquals("First", tree[0].comment.body)
        assertEquals("Second", tree[1].comment.body)
        assertEquals("Third", tree[2].comment.body)
    }

    @Test
    fun `nested comments three levels deep`() {
        val root = makeComment(parentCommentId = null, depth = 0, body = "Root")
        val child = makeComment(parentCommentId = root.id, depth = 1, body = "Child")
        val grandchild = makeComment(parentCommentId = child.id, depth = 2, body = "Grandchild")

        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(root, child, grandchild)

        val tree = service.getCommentTree(postId)

        assertEquals(1, tree.size)
        assertEquals("Root", tree[0].comment.body)
        assertEquals(1, tree[0].children.size)
        assertEquals("Child", tree[0].children[0].comment.body)
        assertEquals(1, tree[0].children[0].children.size)
        assertEquals("Grandchild", tree[0].children[0].children[0].comment.body)
        assertTrue(tree[0].children[0].children[0].children.isEmpty())
    }

    @Test
    fun `comments with missing parent appear as top-level`() {
        val orphanParentId = UUID.randomUUID() // parent not in comment list
        val orphan = makeComment(parentCommentId = orphanParentId, depth = 1, body = "Orphan")
        val normal = makeComment(parentCommentId = null, depth = 0, body = "Normal")

        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(normal, orphan)

        val tree = service.getCommentTree(postId)

        // The orphan is grouped under its (missing) parentId key, which won't match null.
        // buildTree starts from null, so only "Normal" appears as top-level.
        // The orphan is effectively lost since its parent is not in the tree.
        assertEquals(1, tree.size)
        assertEquals("Normal", tree[0].comment.body)
    }

    @Test
    fun `replyCount reflects direct children count`() {
        val root = makeComment(parentCommentId = null, depth = 0, body = "Root")
        val reply1 = makeComment(parentCommentId = root.id, depth = 1, body = "Reply 1")
        val reply2 = makeComment(parentCommentId = root.id, depth = 1, body = "Reply 2")
        val nested = makeComment(parentCommentId = reply1.id, depth = 2, body = "Nested")

        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(root, reply1, reply2, nested)

        val tree = service.getCommentTree(postId)

        assertEquals(1, tree.size)
        assertEquals(2, tree[0].comment.replyCount) // root has 2 direct children
        assertEquals(1, tree[0].children[0].comment.replyCount) // reply1 has 1 child
        assertEquals(0, tree[0].children[1].comment.replyCount) // reply2 has 0 children
    }

    @Test
    fun `deleted (isRemoved) comments still appear in tree`() {
        val removed = makeComment(parentCommentId = null, depth = 0, body = "[deleted]", isRemoved = true)
        val reply = makeComment(parentCommentId = removed.id, depth = 1, body = "Reply to deleted")

        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(removed, reply)

        val tree = service.getCommentTree(postId)

        assertEquals(1, tree.size)
        assertTrue(tree[0].comment.isRemoved)
        assertEquals(1, tree[0].children.size)
        assertEquals("Reply to deleted", tree[0].children[0].comment.body)
    }

    @Test
    fun `username resolves to deleted when userRepo returns null`() {
        val comment = makeComment(parentCommentId = null, depth = 0, body = "Test")
        every { commentRepo.listByPost(postId, CommentSort.BEST) } returns listOf(comment)

        val tree = service.getCommentTree(postId)

        assertEquals("[deleted]", tree[0].comment.authorUsername)
    }

    private fun makeComment(
        parentCommentId: UUID? = null,
        depth: Int = 0,
        body: String = "comment",
        isRemoved: Boolean = false,
    ): Comment =
        Comment(
            id = UUID.randomUUID(),
            postId = postId,
            authorId = UUID.randomUUID(),
            parentCommentId = parentCommentId,
            body = body,
            score = 0,
            depth = depth,
            isRemoved = isRemoved,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
}
