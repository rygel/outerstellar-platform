package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityNotFoundException
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.DuplicateCommunityException
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CommunityServiceTest {

    private lateinit var communityRepo: CommunityRepository
    private lateinit var subscriptionRepo: SubscriptionRepository
    private lateinit var memberRepo: CommunityMemberRepository
    private lateinit var service: CommunityService

    @BeforeEach
    fun setUp() {
        communityRepo = mockk()
        subscriptionRepo = mockk()
        memberRepo = mockk()
        service = CommunityService(communityRepo, subscriptionRepo, memberRepo)
    }

    @Test
    fun `getCommunity returns community when found`() {
        val community = community(name = "kotlin")
        every { communityRepo.findByName("kotlin") } returns community

        val result = service.getCommunity("kotlin")

        assertEquals("kotlin", result.name)
    }

    @Test
    fun `getCommunity throws CommunityNotFoundException when not found`() {
        every { communityRepo.findByName("nonexistent") } returns null

        assertThrows<CommunityNotFoundException> { service.getCommunity("nonexistent") }
    }

    @Test
    fun `createCommunity succeeds and sets up creator as admin and subscriber`() {
        val creatorId = UUID.randomUUID()
        val community = community(name = "newcommunity", creatorId = creatorId)

        every { communityRepo.findByName("newcommunity") } returns null
        every { communityRepo.create(any()) } returns community
        every { memberRepo.addMember(creatorId, community.id, CommunityRole.ADMIN) } just runs
        every { subscriptionRepo.subscribe(creatorId, community.id) } just runs
        every { communityRepo.incrementSubscriberCount(community.id, 1) } just runs

        val result = service.createCommunity("NewCommunity", "New Community", "A description", creatorId)

        assertNotNull(result)
        assertEquals("newcommunity", result.name)
        verify { memberRepo.addMember(creatorId, community.id, CommunityRole.ADMIN) }
        verify { subscriptionRepo.subscribe(creatorId, community.id) }
        verify { communityRepo.incrementSubscriberCount(community.id, 1) }
    }

    @Test
    fun `createCommunity throws DuplicateCommunityException when name already exists`() {
        val existing = community(name = "existing")
        every { communityRepo.findByName("existing") } returns existing

        assertThrows<DuplicateCommunityException> {
            service.createCommunity("Existing", "Existing", "desc", UUID.randomUUID())
        }
    }

    @Test
    fun `subscribe subscribes user when not already subscribed`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns false
        every { subscriptionRepo.subscribe(userId, communityId) } just runs
        every { communityRepo.incrementSubscriberCount(communityId, 1) } just runs

        service.subscribe(userId, communityId)

        verify { subscriptionRepo.subscribe(userId, communityId) }
        verify { communityRepo.incrementSubscriberCount(communityId, 1) }
    }

    @Test
    fun `subscribe does nothing when already subscribed`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns true

        service.subscribe(userId, communityId)

        verify(exactly = 0) { subscriptionRepo.subscribe(any(), any()) }
        verify(exactly = 0) { communityRepo.incrementSubscriberCount(any(), any()) }
    }

    @Test
    fun `unsubscribe removes subscription when currently subscribed`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns true
        every { subscriptionRepo.unsubscribe(userId, communityId) } just runs
        every { communityRepo.incrementSubscriberCount(communityId, -1) } just runs

        service.unsubscribe(userId, communityId)

        verify { subscriptionRepo.unsubscribe(userId, communityId) }
        verify { communityRepo.incrementSubscriberCount(communityId, -1) }
    }

    @Test
    fun `unsubscribe does nothing when not subscribed`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns false

        service.unsubscribe(userId, communityId)

        verify(exactly = 0) { subscriptionRepo.unsubscribe(any(), any()) }
        verify(exactly = 0) { communityRepo.incrementSubscriberCount(any(), any()) }
    }

    private fun community(
        id: UUID = UUID.randomUUID(),
        name: String = "test",
        displayName: String = name,
        creatorId: UUID = UUID.randomUUID(),
    ) = Community(id = id, name = name, displayName = displayName, description = "desc", creatorId = creatorId)
}
