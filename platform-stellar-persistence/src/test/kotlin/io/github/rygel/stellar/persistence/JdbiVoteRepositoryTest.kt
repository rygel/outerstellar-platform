package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Vote
import io.github.rygel.stellar.model.VoteTarget
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiVoteRepositoryTest : H2StellarTest() {

    private lateinit var repo: JdbiVoteRepository
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        repo = JdbiVoteRepository(jdbi)
        userId = createTestUser("voter")
    }

    @Test
    fun `upsert creates new vote`() {
        val targetId = UUID.randomUUID()
        val vote = Vote(userId = userId, targetId = targetId, targetType = VoteTarget.POST, value = 1)
        repo.upsert(vote)

        val found = repo.findVote(userId, targetId, VoteTarget.POST)
        assertNotNull(found)
        assertEquals(1, found.value)
        assertEquals(userId, found.userId)
        assertEquals(targetId, found.targetId)
        assertEquals(VoteTarget.POST, found.targetType)
    }

    @Test
    fun `findVote returns existing`() {
        val targetId = UUID.randomUUID()
        repo.upsert(Vote(userId = userId, targetId = targetId, targetType = VoteTarget.COMMENT, value = -1))

        val found = repo.findVote(userId, targetId, VoteTarget.COMMENT)
        assertNotNull(found)
        assertEquals(-1, found.value)
    }

    @Test
    fun `findVote returns null for nonexistent`() {
        val found = repo.findVote(userId, UUID.randomUUID(), VoteTarget.POST)
        assertNull(found)
    }

    @Test
    fun `upsert updates existing vote value`() {
        val targetId = UUID.randomUUID()
        repo.upsert(Vote(userId = userId, targetId = targetId, targetType = VoteTarget.POST, value = 1))

        repo.upsert(Vote(userId = userId, targetId = targetId, targetType = VoteTarget.POST, value = -1))

        val found = repo.findVote(userId, targetId, VoteTarget.POST)
        assertNotNull(found)
        assertEquals(-1, found.value)
    }

    @Test
    fun `delete removes vote`() {
        val targetId = UUID.randomUUID()
        repo.upsert(Vote(userId = userId, targetId = targetId, targetType = VoteTarget.POST, value = 1))
        assertNotNull(repo.findVote(userId, targetId, VoteTarget.POST))

        repo.delete(userId, targetId, VoteTarget.POST)
        assertNull(repo.findVote(userId, targetId, VoteTarget.POST))
    }

    @Test
    fun `findUserVotes returns map of target to value`() {
        val target1 = UUID.randomUUID()
        val target2 = UUID.randomUUID()
        val target3 = UUID.randomUUID()
        repo.upsert(Vote(userId = userId, targetId = target1, targetType = VoteTarget.POST, value = 1))
        repo.upsert(Vote(userId = userId, targetId = target2, targetType = VoteTarget.POST, value = -1))

        val votes = repo.findUserVotes(userId, listOf(target1, target2, target3), VoteTarget.POST)
        assertEquals(2, votes.size)
        assertEquals(1, votes[target1])
        assertEquals(-1, votes[target2])
        assertNull(votes[target3])
    }

    @Test
    fun `findUserVotes with empty list returns empty map`() {
        val votes = repo.findUserVotes(userId, emptyList(), VoteTarget.POST)
        assertTrue(votes.isEmpty())
    }
}
