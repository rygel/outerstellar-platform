package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityNotFoundException
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.CommunitySummary
import io.github.rygel.stellar.model.DuplicateCommunityException
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.StellarTransactionManager
import io.github.rygel.stellar.persistence.SubscriptionRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class CommunityService(
    private val communityRepo: CommunityRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val memberRepo: CommunityMemberRepository,
    private val cache: StellarCache? = null,
    private val txManager: StellarTransactionManager? = null,
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
            txManager?.inTransaction { doCreateCommunity(normalized, displayName, description, creatorId) }
                ?: doCreateCommunity(normalized, displayName, description, creatorId)
        cache?.invalidateByPrefix("communities:popular:")
        logger.info("Community '{}' created by user {}", normalized, creatorId)
        return community
    }

    private fun doCreateCommunity(name: String, displayName: String, description: String, creatorId: UUID): Community {
        val community =
            communityRepo.create(
                Community(
                    id = UUID.randomUUID(),
                    name = name,
                    displayName = displayName.trim(),
                    description = description.trim(),
                    creatorId = creatorId,
                )
            )
        memberRepo.addMember(creatorId, community.id, CommunityRole.ADMIN)
        subscriptionRepo.subscribe(creatorId, community.id)
        communityRepo.incrementSubscriberCount(community.id, 1)
        return community
    }

    fun updateCommunity(community: Community): Community {
        val updated = communityRepo.update(community)
        cache?.invalidateByPrefix("communities:popular:")
        logger.info("Community '{}' updated", community.name)
        return updated
    }

    fun listPopular(limit: Int = 25, offset: Int = 0): List<CommunitySummary> {
        val cacheKey = "communities:popular:$limit:$offset"
        cache?.get<List<CommunitySummary>>(cacheKey)?.let {
            return it
        }
        val result = communityRepo.listPopular(limit, offset)
        cache?.put(cacheKey, result)
        return result
    }

    fun search(query: String, limit: Int = 25, offset: Int = 0): List<CommunitySummary> =
        communityRepo.search(query, limit, offset)

    fun subscribe(userId: UUID, communityId: UUID) {
        val subscribed =
            txManager?.inTransaction { doSubscribe(userId, communityId) } ?: doSubscribe(userId, communityId)
        if (subscribed) {
            cache?.invalidateByPrefix("communities:popular:")
        }
    }

    private fun doSubscribe(userId: UUID, communityId: UUID): Boolean {
        if (!subscriptionRepo.isSubscribed(userId, communityId)) {
            subscriptionRepo.subscribe(userId, communityId)
            communityRepo.incrementSubscriberCount(communityId, 1)
            return true
        }
        return false
    }

    fun unsubscribe(userId: UUID, communityId: UUID) {
        val unsubscribed =
            txManager?.inTransaction { doUnsubscribe(userId, communityId) } ?: doUnsubscribe(userId, communityId)
        if (unsubscribed) {
            cache?.invalidateByPrefix("communities:popular:")
        }
    }

    private fun doUnsubscribe(userId: UUID, communityId: UUID): Boolean {
        if (subscriptionRepo.isSubscribed(userId, communityId)) {
            subscriptionRepo.unsubscribe(userId, communityId)
            communityRepo.incrementSubscriberCount(communityId, -1)
            return true
        }
        return false
    }

    fun isSubscribed(userId: UUID, communityId: UUID): Boolean = subscriptionRepo.isSubscribed(userId, communityId)

    fun getSubscribedCommunityIds(userId: UUID): List<UUID> = subscriptionRepo.findSubscribedCommunityIds(userId)
}
