package io.github.rygel.stellar.persistence

import java.util.UUID

interface SubscriptionRepository {
    fun subscribe(userId: UUID, communityId: UUID)

    fun unsubscribe(userId: UUID, communityId: UUID)

    fun isSubscribed(userId: UUID, communityId: UUID): Boolean

    fun findSubscribedCommunityIds(userId: UUID): List<UUID>

    fun countSubscribers(communityId: UUID): Long
}
