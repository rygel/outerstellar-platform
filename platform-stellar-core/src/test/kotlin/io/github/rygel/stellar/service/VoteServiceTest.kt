package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Vote
import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.KarmaRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.StellarTransactionManager
import io.github.rygel.stellar.persistence.VoteRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VoteServiceTest {

    private lateinit var voteRepo: VoteRepository
    private lateinit var postRepo: PostRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var karmaRepo: KarmaRepository
    private lateinit var txManager: StellarTransactionManager
    private lateinit var service: VoteService

    @BeforeEach
    fun setUp() {
        voteRepo = mockk()
        postRepo = mockk()
        commentRepo = mockk()
        karmaRepo = mockk()
        txManager = mockk()
        every { txManager.inTransaction(any<() -> Any>()) } answers { firstArg<() -> Any>().invoke() }
        service = VoteService(voteRepo, postRepo, commentRepo, karmaRepo, txManager)
    }

    @Test
    fun `new upvote on post returns delta of 1`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { postRepo.updateScore(postId, 1) } just runs
        every { karmaRepo.updatePostKarma(userId, 1) } just runs

        val delta = service.vote(userId, postId, VoteTarget.POST, 1)

        assertEquals(1, delta)
        verify { voteRepo.upsert(any()) }
        verify { postRepo.updateScore(postId, 1) }
        verify { karmaRepo.updatePostKarma(userId, 1) }
    }

    @Test
    fun `new downvote on comment returns delta of -1`() {
        val userId = UUID.randomUUID()
        val commentId = UUID.randomUUID()

        every { voteRepo.findVote(userId, commentId, VoteTarget.COMMENT) } returns null
        every { voteRepo.upsert(any()) } just runs
        every { commentRepo.updateScore(commentId, -1) } just runs
        every { karmaRepo.updateCommentKarma(userId, -1) } just runs

        val delta = service.vote(userId, commentId, VoteTarget.COMMENT, -1)

        assertEquals(-1, delta)
        verify { commentRepo.updateScore(commentId, -1) }
        verify { karmaRepo.updateCommentKarma(userId, -1) }
    }

    @Test
    fun `change vote from upvote to downvote returns delta of -2`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val existing = Vote(userId = userId, targetId = postId, targetType = VoteTarget.POST, value = 1)

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns existing
        every { voteRepo.upsert(any()) } just runs
        every { postRepo.updateScore(postId, -2) } just runs
        every { karmaRepo.updatePostKarma(userId, -2) } just runs

        val delta = service.vote(userId, postId, VoteTarget.POST, -1)

        assertEquals(-2, delta)
        verify { voteRepo.upsert(any()) }
        verify { postRepo.updateScore(postId, -2) }
    }

    @Test
    fun `change vote from downvote to upvote returns delta of 2`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val existing = Vote(userId = userId, targetId = postId, targetType = VoteTarget.POST, value = -1)

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns existing
        every { voteRepo.upsert(any()) } just runs
        every { postRepo.updateScore(postId, 2) } just runs
        every { karmaRepo.updatePostKarma(userId, 2) } just runs

        val delta = service.vote(userId, postId, VoteTarget.POST, 1)

        assertEquals(2, delta)
    }

    @Test
    fun `undo vote by voting same direction twice returns negative of original`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val existing = Vote(userId = userId, targetId = postId, targetType = VoteTarget.POST, value = 1)

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns existing
        every { voteRepo.delete(userId, postId, VoteTarget.POST) } just runs
        every { postRepo.updateScore(postId, -1) } just runs
        every { karmaRepo.updatePostKarma(userId, -1) } just runs

        val delta = service.vote(userId, postId, VoteTarget.POST, 1)

        assertEquals(-1, delta)
        verify { voteRepo.delete(userId, postId, VoteTarget.POST) }
        verify(exactly = 0) { voteRepo.upsert(any()) }
    }

    @Test
    fun `undo downvote returns delta of 1`() {
        val userId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val existing = Vote(userId = userId, targetId = commentId, targetType = VoteTarget.COMMENT, value = -1)

        every { voteRepo.findVote(userId, commentId, VoteTarget.COMMENT) } returns existing
        every { voteRepo.delete(userId, commentId, VoteTarget.COMMENT) } just runs
        every { commentRepo.updateScore(commentId, 1) } just runs
        every { karmaRepo.updateCommentKarma(userId, 1) } just runs

        val delta = service.vote(userId, commentId, VoteTarget.COMMENT, -1)

        assertEquals(1, delta)
        verify { voteRepo.delete(userId, commentId, VoteTarget.COMMENT) }
    }

    @Test
    fun `vote rejects invalid value`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        assertThrows<IllegalArgumentException> { service.vote(userId, postId, VoteTarget.POST, 0) }

        assertThrows<IllegalArgumentException> { service.vote(userId, postId, VoteTarget.POST, 2) }
    }

    @Test
    fun `getUserVote returns value when vote exists`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val vote = Vote(userId = userId, targetId = postId, targetType = VoteTarget.POST, value = 1)

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns vote

        val result = service.getUserVote(userId, postId, VoteTarget.POST)

        assertEquals(1, result)
    }

    @Test
    fun `getUserVote returns null when no vote exists`() {
        val userId = UUID.randomUUID()
        val postId = UUID.randomUUID()

        every { voteRepo.findVote(userId, postId, VoteTarget.POST) } returns null

        val result = service.getUserVote(userId, postId, VoteTarget.POST)

        assertEquals(null, result)
    }
}
