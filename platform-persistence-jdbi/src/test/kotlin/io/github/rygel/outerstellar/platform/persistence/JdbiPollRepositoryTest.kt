package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Poll
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach

class JdbiPollRepositoryTest : JdbiTest() {

    private val repo by lazy { JdbiPollRepository(jdbi) }

    private val testUserId = UUID.randomUUID()

    @BeforeEach
    fun setupUser() {
        jdbi.useHandle<Exception> { handle ->
            handle.execute(
                "INSERT INTO plt_users (id, username, email, password_hash, role, enabled, created_at) VALUES (?, ?, ?, ?, 'USER', true, ?)",
                testUserId,
                "poll_test_user",
                "poll@test.com",
                "testhash",
                Instant.now(),
            )
        }
    }

    private fun createTestPoll(
        syncId: String = UUID.randomUUID().toString(),
        creatorId: UUID = testUserId,
        question: String = "What is your favorite color?",
        options: List<String> = listOf("Red", "Green", "Blue"),
        multiChoice: Boolean = false,
    ): Poll {
        val poll = Poll(syncId = syncId, creatorId = creatorId, question = question, multiChoice = multiChoice)
        return repo.create(poll, options)
    }

    @Test
    fun `create poll and findById round-trips correctly`() {
        val poll = createTestPoll()
        val found = repo.findById(poll.syncId)
        assertNotNull(found)
        assertEquals(poll.syncId, found.syncId)
        assertEquals(testUserId, found.creatorId)
        assertEquals("What is your favorite color?", found.question)
        assertNull(found.closedAt)

        val options = repo.findOptions(poll.id)
        assertEquals(3, options.size)
        assertEquals(0, options[0].position)
        assertEquals("Red", options[0].optionText)
        assertEquals(1, options[1].position)
        assertEquals("Green", options[1].optionText)
        assertEquals(2, options[2].position)
        assertEquals("Blue", options[2].optionText)
    }

    @Test
    fun `findOptions returns options in position order`() {
        val poll = createTestPoll(options = listOf("First", "Second", "Third", "Fourth"))
        val options = repo.findOptions(poll.id)
        assertEquals(4, options.size)
        assertEquals(listOf(0, 1, 2, 3), options.map { it.position })
        assertEquals(listOf("First", "Second", "Third", "Fourth"), options.map { it.optionText })
    }

    @Test
    fun `findById returns null for non-existent poll`() {
        assertNull(repo.findById("nonexistent-sync-id"))
    }

    @Test
    fun `castVote and getUserVotes round-trip`() {
        val poll = createTestPoll()
        val options = repo.findOptions(poll.id)
        repo.castVote(poll.id, options[1].id, testUserId)
        val votes = repo.getUserVotes(poll.id, testUserId)
        assertEquals(1, votes.size)
        assertTrue(votes.contains(options[1].id))
    }

    @Test
    fun `getVoteCounts returns correct counts`() {
        val poll = createTestPoll(multiChoice = true)
        val options = repo.findOptions(poll.id)
        val user2 = UUID.randomUUID()
        val user3 = UUID.randomUUID()
        createAdditionalUser(user2, "voter2")
        createAdditionalUser(user3, "voter3")

        repo.castVote(poll.id, options[0].id, testUserId)
        repo.castVote(poll.id, options[0].id, user2)
        repo.castVote(poll.id, options[1].id, user3)

        val counts = repo.getVoteCounts(poll.id)
        assertEquals(2, counts[options[0].id])
        assertEquals(1, counts[options[1].id])
    }

    @Test
    fun `removeVote removes the vote`() {
        val poll = createTestPoll()
        val options = repo.findOptions(poll.id)
        repo.castVote(poll.id, options[0].id, testUserId)
        assertEquals(1, repo.getUserVotes(poll.id, testUserId).size)

        repo.removeVote(poll.id, options[0].id, testUserId)
        assertTrue(repo.getUserVotes(poll.id, testUserId).isEmpty())
    }

    @Test
    fun `closePoll sets closedAt`() {
        val poll = createTestPoll()
        assertNull(repo.findById(poll.syncId)!!.closedAt)

        repo.closePoll(poll.syncId)

        val closed = repo.findById(poll.syncId)!!
        assertNotNull(closed.closedAt)
        assertTrue(closed.closedAt!!.isBefore(Instant.now().plusSeconds(1)))
    }

    @Test
    fun `delete removes poll and cascades`() {
        val poll = createTestPoll()
        assertNotNull(repo.findById(poll.syncId))
        assertTrue(repo.findOptions(poll.id).isNotEmpty())

        repo.delete(poll.syncId)

        assertNull(repo.findById(poll.syncId))
        assertTrue(repo.findOptions(poll.id).isEmpty())
    }

    @Test
    fun `listOpen returns only open polls`() {
        createTestPoll(syncId = "open-poll-1")
        createTestPoll(syncId = "closed-poll-1")
        repo.closePoll("closed-poll-1")

        val open = repo.listOpen(10, 0)
        assertEquals(1, open.size)
        assertEquals("open-poll-1", open[0].syncId)
    }

    @Test
    fun `listByCreator returns only creator's polls`() {
        val otherUserId = UUID.randomUUID()
        createAdditionalUser(otherUserId, "other_creator")

        createTestPoll(syncId = "user1-poll-1", creatorId = testUserId)
        createTestPoll(syncId = "user1-poll-2", creatorId = testUserId)
        createTestPoll(syncId = "user2-poll-1", creatorId = otherUserId)

        val byCreator = repo.listByCreator(testUserId, 10, 0)
        assertEquals(2, byCreator.size)
        assertTrue(byCreator.all { it.creatorId == testUserId })
    }

    @Test
    fun `findOptionById returns correct option`() {
        val poll = createTestPoll()
        val options = repo.findOptions(poll.id)
        val target = options[1]

        val found = repo.findOptionById(target.id)
        assertNotNull(found)
        assertEquals(target.id, found.id)
        assertEquals(target.pollId, found.pollId)
        assertEquals(target.position, found.position)
        assertEquals(target.optionText, found.optionText)
    }

    private fun createAdditionalUser(userId: UUID, username: String) {
        jdbi.useHandle<Exception> { handle ->
            handle.execute(
                "INSERT INTO plt_users (id, username, email, password_hash, role, enabled, created_at) VALUES (?, ?, ?, ?, 'USER', true, ?)",
                userId,
                username,
                "$username@test.com",
                "testhash",
                Instant.now(),
            )
        }
    }
}
