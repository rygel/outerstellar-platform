package io.github.rygel.stellar.web

import io.github.rygel.stellar.model.CommentTree
import io.github.rygel.stellar.model.CommunitySummary
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.PostType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.http4k.template.ViewModel

data class FeedPage(
    val feedType: String,
    val sort: String,
    val timeRange: String?,
    val posts: List<PostCardViewModel>,
    val communities: List<CommunityCardViewModel>,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/FeedPage"
}

data class CommunityPage(
    val community: CommunityDetailViewModel,
    val posts: List<PostCardViewModel>,
    val sort: String,
    val timeRange: String?,
    val isSubscribed: Boolean,
    val hasMore: Boolean = false,
    val nextOffset: Int = 0,
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/CommunityPage"
}

data class PostDetailPage(
    val post: PostDetailViewModel,
    val comments: List<CommentTree>,
    val commentSort: String,
    val communityName: String,
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/PostDetailPage"
}

data class SubmitPostPage(val communityName: String, val communityDisplayName: String, val communityId: UUID) :
    ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/SubmitPostPage"
}

data class CommunitiesPage(val communities: List<CommunityCardViewModel>, val query: String? = null) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/CommunitiesPage"
}

data class CreateCommunityPage(val error: String? = null) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/CreateCommunityPage"
}

data class UserProfilePage(
    val username: String,
    val karma: io.github.rygel.stellar.model.UserKarma,
    val posts: List<PostCardViewModel>,
    val tab: String = "posts",
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/UserProfilePage"
}

data class SavedPage(val savedItems: List<io.github.rygel.stellar.model.SavedItem>) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/SavedPage"
}

data class SearchResultsPage(
    val query: String,
    val posts: List<PostCardViewModel>,
    val communities: List<CommunityCardViewModel>,
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/SearchResultsPage"
}

// Component ViewModels

data class PostCardViewModel(
    val id: UUID,
    val communityName: String,
    val communityDisplayName: String,
    val authorUsername: String,
    val title: String,
    val previewText: String?,
    val url: String?,
    val postType: PostType,
    val score: Int,
    val commentCount: Int,
    val flairName: String?,
    val flairColor: String?,
    val userVote: Int?,
    val isPinned: Boolean,
    val timeAgo: String,
)

data class PostDetailViewModel(
    val id: UUID,
    val communityName: String,
    val authorUsername: String,
    val title: String,
    val body: String?,
    val url: String?,
    val postType: PostType,
    val score: Int,
    val commentCount: Int,
    val userVote: Int?,
    val isLocked: Boolean,
    val timeAgo: String,
)

data class CommunityCardViewModel(
    val id: UUID,
    val name: String,
    val displayName: String,
    val description: String,
    val iconUrl: String?,
    val subscriberCount: Long,
    val isSubscribed: Boolean,
)

data class CommunityDetailViewModel(
    val id: UUID,
    val name: String,
    val displayName: String,
    val description: String,
    val sidebar: String,
    val subscriberCount: Long,
    val postCount: Long,
)

data class VoteButtonsFragment(val targetId: UUID, val targetType: String, val score: Int, val userVote: Int?) :
    ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/components/VoteButtons"
}

data class FeedListFragment(
    val posts: List<PostCardViewModel>,
    val hasMore: Boolean,
    val nextOffset: Int,
    val sort: String,
    val feedType: String,
    val communityName: String? = null,
) : ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/components/FeedList"
}

data class SubscribeButtonFragment(val communityId: UUID, val communityName: String, val isSubscribed: Boolean) :
    ViewModel {
    override fun template(): String = "io/github/rygel/stellar/web/components/SubscribeButton"
}

object TimeFormatting {
    fun formatTimeAgo(instant: Instant): String {
        val duration = Duration.between(instant, Instant.now())
        return when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 30 -> "${duration.toDays()}d ago"
            duration.toDays() < 365 -> "${duration.toDays() / 30}mo ago"
            else -> "${duration.toDays() / 365}y ago"
        }
    }

    fun PostSummary.toCardViewModel(): PostCardViewModel =
        PostCardViewModel(
            id = id,
            communityName = communityName,
            communityDisplayName = communityDisplayName,
            authorUsername = authorUsername,
            title = title,
            previewText = previewText,
            url = url,
            postType = postType,
            score = score,
            commentCount = commentCount,
            flairName = flairName,
            flairColor = flairColor,
            userVote = userVote,
            isPinned = isPinned,
            timeAgo = formatTimeAgo(createdAt),
        )

    fun CommunitySummary.toCardViewModel(): CommunityCardViewModel =
        CommunityCardViewModel(
            id = id,
            name = name,
            displayName = displayName,
            description = description,
            iconUrl = iconUrl,
            subscriberCount = subscriberCount,
            isSubscribed = isSubscribed,
        )
}
