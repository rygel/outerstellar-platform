package io.github.rygel.stellar.model

import java.time.Instant
import java.util.UUID

data class Community(
    val id: UUID,
    val name: String,
    val displayName: String,
    val description: String = "",
    val sidebar: String = "",
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val creatorId: UUID,
    val isNsfw: Boolean = false,
    val subscriberCount: Long = 0,
    val createdAt: Instant = Instant.now(),
)

data class Post(
    val id: UUID,
    val communityId: UUID,
    val authorId: UUID,
    val title: String,
    val body: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val postType: PostType = PostType.TEXT,
    val score: Int = 0,
    val commentCount: Int = 0,
    val flairId: UUID? = null,
    val isLocked: Boolean = false,
    val isPinned: Boolean = false,
    val isRemoved: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

enum class PostType {
    TEXT,
    LINK,
    IMAGE,
}

data class Comment(
    val id: UUID,
    val postId: UUID,
    val authorId: UUID,
    val parentCommentId: UUID? = null,
    val body: String,
    val score: Int = 0,
    val depth: Int = 0,
    val isRemoved: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val authorUsername: String = "",
)

data class Vote(
    val id: Long = 0,
    val userId: UUID,
    val targetId: UUID,
    val targetType: VoteTarget,
    val value: Int,
    val createdAt: Instant = Instant.now(),
)

enum class VoteTarget {
    POST,
    COMMENT,
}

data class Subscription(val userId: UUID, val communityId: UUID, val createdAt: Instant = Instant.now())

data class CommunityMember(
    val userId: UUID,
    val communityId: UUID,
    val role: CommunityRole,
    val createdAt: Instant = Instant.now(),
)

enum class CommunityRole {
    MEMBER,
    MODERATOR,
    ADMIN,
}

data class Flair(
    val id: UUID,
    val communityId: UUID,
    val name: String,
    val color: String = "#666666",
    val type: FlairType = FlairType.POST,
)

enum class FlairType {
    POST,
    USER,
}

data class SavedItem(
    val userId: UUID,
    val targetId: UUID,
    val targetType: SavedItemType,
    val createdAt: Instant = Instant.now(),
)

enum class SavedItemType {
    POST,
    COMMENT,
}

data class Report(
    val id: UUID,
    val reporterId: UUID,
    val targetId: UUID,
    val targetType: ReportTarget,
    val reason: String,
    val status: ReportStatus = ReportStatus.PENDING,
    val resolvedBy: UUID? = null,
    val createdAt: Instant = Instant.now(),
)

enum class ReportTarget {
    POST,
    COMMENT,
}

enum class ReportStatus {
    PENDING,
    RESOLVED,
    DISMISSED,
}

data class UserKarma(val userId: UUID, val postKarma: Int = 0, val commentKarma: Int = 0) {
    val totalKarma: Int
        get() = postKarma + commentKarma
}

data class CommunityRule(
    val id: UUID,
    val communityId: UUID,
    val title: String,
    val description: String = "",
    val position: Int = 0,
)

data class CommunityBan(
    val userId: UUID,
    val communityId: UUID,
    val reason: String? = null,
    val bannedBy: UUID,
    val createdAt: Instant = Instant.now(),
)
