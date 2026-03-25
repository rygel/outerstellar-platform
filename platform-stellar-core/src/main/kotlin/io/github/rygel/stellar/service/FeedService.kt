package io.github.rygel.stellar.service

import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.SavedItemType
import io.github.rygel.stellar.model.TimeRange
import io.github.rygel.stellar.model.VoteTarget
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.SavedItemRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.github.rygel.stellar.persistence.VoteRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val POPULAR_FEED_TTL_SECONDS = 120L

class FeedService(
    private val postRepo: PostRepository,
    private val voteRepo: VoteRepository,
    private val savedRepo: SavedItemRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val userRepo: UserRepository,
    private val cache: StellarCache? = null,
) {
    fun homeFeed(userId: UUID?, sort: PostSort, timeRange: TimeRange, limit: Int, offset: Int): List<PostSummary> {
        val posts =
            if (userId != null) {
                val communityIds = subscriptionRepo.findSubscribedCommunityIds(userId)
                if (communityIds.isEmpty()) {
                    postRepo.listAll(sort, timeRange, limit, offset)
                } else {
                    postRepo.listFeed(communityIds, sort, timeRange, limit, offset)
                }
            } else {
                postRepo.listAll(sort, timeRange, limit, offset)
            }
        return enrichPosts(posts, userId)
    }

    fun popularFeed(
        sort: PostSort,
        timeRange: TimeRange,
        limit: Int,
        offset: Int,
        userId: UUID? = null,
    ): List<PostSummary> {
        val cacheKey = "feed:popular:$sort:$timeRange:$limit:$offset"
        val basePosts =
            cache?.get<List<PostSummary>>(cacheKey)
                ?: postRepo.listAll(sort, timeRange, limit, offset).also {
                    cache?.put(cacheKey, it, POPULAR_FEED_TTL_SECONDS)
                }
        return enrichPosts(basePosts, userId)
    }

    fun invalidatePopularFeedCache() {
        cache?.invalidateByPrefix("feed:popular:")
    }

    fun communityFeed(
        communityId: UUID,
        sort: PostSort,
        timeRange: TimeRange,
        limit: Int,
        offset: Int,
        userId: UUID? = null,
    ): List<PostSummary> {
        val posts = postRepo.listByCommunity(communityId, sort, timeRange, limit, offset)
        return enrichPosts(posts, userId)
    }

    fun userPosts(targetUserId: UUID, limit: Int, offset: Int, viewerId: UUID? = null): List<PostSummary> {
        val posts = postRepo.listByUser(targetUserId, limit, offset)
        return enrichPosts(posts, viewerId)
    }

    fun searchPosts(query: String, limit: Int, offset: Int, userId: UUID? = null): List<PostSummary> {
        val posts = postRepo.search(query, limit, offset)
        return enrichPosts(posts, userId)
    }

    private fun enrichPosts(posts: List<PostSummary>, userId: UUID?): List<PostSummary> {
        if (posts.isEmpty()) return posts

        val postIds = posts.map { it.id }
        val userVotes =
            if (userId != null) {
                voteRepo.findUserVotes(userId, postIds, VoteTarget.POST)
            } else {
                emptyMap()
            }
        val savedIds =
            if (userId != null) {
                savedRepo.findSavedIds(userId, postIds, SavedItemType.POST)
            } else {
                emptySet()
            }

        return posts.map { post ->
            val authorUsername = post.authorUsername.ifEmpty { resolveUsername(post) }
            post.copy(authorUsername = authorUsername, userVote = userVotes[post.id], isSaved = post.id in savedIds)
        }
    }

    private val usernameCache = ConcurrentHashMap<UUID, String>()

    private fun resolveUsername(post: PostSummary): String {
        // PostSummary doesn't carry authorId directly, so username should be resolved
        // at the repository level via JOIN. Return what we have.
        return post.authorUsername.ifEmpty { "anonymous" }
    }

    fun resolveUsername(userId: UUID): String =
        usernameCache.getOrPut(userId) { userRepo.findById(userId)?.username ?: "[deleted]" }
}
