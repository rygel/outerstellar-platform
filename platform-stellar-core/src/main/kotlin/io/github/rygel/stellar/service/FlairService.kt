package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import io.github.rygel.stellar.persistence.FlairRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class FlairService(private val flairRepo: FlairRepository) {
    private val logger = LoggerFactory.getLogger(FlairService::class.java)

    fun listPostFlairs(communityId: UUID): List<Flair> = flairRepo.listByCommunity(communityId, FlairType.POST)

    fun listUserFlairs(communityId: UUID): List<Flair> = flairRepo.listByCommunity(communityId, FlairType.USER)

    fun createFlair(communityId: UUID, name: String, color: String, type: FlairType): Flair {
        val flair =
            Flair(
                id = UUID.randomUUID(),
                communityId = communityId,
                name = name.trim(),
                color = color.trim(),
                type = type,
            )
        val created = flairRepo.create(flair)
        logger.info("Created {} flair '{}' for community {}", type, name, communityId)
        return created
    }

    fun deleteFlair(id: UUID) {
        flairRepo.delete(id)
        logger.info("Deleted flair {}", id)
    }

    fun setUserFlair(userId: UUID, communityId: UUID, flairId: UUID) {
        flairRepo.setUserFlair(userId, communityId, flairId)
        logger.info("Set user flair {} for user {} in community {}", flairId, userId, communityId)
    }

    fun getUserFlair(userId: UUID, communityId: UUID): Flair? = flairRepo.getUserFlair(userId, communityId)
}
