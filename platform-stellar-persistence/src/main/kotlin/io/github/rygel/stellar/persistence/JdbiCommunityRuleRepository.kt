package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.CommunityRule
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiCommunityRuleRepository(private val jdbi: Jdbi) : CommunityRuleRepository {

    override fun listByCommunity(communityId: UUID): List<CommunityRule> =
        jdbi.withHandle<List<CommunityRule>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT * FROM stl_community_rules
                    WHERE community_id = :communityId
                    ORDER BY position ASC
                    """
                )
                .bind("communityId", communityId)
                .map { rs, _ ->
                    CommunityRule(
                        id = rs.getObject("id", UUID::class.java),
                        communityId = rs.getObject("community_id", UUID::class.java),
                        title = rs.getString("title"),
                        description = rs.getString("description") ?: "",
                        position = rs.getInt("position"),
                    )
                }
                .list()
        }

    override fun create(rule: CommunityRule): CommunityRule {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO stl_community_rules (id, community_id, title, description, position)
                    VALUES (:id, :communityId, :title, :description, :position)
                    """
                )
                .bind("id", rule.id)
                .bind("communityId", rule.communityId)
                .bind("title", rule.title)
                .bind("description", rule.description)
                .bind("position", rule.position)
                .execute()
        }
        return rule
    }

    override fun update(rule: CommunityRule): CommunityRule {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE stl_community_rules
                    SET title = :title, description = :description, position = :position
                    WHERE id = :id
                    """
                )
                .bind("id", rule.id)
                .bind("title", rule.title)
                .bind("description", rule.description)
                .bind("position", rule.position)
                .execute()
        }
        return rule
    }

    override fun delete(id: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle.createUpdate("DELETE FROM stl_community_rules WHERE id = :id").bind("id", id).execute()
        }
    }

    override fun reorder(communityId: UUID, ruleIds: List<UUID>) {
        jdbi.useHandle<Exception> { handle ->
            ruleIds.forEachIndexed { index, ruleId ->
                handle
                    .createUpdate(
                        """
                        UPDATE stl_community_rules
                        SET position = :position
                        WHERE id = :id AND community_id = :communityId
                        """
                    )
                    .bind("position", index)
                    .bind("id", ruleId)
                    .bind("communityId", communityId)
                    .execute()
            }
        }
    }
}
