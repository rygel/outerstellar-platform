package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiSubscriptionRepositoryTest : H2StellarTest() {

    private lateinit var repo: JdbiSubscriptionRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var userId: UUID
    private lateinit var communityId: UUID

    @BeforeEach
    fun setUp() {
        repo = JdbiSubscriptionRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("subscriber")
        communityId = UUID.randomUUID()
        communityRepo.create(
            Community(id = communityId, name = "subcommunity", displayName = "Sub Community", creatorId = userId)
        )
    }

    private fun createCommunity(name: String): UUID {
        val id = UUID.randomUUID()
        communityRepo.create(Community(id = id, name = name, displayName = "Community $name", creatorId = userId))
        return id
    }

    @Test
    fun `subscribe and isSubscribed`() {
        assertFalse(repo.isSubscribed(userId, communityId))

        repo.subscribe(userId, communityId)

        assertTrue(repo.isSubscribed(userId, communityId))
    }

    @Test
    fun `unsubscribe`() {
        repo.subscribe(userId, communityId)
        assertTrue(repo.isSubscribed(userId, communityId))

        repo.unsubscribe(userId, communityId)

        assertFalse(repo.isSubscribed(userId, communityId))
    }

    @Test
    fun `findSubscribedCommunityIds`() {
        val community2Id = createCommunity("community2")
        val community3Id = createCommunity("community3")

        repo.subscribe(userId, communityId)
        repo.subscribe(userId, community2Id)

        val subscribedIds = repo.findSubscribedCommunityIds(userId)
        assertEquals(2, subscribedIds.size)
        assertTrue(subscribedIds.contains(communityId))
        assertTrue(subscribedIds.contains(community2Id))
        assertFalse(subscribedIds.contains(community3Id))
    }

    @Test
    fun `countSubscribers`() {
        assertEquals(0, repo.countSubscribers(communityId))

        val user2 = createTestUser("subscriber2")
        val user3 = createTestUser("subscriber3")

        repo.subscribe(userId, communityId)
        repo.subscribe(user2, communityId)
        repo.subscribe(user3, communityId)

        assertEquals(3, repo.countSubscribers(communityId))
    }
}
