package io.github.rygel.stellar.model

import java.time.Instant
import java.util.UUID

data class PostSummary(
    val id: UUID,
    val communityName: String,
    val communityDisplayName: String,
    val authorUsername: String,
    val title: String,
    val previewText: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val postType: PostType,
    val score: Int,
    val commentCount: Int,
    val flairName: String? = null,
    val flairColor: String? = null,
    val userVote: Int? = null,
    val isSaved: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Instant,
)

data class CommentSummary(
    val id: UUID,
    val postId: UUID,
    val authorUsername: String,
    val body: String,
    val score: Int,
    val depth: Int,
    val userVote: Int? = null,
    val isSaved: Boolean = false,
    val isRemoved: Boolean = false,
    val replyCount: Int = 0,
    val createdAt: Instant,
)

data class CommentTree(val comment: CommentSummary, val children: List<CommentTree> = emptyList())

data class CommunitySummary(
    val id: UUID,
    val name: String,
    val displayName: String,
    val description: String,
    val iconUrl: String? = null,
    val subscriberCount: Long,
    val isSubscribed: Boolean = false,
)

enum class PostSort {
    HOT,
    NEW,
    TOP,
    RISING,
    CONTROVERSIAL,
}

enum class CommentSort {
    BEST,
    NEW,
    TOP,
    CONTROVERSIAL,
}

enum class TimeRange {
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}
