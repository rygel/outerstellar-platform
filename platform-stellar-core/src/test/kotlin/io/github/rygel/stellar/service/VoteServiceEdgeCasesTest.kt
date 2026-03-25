package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.VoteRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VoteServiceEdgeCasesTest {

    private lateinit var voteRepo: VoteRepository
    private lateinit var postRepo: PostRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var karmaRepo: KarmaRepository
    private lateinit var service: VoteService

    @BeforeEach
    fun setUp() {
        voteRepo = mockk()
        postRepo = mockk()
        commentRepo = mockk()
        karmaRepo = mockk()
        service = VoteService(voteRepo, postRepo, commentRepo, karmaRepo)
    }

    @Test
    fun `vote with value 0 throws IllegalArgumentException`() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        val exception = assertThrows<IllegalArgumentException> { service.vote(userId, targetId, VoteTarget.POST, 0) }
        assertEquals("Vote value must be +1 or -1", exception.message)
    }

    @Test
    fun `vote with value 2 throws IllegalArgumentException`() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        val exception = assertThrows<IllegalArgumentException> { service.vote(userId, targetId, VoteTarget.POST, 2) }
        assertEquals("Vote value must be +1 or -1", exception.message)
    }

    @Test
    fun `vote with value minus 2 throws IllegalArgumentException`() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        assertThrows<IllegalArgumentException> { service.vote(userId, targetId, VoteTarget.COMMENT, -2) }
    }

    @Test
    fun `vote on POST updates postKarma`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { postRepo.updateScore(postId, 1) } just runs
        every { karmaRepo.updatePostKarma(userId, 1) } just runs

        val delta = service.vote(userId, postId, VoteTarget.POST, 1)

        assertEquals(1, delta)
        verify { karmaRepo.updatePostKarma(userId, 1) }
        verify(exactly = 0) { karmaRepo.updateCommentKarma(any(), any()) }
    }

    @Test
    fun `vote on COMMENT updates commentKarma`() {
        val userId = UUID.randomUUID()
        val commentId = UUID.randomUUID()

        every { voteRepo.findVote(userId, commentId, VoteTarget.COMMENT) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { commentRepo.updateScore(commentId, -1) } just runs
        every { karmaRepo.updateCommentKarma(userId, -1) } just runs

        val delta = service.vote(userId, commentId, VoteTarget.COMMENT, -1)

        assertEquals(-1, delta)
        verify { karmaRepo.updateCommentKarma(userId, -1) }
        verify(exactly = 0) { karmaRepo.updatePostKarma(any(), any()) }
    }

    @Test
    fun `vote on POST does not update comment score`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { postRepo.updateScore(postId, 1) } just runs
        every { karmaRepo.updatePostKarma(userId, 1) } just runs

        service.vote(userId, postId, VoteTarget.POST, 1)

        verify { postRepo.updateScore(postId, 1) }
        verify(exactly = 0) { commentRepo.updateScore(any(), any()) }
    }

    @Test
    fun `vote on COMMENT does not update post score`() {
        val userId = UUID.randomUUID()
        val commentId = UUID.randomUUID()

        every { voteRepo.findVote(userId, commentId, VoteTarget.COMMENT) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { commentRepo.updateScore(commentId, 1) } just runs
        every { karmaRepo.updateCommentKarma(userId, 1) } just runs

        service.vote(userId, commentId, VoteTarget.COMMENT, 1)

        verify { commentRepo.updateScore(commentId, 1) }
        verify(exactly = 0) { postRepo.updateScore(any(), any()) }
    }
}
