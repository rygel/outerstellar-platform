package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.CommunityBan
import io.github.rygel.stellar.model.CommunityMember
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.Report
import io.github.rygel.stellar.model.ReportStatus
import io.github.rygel.stellar.model.ReportTarget
import io.github.rygel.stellar.model.SavedItem
import io.github.rygel.stellar.model.SavedItemType
import io.github.rygel.stellar.model.UserKarma
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiCommunityMemberRepository(private val jdbi: Jdbi) : CommunityMemberRepository {

    override fun findMembership(userId: UUID, communityId: UUID): CommunityMember? =
        jdbi.withHandle<CommunityMember?, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT * FROM stl_community_members WHERE user_id = :userId AND community_id = :communityId"
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .map { rs, _ ->
                    CommunityMember(
                        userId = rs.getObject("user_id", UUID::class.java),
                        communityId = rs.getObject("community_id", UUID::class.java),
                        role = CommunityRole.valueOf(rs.getString("role")),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .findOne()
                .orElse(null)
        }

    override fun addMember(userId: UUID, communityId: UUID, role: CommunityRole) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_community_members (user_id, community_id, role)
                VALUES (:userId, :communityId, :role)
                """
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .bind("role", role.name)
                .execute()
        }
    }

    override fun removeMember(userId: UUID, communityId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "DELETE FROM stl_community_members WHERE user_id = :userId AND community_id = :communityId"
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .execute()
        }
    }

    override fun listModerators(communityId: UUID): List<CommunityMember> =
        jdbi.withHandle<List<CommunityMember>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT * FROM stl_community_members
                WHERE community_id = :communityId AND role IN ('MODERATOR', 'ADMIN')
                """
                )
                .bind("communityId", communityId)
                .map { rs, _ ->
                    CommunityMember(
                        userId = rs.getObject("user_id", UUID::class.java),
                        communityId = rs.getObject("community_id", UUID::class.java),
                        role = CommunityRole.valueOf(rs.getString("role")),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .list()
        }

    override fun isBanned(userId: UUID, communityId: UUID): Boolean =
        jdbi.withHandle<Boolean, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT COUNT(*) FROM stl_community_bans WHERE user_id = :userId AND community_id = :communityId"
                )
                .bind("userId", userId)
                .bind("communityId", communityId)
                .mapTo(Long::class.java)
                .one() > 0
        }

    override fun ban(ban: CommunityBan) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_community_bans (user_id, community_id, reason, banned_by, created_at)
                VALUES (:userId, :communityId, :reason, :bannedBy, :createdAt)
                """
                )
                .bind("userId", ban.userId)
                .bind("communityId", ban.communityId)
                .bind("reason", ban.reason)
                .bind("bannedBy", ban.bannedBy)
                .bind("createdAt", OffsetDateTime.ofInstant(ban.createdAt, ZoneOffset.UTC))
                .execute()
        }
    }

    override fun unban(userId: UUID, communityId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM stl_community_bans WHERE user_id = :userId AND community_id = :communityId")
                .bind("userId", userId)
                .bind("communityId", communityId)
                .execute()
        }
    }
}

class JdbiReportRepository(private val jdbi: Jdbi) : ReportRepository {

    override fun create(report: Report): Report {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_reports (id, reporter_id, target_id, target_type, reason, status, created_at)
                VALUES (:id, :reporterId, :targetId, :targetType, :reason, :status, :createdAt)
                """
                )
                .bind("id", report.id)
                .bind("reporterId", report.reporterId)
                .bind("targetId", report.targetId)
                .bind("targetType", report.targetType.name)
                .bind("reason", report.reason)
                .bind("status", report.status.name)
                .bind("createdAt", OffsetDateTime.ofInstant(report.createdAt, ZoneOffset.UTC))
                .execute()
        }
        return report
    }

    override fun findById(id: UUID): Report? =
        jdbi.withHandle<Report?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_reports WHERE id = :id")
                .bind("id", id)
                .map { rs, _ ->
                    Report(
                        id = rs.getObject("id", UUID::class.java),
                        reporterId = rs.getObject("reporter_id", UUID::class.java),
                        targetId = rs.getObject("target_id", UUID::class.java),
                        targetType = ReportTarget.valueOf(rs.getString("target_type")),
                        reason = rs.getString("reason"),
                        status = ReportStatus.valueOf(rs.getString("status")),
                        resolvedBy = rs.getObject("resolved_by", UUID::class.java),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .findOne()
                .orElse(null)
        }

    override fun listByCommunity(communityId: UUID, status: ReportStatus?, limit: Int): List<Report> =
        jdbi.withHandle<List<Report>, Exception> { handle ->
            val statusFilter = if (status != null) "AND r.status = :status" else ""
            handle
                .createQuery(
                    """
                SELECT r.* FROM stl_reports r
                JOIN stl_posts p ON r.target_id = p.id AND r.target_type = 'POST'
                WHERE p.community_id = :communityId $statusFilter
                ORDER BY r.created_at DESC
                LIMIT :limit
                """
                )
                .bind("communityId", communityId)
                .also { q -> status?.let { q.bind("status", it.name) } }
                .bind("limit", limit)
                .map { rs, _ ->
                    Report(
                        id = rs.getObject("id", UUID::class.java),
                        reporterId = rs.getObject("reporter_id", UUID::class.java),
                        targetId = rs.getObject("target_id", UUID::class.java),
                        targetType = ReportTarget.valueOf(rs.getString("target_type")),
                        reason = rs.getString("reason"),
                        status = ReportStatus.valueOf(rs.getString("status")),
                        resolvedBy = rs.getObject("resolved_by", UUID::class.java),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .list()
        }

    override fun updateStatus(reportId: UUID, status: ReportStatus, resolvedBy: UUID?) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_reports SET status = :status, resolved_by = :resolvedBy WHERE id = :id")
                .bind("id", reportId)
                .bind("status", status.name)
                .bind("resolvedBy", resolvedBy)
                .execute()
        }
    }
}

class JdbiSavedItemRepository(private val jdbi: Jdbi) : SavedItemRepository {

    override fun save(userId: UUID, targetId: UUID, targetType: SavedItemType) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_saved_items (user_id, target_id, target_type)
                VALUES (:userId, :targetId, :targetType)
                """
                )
                .bind("userId", userId)
                .bind("targetId", targetId)
                .bind("targetType", targetType.name)
                .execute()
        }
    }

    override fun unsave(userId: UUID, targetId: UUID, targetType: SavedItemType) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                DELETE FROM stl_saved_items
                WHERE user_id = :userId AND target_id = :targetId AND target_type = :targetType
                """
                )
                .bind("userId", userId)
                .bind("targetId", targetId)
                .bind("targetType", targetType.name)
                .execute()
        }
    }

    override fun isSaved(userId: UUID, targetId: UUID, targetType: SavedItemType): Boolean =
        jdbi.withHandle<Boolean, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT COUNT(*) FROM stl_saved_items
                WHERE user_id = :userId AND target_id = :targetId AND target_type = :targetType
                """
                )
                .bind("userId", userId)
                .bind("targetId", targetId)
                .bind("targetType", targetType.name)
                .mapTo(Long::class.java)
                .one() > 0
        }

    override fun listSaved(userId: UUID, limit: Int, offset: Int): List<SavedItem> =
        jdbi.withHandle<List<SavedItem>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT * FROM stl_saved_items WHERE user_id = :userId
                ORDER BY created_at DESC LIMIT :limit OFFSET :offset
                """
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ ->
                    SavedItem(
                        userId = rs.getObject("user_id", UUID::class.java),
                        targetId = rs.getObject("target_id", UUID::class.java),
                        targetType = SavedItemType.valueOf(rs.getString("target_type")),
                        createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
                    )
                }
                .list()
        }

    override fun findSavedIds(userId: UUID, targetIds: List<UUID>, targetType: SavedItemType): Set<UUID> {
        if (targetIds.isEmpty()) return emptySet()
        return jdbi.withHandle<Set<UUID>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT target_id FROM stl_saved_items
                WHERE user_id = :userId AND target_type = :targetType
                AND target_id IN (<targetIds>)
                """
                )
                .bind("userId", userId)
                .bind("targetType", targetType.name)
                .bindList("targetIds", targetIds)
                .mapTo(UUID::class.java)
                .set()
        }
    }
}

class JdbiKarmaRepository(private val jdbi: Jdbi) : KarmaRepository {

    override fun getKarma(userId: UUID): UserKarma =
        jdbi.withHandle<UserKarma, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_user_karma WHERE user_id = :userId")
                .bind("userId", userId)
                .map { rs, _ ->
                    UserKarma(
                        userId = rs.getObject("user_id", UUID::class.java),
                        postKarma = rs.getInt("post_karma"),
                        commentKarma = rs.getInt("comment_karma"),
                    )
                }
                .findOne()
                .orElse(UserKarma(userId))
        }

    override fun updatePostKarma(userId: UUID, delta: Int) {
        ensureExists(userId)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_user_karma SET post_karma = post_karma + :delta WHERE user_id = :userId")
                .bind("userId", userId)
                .bind("delta", delta)
                .execute()
        }
    }

    override fun updateCommentKarma(userId: UUID, delta: Int) {
        ensureExists(userId)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE stl_user_karma SET comment_karma = comment_karma + :delta WHERE user_id = :userId"
                )
                .bind("userId", userId)
                .bind("delta", delta)
                .execute()
        }
    }

    override fun ensureExists(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            val exists =
                handle
                    .createQuery("SELECT COUNT(*) FROM stl_user_karma WHERE user_id = :userId")
                    .bind("userId", userId)
                    .mapTo(Long::class.java)
                    .one() > 0
            if (!exists) {
                handle
                    .createUpdate("INSERT INTO stl_user_karma (user_id) VALUES (:userId)")
                    .bind("userId", userId)
                    .execute()
            }
        }
    }
}
