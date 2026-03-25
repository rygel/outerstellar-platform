package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityNotFoundException
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.CommunitySummary
import io.github.rygel.stellar.model.DuplicateCommunityException
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class CommunityService(
    private val communityRepo: CommunityRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val memberRepo: CommunityMemberRepository,
) {
    private val logger = LoggerFactory.getLogger(CommunityService::class.java)

    fun getCommunity(name: String): Community = communityRepo.findByName(name) ?: throw CommunityNotFoundException(name)

    fun getCommunityById(id: UUID): Community? = communityRepo.findById(id)

    fun createCommunity(name: String, displayName: String, description: String, creatorId: UUID): Community {
        val normalized = name.lowercase().trim()
        if (communityRepo.findByName(normalized) != null) {
            throw DuplicateCommunityException(normalized)
        }
        val community =
            communityRepo.create(
                Community(
                    id = UUID.randomUUID(),
                    name = normalized,
                    displayName = displayName.trim(),
                    description = description.trim(),
                    creatorId = creatorId,
                )
            )
        memberRepo.addMember(creatorId, community.id, CommunityRole.ADMIN)
        subscriptionRepo.subscribe(creatorId, community.id)
        communityRepo.incrementSubscriberCount(community.id, 1)
        logger.info("Community '{}' created by user {}", normalized, creatorId)
        return community
    }

    fun listPopular(limit: Int = 25, offset: Int = 0): List<CommunitySummary> = communityRepo.listPopular(limit, offset)

    fun search(query: String, limit: Int = 25, offset: Int = 0): List<CommunitySummary> =
        communityRepo.search(query, limit, offset)

    fun subscribe(userId: UUID, communityId: UUID) {
        if (!subscriptionRepo.isSubscribed(userId, communityId)) {
            subscriptionRepo.subscribe(userId, communityId)
            communityRepo.incrementSubscriberCount(communityId, 1)
        }
    }

    fun unsubscribe(userId: UUID, communityId: UUID) {
        if (subscriptionRepo.isSubscribed(userId, communityId)) {
            subscriptionRepo.unsubscribe(userId, communityId)
            communityRepo.incrementSubscriberCount(communityId, -1)
        }
    }

    fun isSubscribed(userId: UUID, communityId: UUID): Boolean = subscriptionRepo.isSubscribed(userId, communityId)

    fun getSubscribedCommunityIds(userId: UUID): List<UUID> = subscriptionRepo.findSubscribedCommunityIds(userId)
}
