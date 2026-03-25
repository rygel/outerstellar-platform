package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommunityServiceEdgeCasesTest {

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
    fun `createCommunity normalizes name to lowercase and trims whitespace`() {
        val creatorId = UUID.randomUUID()
        val communitySlot = slot<Community>()

        every { communityRepo.findByName("testcommunity") } returns null
        every { communityRepo.create(capture(communitySlot)) } answers { communitySlot.captured }
        every { memberRepo.addMember(creatorId, any(), CommunityRole.ADMIN) } just runs
        every { subscriptionRepo.subscribe(creatorId, any()) } just runs
        every { communityRepo.incrementSubscriberCount(any(), 1) } just runs

        service.createCommunity("  TestCommunity  ", "Test Community", "A description", creatorId)

        assertEquals("testcommunity", communitySlot.captured.name)
    }

    @Test
    fun `createCommunity trims displayName and description`() {
        val creatorId = UUID.randomUUID()
        val communitySlot = slot<Community>()

        every { communityRepo.findByName("test") } returns null
        every { communityRepo.create(capture(communitySlot)) } answers { communitySlot.captured }
        every { memberRepo.addMember(creatorId, any(), CommunityRole.ADMIN) } just runs
        every { subscriptionRepo.subscribe(creatorId, any()) } just runs
        every { communityRepo.incrementSubscriberCount(any(), 1) } just runs

        service.createCommunity("test", "  My Community  ", "  Some description  ", creatorId)

        assertEquals("My Community", communitySlot.captured.displayName)
        assertEquals("Some description", communitySlot.captured.description)
    }

    @Test
    fun `subscribe when already subscribed is a no-op`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns true

        service.subscribe(userId, communityId)

        verify(exactly = 0) { subscriptionRepo.subscribe(userId, communityId) }
        verify(exactly = 0) { communityRepo.incrementSubscriberCount(communityId, any()) }
    }

    @Test
    fun `unsubscribe when not subscribed is a no-op`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns false

        service.unsubscribe(userId, communityId)

        verify(exactly = 0) { subscriptionRepo.unsubscribe(userId, communityId) }
        verify(exactly = 0) { communityRepo.incrementSubscriberCount(communityId, any()) }
    }

    @Test
    fun `subscribe when not subscribed performs subscription and increments count`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns false
        every { subscriptionRepo.subscribe(userId, communityId) } just runs
        every { communityRepo.incrementSubscriberCount(communityId, 1) } just runs

        service.subscribe(userId, communityId)

        verify(exactly = 1) { subscriptionRepo.subscribe(userId, communityId) }
        verify(exactly = 1) { communityRepo.incrementSubscriberCount(communityId, 1) }
    }

    @Test
    fun `unsubscribe when subscribed performs unsubscription and decrements count`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()

        every { subscriptionRepo.isSubscribed(userId, communityId) } returns true
        every { subscriptionRepo.unsubscribe(userId, communityId) } just runs
        every { communityRepo.incrementSubscriberCount(communityId, -1) } just runs

        service.unsubscribe(userId, communityId)

        verify(exactly = 1) { subscriptionRepo.unsubscribe(userId, communityId) }
        verify(exactly = 1) { communityRepo.incrementSubscriberCount(communityId, -1) }
    }
}
