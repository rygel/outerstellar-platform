package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.Poll
import io.github.rygel.outerstellar.platform.model.PollOption
import io.github.rygel.outerstellar.platform.persistence.PollRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PollServiceTest {
    private val pollRepository = mockk<PollRepository>(relaxed = true)
    private val service = PollService(pollRepository)

    private val creatorId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val syncId = "test-poll-sync-id"
    private val pollId = 1L

    private fun openPoll(multiChoice: Boolean = false, deadline: Instant? = null, creatorId: UUID = this.creatorId) =
        Poll(
            id = pollId,
            syncId = syncId,
            creatorId = creatorId,
            question = "What is your favorite color?",
            multiChoice = multiChoice,
            closedAt = null,
            deadline = deadline,
        )

    private fun stubGetPoll(poll: Poll, userId: UUID? = creatorId) {
        every { pollRepository.findOptions(poll.id) } returns
            listOf(
                PollOption(id = 10L, pollId = poll.id, position = 0, optionText = "Red"),
                PollOption(id = 11L, pollId = poll.id, position = 1, optionText = "Blue"),
            )
        every { pollRepository.getVoteCounts(poll.id) } returns mapOf(10L to 1, 11L to 0)
        if (userId != null) {
            every { pollRepository.getUserVotes(poll.id, userId) } returns setOf(10L)
        }
    }

    @Test
    fun `createPoll succeeds with valid input`() {
        val createdPoll = openPoll()
        every { pollRepository.create(any(), any()) } returns createdPoll
        every { pollRepository.findById(syncId) } returns createdPoll
        stubGetPoll(createdPoll)

        val result = service.createPoll("What is your favorite color?", listOf("Red", "Blue"), false, null, creatorId)

        assertNotNull(result)
        verify { pollRepository.create(any(), any()) }
    }

    @Test
    fun `createPoll rejects blank question`() {
        assertFailsWith<IllegalArgumentException> {
            service.createPoll("   ", listOf("Red", "Blue"), false, null, creatorId)
        }
    }

    @Test
    fun `createPoll rejects too few options`() {
        assertFailsWith<IllegalArgumentException> {
            service.createPoll("Question?", listOf("Only one"), false, null, creatorId)
        }
    }

    @Test
    fun `createPoll rejects too many options`() {
        val options = (1..11).map { "Option $it" }
        assertFailsWith<IllegalArgumentException> { service.createPoll("Question?", options, false, null, creatorId) }
    }

    @Test
    fun `castVote succeeds on open single-choice poll`() {
        val poll = openPoll(multiChoice = false)
        val optionId = 10L
        val option = PollOption(id = optionId, pollId = pollId, position = 0, optionText = "Red")

        every { pollRepository.findById(syncId) } returns poll
        every { pollRepository.findOptionById(optionId) } returns option
        every { pollRepository.getUserVotes(pollId, otherUserId) } returns emptySet()
        every { pollRepository.findOptions(pollId) } returns listOf(option)
        every { pollRepository.getVoteCounts(pollId) } returns mapOf(optionId to 1)
        every { pollRepository.getUserVotes(pollId, otherUserId) } returns setOf(optionId)

        val result = service.castVote(syncId, optionId, otherUserId)

        assertNotNull(result)
        verify { pollRepository.castVote(pollId, optionId, otherUserId) }
    }

    @Test
    fun `castVote rejects second vote on single-choice poll`() {
        val poll = openPoll(multiChoice = false)
        val otherOptionId = 11L
        val option = PollOption(id = otherOptionId, pollId = pollId, position = 1, optionText = "Blue")

        every { pollRepository.findById(syncId) } returns poll
        every { pollRepository.findOptionById(otherOptionId) } returns option
        every { pollRepository.getUserVotes(pollId, otherUserId) } returns setOf(10L)

        assertFailsWith<IllegalStateException> { service.castVote(syncId, otherOptionId, otherUserId) }
    }

    @Test
    fun `castVote allows multiple votes on multi-choice poll`() {
        val poll = openPoll(multiChoice = true)
        val newOptionId = 11L
        val option = PollOption(id = newOptionId, pollId = pollId, position = 1, optionText = "Blue")

        every { pollRepository.findById(syncId) } returns poll
        every { pollRepository.findOptionById(newOptionId) } returns option
        every { pollRepository.getUserVotes(pollId, otherUserId) } returns setOf(10L)
        every { pollRepository.findOptions(pollId) } returns
            listOf(PollOption(id = 10L, pollId = pollId, position = 0, optionText = "Red"), option)
        every { pollRepository.getVoteCounts(pollId) } returns mapOf(10L to 1, 11L to 1)
        every { pollRepository.getUserVotes(pollId, otherUserId) } returns setOf(10L, newOptionId)

        val result = service.castVote(syncId, newOptionId, otherUserId)

        assertNotNull(result)
        assertTrue(result.userVotedOptionIds.contains(newOptionId))
        verify { pollRepository.castVote(pollId, newOptionId, otherUserId) }
    }

    @Test
    fun `castVote rejects vote on closed poll`() {
        val poll = openPoll().copy(closedAt = Instant.now())
        every { pollRepository.findById(syncId) } returns poll

        assertFailsWith<IllegalStateException> { service.castVote(syncId, 10L, otherUserId) }
    }

    @Test
    fun `castVote rejects vote past deadline`() {
        val poll = openPoll(deadline = Instant.now().minusSeconds(3600))
        every { pollRepository.findById(syncId) } returns poll

        assertFailsWith<IllegalStateException> { service.castVote(syncId, 10L, otherUserId) }
    }

    @Test
    fun `closePoll succeeds for creator`() {
        val poll = openPoll()
        every { pollRepository.findById(syncId) } returns poll

        service.closePoll(syncId, creatorId)

        verify { pollRepository.closePoll(syncId) }
    }

    @Test
    fun `closePoll rejects non-creator`() {
        val poll = openPoll()
        every { pollRepository.findById(syncId) } returns poll

        assertFailsWith<IllegalStateException> { service.closePoll(syncId, otherUserId) }
    }

    @Test
    fun `deletePoll succeeds for creator`() {
        val poll = openPoll()
        every { pollRepository.findById(syncId) } returns poll

        service.deletePoll(syncId, creatorId)

        verify { pollRepository.delete(syncId) }
    }

    @Test
    fun `getPoll returns null for non-existent poll`() {
        every { pollRepository.findById("nonexistent") } returns null

        val result = service.getPoll("nonexistent", creatorId)

        assertNull(result)
    }
}
