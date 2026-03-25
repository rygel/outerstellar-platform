package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentNotFoundException
import io.github.rygel.stellar.model.CommunityBan
import io.github.rygel.stellar.model.CommunityMember
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.NotModeratorException
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostNotFoundException
import io.github.rygel.stellar.model.Report
import io.github.rygel.stellar.model.ReportNotFoundException
import io.github.rygel.stellar.model.ReportStatus
import io.github.rygel.stellar.model.ReportTarget
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.ReportRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class ModerationService(
    private val postRepo: PostRepository,
    private val commentRepo: CommentRepository,
    private val memberRepo: CommunityMemberRepository,
    private val reportRepo: ReportRepository,
) {
    private val logger = LoggerFactory.getLogger(ModerationService::class.java)

    fun removePost(postId: UUID, moderatorId: UUID, communityId: UUID): Post {
        requireModerator(moderatorId, communityId)
        val post = postRepo.findById(postId) ?: throw PostNotFoundException(postId)
        val updated = post.copy(isRemoved = true)
        val result = postRepo.update(updated)
        logger.info("Post {} removed by moderator {} in community {}", postId, moderatorId, communityId)
        return result
    }

    fun lockPost(postId: UUID, moderatorId: UUID, communityId: UUID): Post {
        requireModerator(moderatorId, communityId)
        val post = postRepo.findById(postId) ?: throw PostNotFoundException(postId)
        val updated = post.copy(isLocked = true)
        val result = postRepo.update(updated)
        logger.info("Post {} locked by moderator {} in community {}", postId, moderatorId, communityId)
        return result
    }

    fun pinPost(postId: UUID, moderatorId: UUID, communityId: UUID): Post {
        requireModerator(moderatorId, communityId)
        val post = postRepo.findById(postId) ?: throw PostNotFoundException(postId)
        val updated = post.copy(isPinned = !post.isPinned)
        val result = postRepo.update(updated)
        logger.info(
            "Post {} pin toggled to {} by moderator {} in community {}",
            postId,
            updated.isPinned,
            moderatorId,
            communityId,
        )
        return result
    }

    fun removeComment(commentId: UUID, moderatorId: UUID): Comment {
        val comment = commentRepo.findById(commentId) ?: throw CommentNotFoundException(commentId)
        val post = postRepo.findById(comment.postId) ?: throw PostNotFoundException(comment.postId)
        requireModerator(moderatorId, post.communityId)
        val updated = comment.copy(isRemoved = true)
        val result = commentRepo.update(updated)
        logger.info("Comment {} removed by moderator {} in community {}", commentId, moderatorId, post.communityId)
        return result
    }

    fun banUser(userId: UUID, communityId: UUID, reason: String?, bannedBy: UUID) {
        requireModerator(bannedBy, communityId)
        val ban = CommunityBan(userId = userId, communityId = communityId, reason = reason, bannedBy = bannedBy)
        memberRepo.ban(ban)
        logger.info("User {} banned from community {} by {}", userId, communityId, bannedBy)
    }

    fun unbanUser(userId: UUID, communityId: UUID, moderatorId: UUID) {
        requireModerator(moderatorId, communityId)
        memberRepo.unban(userId, communityId)
        logger.info("User {} unbanned from community {} by {}", userId, communityId, moderatorId)
    }

    fun createReport(reporterId: UUID, targetId: UUID, targetType: ReportTarget, reason: String): Report {
        val report =
            Report(
                id = UUID.randomUUID(),
                reporterId = reporterId,
                targetId = targetId,
                targetType = targetType,
                reason = reason.trim(),
            )
        val created = reportRepo.create(report)
        logger.info("Report {} created by {} for {} {}", created.id, reporterId, targetType, targetId)
        return created
    }

    fun resolveReport(reportId: UUID, resolvedBy: UUID): Report {
        val report = reportRepo.findById(reportId) ?: throw ReportNotFoundException(reportId)
        reportRepo.updateStatus(reportId, ReportStatus.RESOLVED, resolvedBy)
        logger.info("Report {} resolved by {}", reportId, resolvedBy)
        return report.copy(status = ReportStatus.RESOLVED, resolvedBy = resolvedBy)
    }

    fun dismissReport(reportId: UUID, resolvedBy: UUID): Report {
        val report = reportRepo.findById(reportId) ?: throw ReportNotFoundException(reportId)
        reportRepo.updateStatus(reportId, ReportStatus.DISMISSED, resolvedBy)
        logger.info("Report {} dismissed by {}", reportId, resolvedBy)
        return report.copy(status = ReportStatus.DISMISSED, resolvedBy = resolvedBy)
    }

    fun getPendingReports(communityId: UUID): List<Report> =
        reportRepo.listByCommunity(communityId, ReportStatus.PENDING)

    fun isModerator(userId: UUID, communityId: UUID): Boolean {
        val membership = memberRepo.findMembership(userId, communityId)
        return membership != null &&
            (membership.role == CommunityRole.MODERATOR || membership.role == CommunityRole.ADMIN)
    }

    fun listModerators(communityId: UUID): List<CommunityMember> = memberRepo.listModerators(communityId)

    private fun requireModerator(userId: UUID, communityId: UUID) {
        if (!isModerator(userId, communityId)) {
            throw NotModeratorException(communityId.toString())
        }
    }
}
