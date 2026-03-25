package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.TimeRange
import java.util.UUID

interface PostRepository {
    fun findById(id: UUID): Post?

    fun create(post: Post): Post

    fun update(post: Post): Post

    fun listByCommunity(
        communityId: UUID,
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary>

    fun listByUser(userId: UUID, limit: Int = 25, offset: Int = 0): List<PostSummary>

    fun listFeed(
        communityIds: List<UUID>,
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary>

    fun listAll(
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary>

    fun search(query: String, limit: Int = 25, offset: Int = 0): List<PostSummary>

    fun updateScore(postId: UUID, delta: Int)

    fun incrementCommentCount(postId: UUID, delta: Int)

    fun countByCommunity(communityId: UUID): Long
}
