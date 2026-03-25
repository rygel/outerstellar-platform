package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.CommunityBan
import io.github.rygel.stellar.model.CommunityMember
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.Report
import io.github.rygel.stellar.model.ReportStatus
import io.github.rygel.stellar.model.SavedItem
import io.github.rygel.stellar.model.SavedItemType
import io.github.rygel.stellar.model.UserKarma
import java.util.UUID

interface CommunityMemberRepository {
    fun findMembership(userId: UUID, communityId: UUID): CommunityMember?

    fun addMember(userId: UUID, communityId: UUID, role: CommunityRole)

    fun removeMember(userId: UUID, communityId: UUID)

    fun listModerators(communityId: UUID): List<CommunityMember>

    fun isBanned(userId: UUID, communityId: UUID): Boolean

    fun ban(ban: CommunityBan)

    fun unban(userId: UUID, communityId: UUID)
}

interface ReportRepository {
    fun create(report: Report): Report

    fun findById(id: UUID): Report?

    fun listByCommunity(communityId: UUID, status: ReportStatus? = null, limit: Int = 50): List<Report>

    fun updateStatus(reportId: UUID, status: ReportStatus, resolvedBy: UUID?)
}

interface SavedItemRepository {
    fun save(userId: UUID, targetId: UUID, targetType: SavedItemType)

    fun unsave(userId: UUID, targetId: UUID, targetType: SavedItemType)

    fun isSaved(userId: UUID, targetId: UUID, targetType: SavedItemType): Boolean

    fun listSaved(userId: UUID, limit: Int = 25, offset: Int = 0): List<SavedItem>

    fun findSavedIds(userId: UUID, targetIds: List<UUID>, targetType: SavedItemType): Set<UUID>
}

interface KarmaRepository {
    fun getKarma(userId: UUID): UserKarma

    fun updatePostKarma(userId: UUID, delta: Int)

    fun updateCommentKarma(userId: UUID, delta: Int)

    fun ensureExists(userId: UUID)
}
