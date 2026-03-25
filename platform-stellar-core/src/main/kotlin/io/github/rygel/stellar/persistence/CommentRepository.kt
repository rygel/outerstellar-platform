package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentSort
import java.util.UUID

interface CommentRepository {
    fun findById(id: UUID): Comment?

    fun create(comment: Comment): Comment

    fun update(comment: Comment): Comment

    fun listByPost(
        postId: UUID,
        sort: CommentSort = CommentSort.BEST,
        limit: Int = 200,
        maxDepth: Int = 10,
    ): List<Comment>

    fun listByUser(userId: UUID, limit: Int = 25, offset: Int = 0): List<Comment>

    fun countByPost(postId: UUID): Long

    fun updateScore(commentId: UUID, delta: Int)
}
