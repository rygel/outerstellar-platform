package io.github.rygel.stellar.service

import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommentNotFoundException
import io.github.rygel.stellar.model.CommentSort
import io.github.rygel.stellar.model.CommentSummary
import io.github.rygel.stellar.model.CommentTree
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.PostRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class CommentService(
    private val commentRepo: CommentRepository,
    private val postRepo: PostRepository,
    private val userRepo: UserRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(CommentService::class.java)
    private val usernameCache = mutableMapOf<UUID, String>()

    fun getComment(id: UUID): Comment = commentRepo.findById(id) ?: throw CommentNotFoundException(id)

    fun createComment(postId: UUID, authorId: UUID, body: String, parentCommentId: UUID? = null): Comment {
        val depth =
            if (parentCommentId != null) {
                val parent = commentRepo.findById(parentCommentId)
                (parent?.depth ?: 0) + 1
            } else {
                0
            }
        val comment =
            commentRepo.create(
                Comment(
                    id = UUID.randomUUID(),
                    postId = postId,
                    authorId = authorId,
                    parentCommentId = parentCommentId,
                    body = body.trim(),
                    depth = depth,
                )
            )
        postRepo.incrementCommentCount(postId, 1)
        logger.info("Comment created on post {} by user {}", postId, authorId)
        return comment
    }

    fun getCommentTree(postId: UUID, sort: CommentSort = CommentSort.BEST): List<CommentTree> {
        val comments = commentRepo.listByPost(postId, sort)
        return buildTree(comments)
    }

    private fun buildTree(comments: List<Comment>): List<CommentTree> {
        val byParent = comments.groupBy { it.parentCommentId }
        fun buildChildren(parentId: UUID?): List<CommentTree> =
            (byParent[parentId] ?: emptyList()).map { comment ->
                val children = buildChildren(comment.id)
                CommentTree(
                    comment =
                        CommentSummary(
                            id = comment.id,
                            postId = comment.postId,
                            authorUsername = resolveUsername(comment.authorId),
                            body = comment.body,
                            score = comment.score,
                            depth = comment.depth,
                            isRemoved = comment.isRemoved,
                            replyCount = children.size,
                            createdAt = comment.createdAt,
                        ),
                    children = children,
                )
            }
        return buildChildren(null)
    }

    private fun resolveUsername(userId: UUID): String =
        usernameCache.getOrPut(userId) { userRepo?.findById(userId)?.username ?: "[deleted]" }
}
