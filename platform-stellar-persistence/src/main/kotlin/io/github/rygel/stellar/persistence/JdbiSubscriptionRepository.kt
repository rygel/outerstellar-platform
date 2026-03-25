package io.github.rygel.stellar.persistence

import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiSubscriptionRepository(private val jdbi: Jdbi) : SubscriptionRepository {

    override fun subscribe(userId: UUID, communityId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_subscriptions (user_id, community_id)
                VALUES (:userId, :communityId)
                """
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .execute()
        }
    }

    override fun unsubscribe(userId: UUID, communityId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM stl_subscriptions WHERE user_id = :userId AND community_id = :communityId")
                .bind("userId", userId)
                .bind("communityId", communityId)
                .execute()
        }
    }

    override fun isSubscribed(userId: UUID, communityId: UUID): Boolean =
        jdbi.withHandle<Boolean, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT COUNT(*) FROM stl_subscriptions
                WHERE user_id = :userId AND community_id = :communityId
                """
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .mapTo(Long::class.java)
                .one() > 0
        }

    override fun findSubscribedCommunityIds(userId: UUID): List<UUID> =
        jdbi.withHandle<List<UUID>, Exception> { handle ->
            handle
                .createQuery("SELECT community_id FROM stl_subscriptions WHERE user_id = :userId")
                .bind("userId", userId)
                .mapTo(UUID::class.java)
                .list()
        }

    override fun countSubscribers(communityId: UUID): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM stl_subscriptions WHERE community_id = :communityId")
                .bind("communityId", communityId)
                .mapTo(Long::class.java)
                .one()
        }
}
