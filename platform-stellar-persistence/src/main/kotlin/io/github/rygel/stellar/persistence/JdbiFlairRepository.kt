package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import java.sql.ResultSet
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiFlairRepository(private val jdbi: Jdbi) : FlairRepository {

    override fun findById(id: UUID): Flair? =
        jdbi.withHandle<Flair?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_flairs WHERE id = :id")
                .bind("id", id)
                .map { rs, _ -> mapFlair(rs) }
                .findOne()
                .orElse(null)
        }

    override fun listByCommunity(communityId: UUID, type: FlairType?): List<Flair> =
        jdbi.withHandle<List<Flair>, Exception> { handle ->
            if (type != null) {
                handle
                    .createQuery(
                        "SELECT * FROM stl_flairs WHERE community_id = :communityId AND type = :type ORDER BY name"
                    )
                    .bind("communityId", communityId)
                    .bind("type", type.name)
                    .map { rs, _ -> mapFlair(rs) }
                    .list()
            } else {
                handle
                    .createQuery("SELECT * FROM stl_flairs WHERE community_id = :communityId ORDER BY name")
                    .bind("communityId", communityId)
                    .map { rs, _ -> mapFlair(rs) }
                    .list()
            }
        }

    override fun create(flair: Flair): Flair {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO stl_flairs (id, community_id, name, color, type)
                    VALUES (:id, :communityId, :name, :color, :type)
                    """
                )
                .bind("id", flair.id)
                .bind("communityId", flair.communityId)
                .bind("name", flair.name)
                .bind("color", flair.color)
                .bind("type", flair.type.name)
                .execute()
        }
        return flair
    }

    override fun delete(id: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM stl_flairs WHERE id = :id").bind("id", id).execute()
        }
    }

    override fun setUserFlair(userId: UUID, communityId: UUID, flairId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            // Try update first, then insert
            val updated =
                handle
                    .createUpdate(
                        """
                        UPDATE stl_user_flairs SET flair_id = :flairId
                        WHERE user_id = :userId AND community_id = :communityId
                        """
                    )
                    .bind("userId", userId)
                    .bind("communityId", communityId)
                    .bind("flairId", flairId)
                    .execute()

            if (updated == 0) {
                handle
                    .createUpdate(
                        """
                        INSERT INTO stl_user_flairs (user_id, community_id, flair_id)
                        VALUES (:userId, :communityId, :flairId)
                        """
                    )
                    .bind("userId", userId)
                    .bind("communityId", communityId)
                    .bind("flairId", flairId)
                    .execute()
            }
        }
    }

    override fun removeUserFlair(userId: UUID, communityId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM stl_user_flairs WHERE user_id = :userId AND community_id = :communityId")
                .bind("userId", userId)
                .bind("communityId", communityId)
                .execute()
        }
    }

    override fun getUserFlair(userId: UUID, communityId: UUID): Flair? =
        jdbi.withHandle<Flair?, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT f.* FROM stl_flairs f
                    JOIN stl_user_flairs uf ON uf.flair_id = f.id
                    WHERE uf.user_id = :userId AND uf.community_id = :communityId
                    """
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .map { rs, _ -> mapFlair(rs) }
                .findOne()
                .orElse(null)
        }

    private fun mapFlair(rs: ResultSet): Flair =
        Flair(
            id = rs.getObject("id", UUID::class.java),
            communityId = rs.getObject("community_id", UUID::class.java),
            name = rs.getString("name"),
            color = rs.getString("color"),
            type = FlairType.valueOf(rs.getString("type")),
        )
}
