package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ApiKey
import java.util.UUID

interface ApiKeyRepository {
    fun save(apiKey: ApiKey)

    fun findByKeyHash(keyHash: String): ApiKey?

    fun findByUserId(userId: UUID): List<ApiKey>

    fun delete(id: Long, userId: UUID)

    fun updateLastUsed(id: Long)
}
