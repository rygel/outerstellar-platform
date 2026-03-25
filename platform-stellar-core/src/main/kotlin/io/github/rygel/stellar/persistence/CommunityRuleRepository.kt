package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.CommunityRule
import java.util.UUID

interface CommunityRuleRepository {
    fun listByCommunity(communityId: UUID): List<CommunityRule>

    fun create(rule: CommunityRule): CommunityRule

    fun update(rule: CommunityRule): CommunityRule

    fun delete(id: UUID)

    fun reorder(communityId: UUID, ruleIds: List<UUID>)
}
