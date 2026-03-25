package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunitySummary
import java.util.UUID

interface CommunityRepository {
    fun findById(id: UUID): Community?

    fun findByName(name: String): Community?

    fun create(community: Community): Community

    fun update(community: Community): Community

    fun delete(id: UUID)

    fun listPopular(limit: Int = 25, offset: Int = 0): List<CommunitySummary>

    fun search(query: String, limit: Int = 25, offset: Int = 0): List<CommunitySummary>

    fun countAll(): Long

    fun findSubscribed(userId: UUID, limit: Int = 100, offset: Int = 0): List<CommunitySummary>

    fun incrementSubscriberCount(communityId: UUID, delta: Int)
}
