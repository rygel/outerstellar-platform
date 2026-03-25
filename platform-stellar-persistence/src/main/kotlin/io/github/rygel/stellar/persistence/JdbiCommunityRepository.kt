package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunitySummary
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiCommunityRepository(private val jdbi: Jdbi) : CommunityRepository {

    override fun findById(id: UUID): Community? =
        jdbi.withHandle<Community?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_communities WHERE id = :id")
                .bind("id", id)
                .map { rs, _ -> mapCommunity(rs) }
                .findOne()
                .orElse(null)
        }

    override fun findByName(name: String): Community? =
        jdbi.withHandle<Community?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_communities WHERE name = :name")
                .bind("name", name.lowercase())
                .map { rs, _ -> mapCommunity(rs) }
                .findOne()
                .orElse(null)
        }

    override fun create(community: Community): Community {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_communities (id, name, display_name, description, sidebar,
                    icon_url, banner_url, creator_id, is_nsfw, subscriber_count, created_at)
                VALUES (:id, :name, :displayName, :description, :sidebar,
                    :iconUrl, :bannerUrl, :creatorId, :isNsfw, :subscriberCount, :createdAt)
                """
                )
                .bind("id", community.id)
                .bind("name", community.name)
                .bind("displayName", community.displayName)
                .bind("description", community.description)
                .bind("sidebar", community.sidebar)
                .bind("iconUrl", community.iconUrl)
                .bind("bannerUrl", community.bannerUrl)
                .bind("creatorId", community.creatorId)
                .bind("isNsfw", community.isNsfw)
                .bind("subscriberCount", community.subscriberCount)
                .bind("createdAt", OffsetDateTime.ofInstant(community.createdAt, java.time.ZoneOffset.UTC))
                .execute()
        }
        return community
    }

    override fun update(community: Community): Community {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                UPDATE stl_communities
                SET display_name = :displayName, description = :description, sidebar = :sidebar,
                    icon_url = :iconUrl, banner_url = :bannerUrl, is_nsfw = :isNsfw
                WHERE id = :id
                """
                )
                .bind("id", community.id)
                .bind("displayName", community.displayName)
                .bind("description", community.description)
                .bind("sidebar", community.sidebar)
                .bind("iconUrl", community.iconUrl)
                .bind("bannerUrl", community.bannerUrl)
                .bind("isNsfw", community.isNsfw)
                .execute()
        }
        return community
    }

    override fun delete(id: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM stl_communities WHERE id = :id").bind("id", id).execute()
        }
    }

    override fun listPopular(limit: Int, offset: Int): List<CommunitySummary> =
        jdbi.withHandle<List<CommunitySummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT * FROM stl_communities
                ORDER BY subscriber_count DESC, created_at DESC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapCommunitySummary(rs) }
                .list()
        }

    /**
     * Searches communities by name and display name. Uses prefix match for short queries (< 3 chars) which can leverage
     * functional indexes, and contains match for longer queries.
     *
     * Note: For production PostgreSQL deployments, consider replacing this with full-text search (tsvector/tsquery) or
     * pg_trgm GIN indexes for significantly better performance.
     */
    override fun search(query: String, limit: Int, offset: Int): List<CommunitySummary> =
        jdbi.withHandle<List<CommunitySummary>, Exception> { handle ->
            val lowerQuery = query.lowercase()
            val likePattern = if (lowerQuery.length < 3) "$lowerQuery%" else "%$lowerQuery%"
            handle
                .createQuery(
                    """
                SELECT * FROM stl_communities
                WHERE LOWER(name) LIKE :q OR LOWER(display_name) LIKE :q
                ORDER BY subscriber_count DESC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("q", likePattern)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapCommunitySummary(rs) }
                .list()
        }

    override fun countAll(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle.createQuery("SELECT COUNT(*) FROM stl_communities").mapTo(Long::class.java).one()
        }

    override fun findSubscribed(userId: UUID, limit: Int, offset: Int): List<CommunitySummary> =
        jdbi.withHandle<List<CommunitySummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT c.* FROM stl_communities c
                JOIN stl_subscriptions s ON s.community_id = c.id
                WHERE s.user_id = :userId
                ORDER BY c.name ASC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapCommunitySummary(rs) }
                .list()
        }

    override fun incrementSubscriberCount(communityId: UUID, delta: Int) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_communities SET subscriber_count = subscriber_count + :delta WHERE id = :id")
                .bind("id", communityId)
                .bind("delta", delta)
                .execute()
        }
    }

    private fun mapCommunity(rs: ResultSet): Community =
        Community(
            id = rs.getObject("id", UUID::class.java),
            name = rs.getString("name"),
            displayName = rs.getString("display_name"),
            description = rs.getString("description") ?: "",
            sidebar = rs.getString("sidebar") ?: "",
            iconUrl = rs.getString("icon_url"),
            bannerUrl = rs.getString("banner_url"),
            creatorId = rs.getObject("creator_id", UUID::class.java),
            isNsfw = rs.getBoolean("is_nsfw"),
            subscriberCount = rs.getLong("subscriber_count"),
            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
        )

    private fun mapCommunitySummary(rs: ResultSet): CommunitySummary =
        CommunitySummary(
            id = rs.getObject("id", UUID::class.java),
            name = rs.getString("name"),
            displayName = rs.getString("display_name"),
            description = rs.getString("description") ?: "",
            iconUrl = rs.getString("icon_url"),
            subscriberCount = rs.getLong("subscriber_count"),
        )
}
