package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Poll
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jooq.impl.DSL

class JooqPollRepositoryTest : JooqTest() {

    private val repo by lazy { JooqPollRepository(dsl) }

    private val testUserId = UUID.randomUUID()
    private val testUserId2 = UUID.randomUUID()

    @org.junit.jupiter.api.BeforeEach
    fun setupUser() {
        dsl.insertInto(DSL.table("plt_users"))
            .set(DSL.field("id"), testUserId)
            .set(DSL.field("username"), "poll_test_user")
            .set(DSL.field("email"), "poll@test.com")
            .set(DSL.field("password_hash"), "testhash")
            .set(DSL.field("role"), "USER")
            .set(DSL.field("enabled"), true)
            .set(DSL.field("created_at"), Instant.now())
            .execute()

        dsl.insertInto(DSL.table("plt_users"))
            .set(DSL.field("id"), testUserId2)
            .set(DSL.field("username"), "poll_test_user2")
            .set(DSL.field("email"), "poll2@test.com")
            .set(DSL.field("password_hash"), "testhash")
            .set(DSL.field("role"), "USER")
            .set(DSL.field("enabled"), true)
            .set(DSL.field("created_at"), Instant.now())
            .execute()
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
        val created = createTestPoll()
        val found = repo.findById(created.syncId)!!

        assertNotNull(found)
        assertEquals(created.syncId, found.syncId)
        assertEquals(testUserId, found.creatorId)
        assertEquals("What is your favorite color?", found.question)
        assertNull(found.closedAt)

        val options = repo.findOptions(created.id)
        assertEquals(3, options.size)
        assertEquals("Red", options[0].optionText)
        assertEquals("Green", options[1].optionText)
        assertEquals("Blue", options[2].optionText)
        assertEquals(0, options[0].position)
        assertEquals(1, options[1].position)
        assertEquals(2, options[2].position)
    }

    @Test
    fun `findOptions returns options in position order`() {
        val created = createTestPoll(options = listOf("Zebra", "Apple", "Middle"))
        val options = repo.findOptions(created.id)

        assertEquals(3, options.size)
        assertEquals(0, options[0].position)
        assertEquals(1, options[1].position)
        assertEquals(2, options[2].position)
        assertEquals("Zebra", options[0].optionText)
        assertEquals("Apple", options[1].optionText)
        assertEquals("Middle", options[2].optionText)
    }

    @Test
    fun `findById returns null for non-existent poll`() {
        assertNull(repo.findById("nonexistent-sync-id"))
    }

    @Test
    fun `castVote and getUserVotes round-trip`() {
        val created = createTestPoll()
        val options = repo.findOptions(created.id)
        val optionId = options[1].id

        repo.castVote(created.id, optionId, testUserId)
        val votes = repo.getUserVotes(created.id, testUserId)

        assertEquals(1, votes.size)
        assertTrue(votes.contains(optionId))
    }

    @Test
    fun `getVoteCounts returns correct counts`() {
        val created = createTestPoll(multiChoice = true)
        val options = repo.findOptions(created.id)

        repo.castVote(created.id, options[0].id, testUserId)
        repo.castVote(created.id, options[1].id, testUserId)
        repo.castVote(created.id, options[0].id, testUserId2)

        val counts = repo.getVoteCounts(created.id)

        assertEquals(2, counts[options[0].id])
        assertEquals(1, counts[options[1].id])
    }

    @Test
    fun `removeVote removes the vote`() {
        val created = createTestPoll()
        val options = repo.findOptions(created.id)
        val optionId = options[0].id

        repo.castVote(created.id, optionId, testUserId)
        assertEquals(setOf(optionId), repo.getUserVotes(created.id, testUserId))

        repo.removeVote(created.id, optionId, testUserId)
        assertTrue(repo.getUserVotes(created.id, testUserId).isEmpty())
    }

    @Test
    fun `closePoll sets closedAt`() {
        val created = createTestPoll()
        assertNull(repo.findById(created.syncId)!!.closedAt)

        repo.closePoll(created.syncId)

        val closed = repo.findById(created.syncId)!!
        assertNotNull(closed.closedAt)
        assertTrue(closed.closedAt!!.isBefore(Instant.now().plusSeconds(1)))
    }

    @Test
    fun `delete removes poll and cascades`() {
        val created = createTestPoll()
        val options = repo.findOptions(created.id)
        assertTrue(options.isNotEmpty())

        repo.delete(created.syncId)

        assertNull(repo.findById(created.syncId))
        assertTrue(repo.findOptions(created.id).isEmpty())
    }

    @Test
    fun `listOpen returns only open polls`() {
        createTestPoll(syncId = "open-poll-1")
        createTestPoll(syncId = "closed-poll-1")
        repo.closePoll("closed-poll-1")

        val openPolls = repo.listOpen(10, 0)

        assertEquals(1, openPolls.size)
        assertEquals("open-poll-1", openPolls[0].syncId)
    }

    @Test
    fun `listByCreator returns only creator polls`() {
        createTestPoll(syncId = "user1-poll", creatorId = testUserId)
        createTestPoll(syncId = "user2-poll", creatorId = testUserId2)

        val user1Polls = repo.listByCreator(testUserId, 10, 0)

        assertEquals(1, user1Polls.size)
        assertEquals("user1-poll", user1Polls[0].syncId)
    }

    @Test
    fun `findOptionById returns correct option`() {
        val created = createTestPoll()
        val options = repo.findOptions(created.id)
        val targetOption = options[1]

        val found = repo.findOptionById(targetOption.id)

        assertNotNull(found)
        assertEquals(targetOption.id, found.id)
        assertEquals(targetOption.optionText, found.optionText)
        assertEquals(created.id, found.pollId)
    }
}
