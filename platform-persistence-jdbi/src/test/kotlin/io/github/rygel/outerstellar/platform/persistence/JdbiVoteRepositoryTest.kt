package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageVote
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiVoteRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiVoteRepository(jdbi) }

    private fun vote(
        messageSyncId: String = UUID.randomUUID().toString(),
        userId: UUID = createUser(),
        direction: Int = 1,
        createdAt: Instant = Instant.now(),
    ) = MessageVote(messageSyncId = messageSyncId, userId = userId, direction = direction, createdAt = createdAt)

    @Test
    fun `save and findByUserAndMessage round-trips correctly`() {
        val userId = createUser()
        val v = vote(messageSyncId = "msg-1", userId = userId, direction = 1)
        repo.save(v)

        val found = repo.findByUserAndMessage(userId, "msg-1")!!
        assertEquals("msg-1", found.messageSyncId)
        assertEquals(userId, found.userId)
        assertEquals(1, found.direction)
    }

    @Test
    fun `findByUserAndMessage returns null when no vote exists`() {
        val userId = createUser()
        assertNull(repo.findByUserAndMessage(userId, "missing"))
    }

    @Test
    fun `updateDirection changes an existing vote`() {
        val userId = createUser()
        repo.save(vote(messageSyncId = "msg-1", userId = userId, direction = 1))
        repo.updateDirection(userId, "msg-1", -1)

        assertEquals(-1, repo.findByUserAndMessage(userId, "msg-1")!!.direction)
    }

    @Test
    fun `delete removes a vote`() {
        val userId = createUser()
        repo.save(vote(messageSyncId = "msg-1", userId = userId))
        repo.delete(userId, "msg-1")

        assertNull(repo.findByUserAndMessage(userId, "msg-1"))
    }

    @Test
    fun `findScoresByMessages aggregates upvotes and downvotes across users`() {
        val alice = createUser()
        val bob = createUser()
        val carol = createUser()
        // msg-a: 2 up (alice, bob), 1 down (carol) -> net 1
        repo.save(vote("msg-a", alice, direction = 1))
        repo.save(vote("msg-a", bob, direction = 1))
        repo.save(vote("msg-a", carol, direction = -1))
        // msg-b: 1 down only -> net -1
        repo.save(vote("msg-b", alice, direction = -1))

        val scores = repo.findScoresByMessages(listOf("msg-a", "msg-b"), userId = null)

        assertEquals(2, scores.size)
        val scoreA = scores["msg-a"]!!
        assertEquals(2, scoreA.upvotes)
        assertEquals(1, scoreA.downvotes)
        assertEquals(1, scoreA.netScore)
        assertNull(scoreA.userVote)
        val scoreB = scores["msg-b"]!!
        assertEquals(0, scoreB.upvotes)
        assertEquals(1, scoreB.downvotes)
        assertEquals(-1, scoreB.netScore)
    }

    @Test
    fun `findScoresByMessages returns the requesting user's vote direction`() {
        val alice = createUser()
        val bob = createUser()
        repo.save(vote("msg-a", alice, direction = 1))
        repo.save(vote("msg-a", bob, direction = -1))

        // Alice requests: she upvoted.
        val fromAlice = repo.findScoresByMessages(listOf("msg-a"), userId = alice)
        assertEquals(1, fromAlice["msg-a"]!!.userVote)

        // Bob requests: he downvoted.
        val fromBob = repo.findScoresByMessages(listOf("msg-a"), userId = bob)
        assertEquals(-1, fromBob["msg-a"]!!.userVote)
    }

    @Test
    fun `findScoresByMessages includes messages with no votes as zero scores`() {
        val scores = repo.findScoresByMessages(listOf("msg-none"), userId = null)
        val score = scores["msg-none"]!!
        assertEquals(0, score.upvotes)
        assertEquals(0, score.downvotes)
        assertEquals(0, score.netScore)
        assertNull(score.userVote)
    }

    @Test
    fun `findScoresByMessages returns empty map for empty input list`() {
        // Regression guard for the empty-list case (issue #529): bindList must not receive an empty
        // list, which would produce invalid SQL. The early-return guard covers this; this test pins it.
        assertTrue(repo.findScoresByMessages(emptyList(), userId = null).isEmpty())
    }

    @Test
    fun `findScoreByMessage returns a single aggregated score`() {
        val alice = createUser()
        val bob = createUser()
        repo.save(vote("msg-a", alice, direction = 1))
        repo.save(vote("msg-a", bob, direction = 1))

        val score = repo.findScoreByMessage("msg-a", userId = alice)
        assertEquals(2, score.upvotes)
        assertEquals(0, score.downvotes)
        assertEquals(1, score.userVote)
    }
}
