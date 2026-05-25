package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.DeviceToken
import java.util.UUID

interface DeviceTokenRepository {
    fun upsert(deviceToken: DeviceToken)

    fun delete(token: String)

    fun deleteByTokenAndUserId(token: String, userId: UUID): Boolean

    fun findByUserId(userId: UUID): List<DeviceToken>

    fun deleteAllForUser(userId: UUID)
}
