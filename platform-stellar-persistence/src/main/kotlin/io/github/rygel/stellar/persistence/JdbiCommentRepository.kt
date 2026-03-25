package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentSort
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiCommentRepository(private val jdbi: Jdbi) : CommentRepository {

    override fun findById(id: UUID): Comment? =
        jdbi.withHandle<Comment?, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_comments WHERE id = :id")
                .bind("id", id)
                .map { rs, _ -> mapComment(rs) }
                .findOne()
                .orElse(null)
        }

    override fun create(comment: Comment): Comment {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                INSERT INTO stl_comments (id, post_id, author_id, parent_comment_id, body,
                    score, depth, is_removed, created_at, updated_at)
                VALUES (:id, :postId, :authorId, :parentCommentId, :body,
                    :score, :depth, :isRemoved, :createdAt, :updatedAt)
                """
                )
                .bind("id", comment.id)
                .bind("postId", comment.postId)
                .bind("authorId", comment.authorId)
                .bind("parentCommentId", comment.parentCommentId)
                .bind("body", comment.body)
                .bind("score", comment.score)
                .bind("depth", comment.depth)
                .bind("isRemoved", comment.isRemoved)
                .bind("createdAt", OffsetDateTime.ofInstant(comment.createdAt, ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(comment.updatedAt, ZoneOffset.UTC))
                .execute()
        }
        return comment
    }

    override fun update(comment: Comment): Comment {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                UPDATE stl_comments SET body = :body, is_removed = :isRemoved,
                    updated_at = :updatedAt WHERE id = :id
                """
                )
                .bind("id", comment.id)
                .bind("body", comment.body)
                .bind("isRemoved", comment.isRemoved)
                .bind("updatedAt", OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
                .execute()
        }
        return comment
    }

    override fun listByPost(postId: UUID, sort: CommentSort): List<Comment> {
        val orderBy =
            when (sort) {
                CommentSort.BEST -> "score DESC, created_at ASC"
                CommentSort.NEW -> "created_at DESC"
                CommentSort.TOP -> "score DESC"
                CommentSort.CONTROVERSIAL -> "score ASC, created_at DESC"
            }
        return jdbi.withHandle<List<Comment>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM stl_comments WHERE post_id = :postId ORDER BY $orderBy")
                .bind("postId", postId)
                .map { rs, _ -> mapComment(rs) }
                .list()
        }
    }

    override fun listByUser(userId: UUID, limit: Int, offset: Int): List<Comment> =
        jdbi.withHandle<List<Comment>, Exception> { handle ->
            handle
                .createQuery(
                    """
                SELECT * FROM stl_comments
                WHERE author_id = :userId AND is_removed = false
                ORDER BY created_at DESC
                LIMIT :limit OFFSET :offset
                """
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ -> mapComment(rs) }
                .list()
        }

    override fun countByPost(postId: UUID): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT COUNT(*) FROM stl_comments WHERE post_id = :id AND is_removed = false")
                .bind("id", postId)
                .mapTo(Long::class.java)
                .one()
        }

    override fun updateScore(commentId: UUID, delta: Int) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE stl_comments SET score = score + :delta WHERE id = :id")
                .bind("id", commentId)
                .bind("delta", delta)
                .execute()
        }
    }

    private fun mapComment(rs: ResultSet): Comment =
        Comment(
            id = rs.getObject("id", UUID::class.java),
            postId = rs.getObject("post_id", UUID::class.java),
            authorId = rs.getObject("author_id", UUID::class.java),
            parentCommentId = rs.getObject("parent_comment_id", UUID::class.java),
            body = rs.getString("body"),
            score = rs.getInt("score"),
            depth = rs.getInt("depth"),
            isRemoved = rs.getBoolean("is_removed"),
            createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now(),
            updatedAt = rs.getTimestamp("updated_at")?.toInstant() ?: Instant.now(),
        )
}
