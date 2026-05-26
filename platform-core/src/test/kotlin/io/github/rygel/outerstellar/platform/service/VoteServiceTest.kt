package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.MessageVote
import io.github.rygel.outerstellar.platform.model.VoteScore
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.VoteRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VoteServiceTest {
    private val voteRepository = mockk<VoteRepository>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val service = VoteService(voteRepository, messageRepository)

    private val userId = UUID.randomUUID()
    private val messageSyncId = "test-msg-1"

    @Test
    fun `vote creates new upvote when no existing vote`() {
        every { voteRepository.findByUserAndMessage(userId, messageSyncId) } returns null
        every { messageRepository.findBySyncId(messageSyncId) } returns mockk()
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns VoteScore(messageSyncId, 1, 0, 1, 1)

        val result = service.vote(messageSyncId, userId, 1)

        assertNotNull(result)
        assertEquals(1, result.netScore)
        verify { voteRepository.save(any()) }
    }

    @Test
    fun `vote creates new downvote when no existing vote`() {
        every { voteRepository.findByUserAndMessage(userId, messageSyncId) } returns null
        every { messageRepository.findBySyncId(messageSyncId) } returns mockk()
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns
            VoteScore(messageSyncId, 0, 1, -1, -1)

        val result = service.vote(messageSyncId, userId, -1)

        assertNotNull(result)
        assertEquals(-1, result.netScore)
        verify { voteRepository.save(any()) }
    }

    @Test
    fun `vote toggles off when same direction`() {
        val existing = MessageVote(messageSyncId = messageSyncId, userId = userId, direction = 1)
        every { voteRepository.findByUserAndMessage(userId, messageSyncId) } returns existing
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns
            VoteScore(messageSyncId, 0, 0, 0, null)

        val result = service.vote(messageSyncId, userId, 1)

        assertNotNull(result)
        assertEquals(0, result.netScore)
        assertNull(result.userVote)
        verify { voteRepository.delete(userId, messageSyncId) }
    }

    @Test
    fun `vote flips from upvote to downvote`() {
        val existing = MessageVote(messageSyncId = messageSyncId, userId = userId, direction = 1)
        every { voteRepository.findByUserAndMessage(userId, messageSyncId) } returns existing
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns
            VoteScore(messageSyncId, 0, 1, -1, -1)

        val result = service.vote(messageSyncId, userId, -1)

        assertNotNull(result)
        assertEquals(-1, result.netScore)
        verify { voteRepository.updateDirection(userId, messageSyncId, -1) }
    }

    @Test
    fun `vote flips from downvote to upvote`() {
        val existing = MessageVote(messageSyncId = messageSyncId, userId = userId, direction = -1)
        every { voteRepository.findByUserAndMessage(userId, messageSyncId) } returns existing
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns VoteScore(messageSyncId, 1, 0, 1, 1)

        val result = service.vote(messageSyncId, userId, 1)

        assertNotNull(result)
        assertEquals(1, result.netScore)
        verify { voteRepository.updateDirection(userId, messageSyncId, 1) }
    }

    @Test
    fun `vote returns null when message not found`() {
        every { messageRepository.findBySyncId(messageSyncId) } returns null

        val result = service.vote(messageSyncId, userId, 1)
        assertNull(result)
    }

    @Test
    fun `removeVote delegates to repository`() {
        service.removeVote(messageSyncId, userId)
        verify { voteRepository.delete(userId, messageSyncId) }
    }

    @Test
    fun `getScore returns score from repository`() {
        val expected = VoteScore(messageSyncId, 5, 2, 3, 1)
        every { voteRepository.findScoreByMessage(messageSyncId, userId) } returns expected

        val result = service.getScore(messageSyncId, userId)
        assertEquals(3, result.netScore)
        assertEquals(1, result.userVote)
    }
}
