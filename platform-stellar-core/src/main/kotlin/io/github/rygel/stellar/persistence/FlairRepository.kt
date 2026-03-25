package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import java.util.UUID

interface FlairRepository {
    fun findById(id: UUID): Flair?

    fun listByCommunity(communityId: UUID, type: FlairType? = null): List<Flair>

    fun create(flair: Flair): Flair

    fun delete(id: UUID)

    fun setUserFlair(userId: UUID, communityId: UUID, flairId: UUID)

    fun removeUserFlair(userId: UUID, communityId: UUID)

    fun getUserFlair(userId: UUID, communityId: UUID): Flair?
}
