package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.model.TimeRange
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiPostRepository(private val jdbi: Jdbi) : PostRepository {

    override fun findById(id: UUID): Post? =
        jdbi.withHandle<Post?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_posts WHERE id = :id")
                .bind("id", id)
                .map { rs, _ -> mapPost(rs) }
                .findOne()
                .orElse(null)
        }

    override fun create(post: Post): Post {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_posts (id, community_id, author_id, title, body, url, image_url,
                    post_type, score, comment_count, flair_id, is_locked, is_pinned, is_removed,
                    created_at, updated_at)
                VALUES (:id, :communityId, :authorId, :title, :body, :url, :imageUrl,
                    :postType, :score, :commentCount, :flairId, :isLocked, :isPinned, :isRemoved,
                    :createdAt, :updatedAt)
                """
                )
                .bind("id", post.id)
                .bind("communityId", post.communityId)
                .bind("authorId", post.authorId)
                .bind("title", post.title)
                .bind("body", post.body)
                .bind("url", post.url)
                .bind("imageUrl", post.imageUrl)
                .bind("postType", post.postType.name)
                .bind("score", post.score)
                .bind("commentCount", post.commentCount)
                .bind("flairId", post.flairId)
                .bind("isLocked", post.isLocked)
                .bind("isPinned", post.isPinned)
                .bind("isRemoved", post.isRemoved)
                .bind("createdAt", OffsetDateTime.ofInstant(post.createdAt, ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(post.updatedAt, ZoneOffset.UTC))
                .execute()
        }
        return post
    }

    override fun update(post: Post): Post {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                UPDATE stl_posts
                SET title = :title, body = :body, url = :url, image_url = :imageUrl,
                    flair_id = :flairId, is_locked = :isLocked, is_pinned = :isPinned,
                    is_removed = :isRemoved, updated_at = :updatedAt
                WHERE id = :id
                """
                )
                .bind("id", post.id)
                .bind("title", post.title)
                .bind("body", post.body)
                .bind("url", post.url)
                .bind("imageUrl", post.imageUrl)
                .bind("flairId", post.flairId)
                .bind("isLocked", post.isLocked)
                .bind("isPinned", post.isPinned)
                .bind("isRemoved", post.isRemoved)
                .bind("updatedAt", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
                .execute()
        }
        return post
    }

    override fun listByCommunity(
        communityId: UUID,
        sort: PostSort,
        timeRange: TimeRange,
        limit: Int,
        offset: Int,
    ): List<PostSummary> {
        val timeFilter = buildTimeFilter(timeRange)
        val orderBy = buildOrderBy(sort)
        return jdbi.withHandle<List<PostSummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT p.*, c.name AS community_name, c.display_name AS community_display_name,
                    u.username AS author_username
                FROM stl_posts p
                JOIN stl_communities c ON c.id = p.community_id
                JOIN plt_users u ON u.id = p.author_id
                WHERE p.community_id = :communityId AND p.is_removed = false $timeFilter
                ORDER BY $orderBy
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("communityId", communityId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPostSummary(rs) }
                .list()
        }
    }

    override fun listByUser(userId: UUID, limit: Int, offset: Int): List<PostSummary> =
        jdbi.withHandle<List<PostSummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT p.*, c.name AS community_name, c.display_name AS community_display_name,
                    u.username AS author_username
                FROM stl_posts p
                JOIN stl_communities c ON c.id = p.community_id
                JOIN plt_users u ON u.id = p.author_id
                WHERE p.author_id = :userId AND p.is_removed = false
                ORDER BY p.created_at DESC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPostSummary(rs) }
                .list()
        }

    override fun listFeed(
        communityIds: List<UUID>,
        sort: PostSort,
        timeRange: TimeRange,
        limit: Int,
        offset: Int,
    ): List<PostSummary> {
        if (communityIds.isEmpty()) return emptyList()
        val timeFilter = buildTimeFilter(timeRange)
        val orderBy = buildOrderBy(sort)
        return jdbi.withHandle<List<PostSummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT p.*, c.name AS community_name, c.display_name AS community_display_name,
                    u.username AS author_username
                FROM stl_posts p
                JOIN stl_communities c ON c.id = p.community_id
                JOIN plt_users u ON u.id = p.author_id
                WHERE p.community_id IN (<communityIds>) AND p.is_removed = false $timeFilter
                ORDER BY $orderBy
                LIMIT :limit OFFSET :offset
                """
                )
                .bindList("communityIds", communityIds)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPostSummary(rs) }
                .list()
        }
    }

    override fun listAll(sort: PostSort, timeRange: TimeRange, limit: Int, offset: Int): List<PostSummary> {
        val timeFilter = buildTimeFilter(timeRange)
        val orderBy = buildOrderBy(sort)
        return jdbi.withHandle<List<PostSummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT p.*, c.name AS community_name, c.display_name AS community_display_name,
                    u.username AS author_username
                FROM stl_posts p
                JOIN stl_communities c ON c.id = p.community_id
                JOIN plt_users u ON u.id = p.author_id
                WHERE p.is_removed = false $timeFilter
                ORDER BY $orderBy
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPostSummary(rs) }
                .list()
        }
    }

    override fun search(query: String, limit: Int, offset: Int): List<PostSummary> =
        jdbi.withHandle<List<PostSummary>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT p.*, c.name AS community_name, c.display_name AS community_display_name,
                    u.username AS author_username
                FROM stl_posts p
                JOIN stl_communities c ON c.id = p.community_id
                JOIN plt_users u ON u.id = p.author_id
                WHERE p.is_removed = false AND LOWER(p.title) LIKE :q
                ORDER BY p.score DESC, p.created_at DESC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("q", "%${query.lowercase()}%")
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapPostSummary(rs) }
                .list()
        }

    override fun updateScore(postId: UUID, delta: Int) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_posts SET score = score + :delta WHERE id = :id")
                .bind("id", postId)
                .bind("delta", delta)
                .execute()
        }
    }

    override fun incrementCommentCount(postId: UUID, delta: Int) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_posts SET comment_count = comment_count + :delta WHERE id = :id")
                .bind("id", postId)
                .bind("delta", delta)
                .execute()
        }
    }

    override fun countByCommunity(communityId: UUID): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM stl_posts WHERE community_id = :id AND is_removed = false")
                .bind("id", communityId)
                .mapTo(Long::class.java)
                .one()
        }

    private fun buildOrderBy(sort: PostSort): String =
        when (sort) {
            PostSort.NEW -> "p.created_at DESC"
            PostSort.TOP -> "p.score DESC, p.created_at DESC"
            PostSort.CONTROVERSIAL -> "p.comment_count DESC, p.created_at DESC"
            PostSort.RISING -> "p.score DESC, p.created_at DESC"
            PostSort.HOT ->
                """
                (LOG(GREATEST(ABS(p.score), 1)) +
                 SIGN(p.score) * EXTRACT(EPOCH FROM p.created_at) / 45000) DESC
                """
                    .trimIndent()
        }

    private fun buildTimeFilter(timeRange: TimeRange): String {
        val cutoff =
            when (timeRange) {
                TimeRange.HOUR -> Instant.now().minus(1, ChronoUnit.HOURS)
                TimeRange.DAY -> Instant.now().minus(1, ChronoUnit.DAYS)
                TimeRange.WEEK -> Instant.now().minus(7, ChronoUnit.DAYS)
                TimeRange.MONTH -> Instant.now().minus(30, ChronoUnit.DAYS)
                TimeRange.YEAR -> Instant.now().minus(365, ChronoUnit.DAYS)
                TimeRange.ALL -> return ""
            }
        return "AND p.created_at >= TIMESTAMP '${OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)}'"
    }

    private fun mapPost(rs: ResultSet): Post =
        Post(
            id = rs.getObject("id", UUID::class.java),
            communityId = rs.getObject("community_id", UUID::class.java),
            authorId = rs.getObject("author_id", UUID::class.java),
            title = rs.getString("title"),
            body = rs.getString("body"),
            url = rs.getString("url"),
            imageUrl = rs.getString("image_url"),
            postType = PostType.valueOf(rs.getString("post_type")),
            score = rs.getInt("score"),
            commentCount = rs.getInt("comment_count"),
            flairId = rs.getObject("flair_id", UUID::class.java),
            isLocked = rs.getBoolean("is_locked"),
            isPinned = rs.getBoolean("is_pinned"),
            isRemoved = rs.getBoolean("is_removed"),
            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
            updatedAt = rs.getTimestamp("updated_at")?.toInstant() ?: Instant.now(),
        )

    private fun mapPostSummary(rs: ResultSet): PostSummary =
        PostSummary(
            id = rs.getObject("id", UUID::class.java),
            communityName = rs.getString("community_name"),
            communityDisplayName = rs.getString("community_display_name"),
            authorUsername = rs.getString("author_username") ?: "[deleted]",
            title = rs.getString("title"),
            previewText = rs.getString("body")?.take(200),
            url = rs.getString("url"),
            postType = PostType.valueOf(rs.getString("post_type")),
            score = rs.getInt("score"),
            commentCount = rs.getInt("comment_count"),
            isPinned = rs.getBoolean("is_pinned"),
            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
        )
}
