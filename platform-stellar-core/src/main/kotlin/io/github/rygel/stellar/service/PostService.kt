package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.BannedFromCommunityException
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostNotFoundException
import io.github.rygel.stellar.model.PostSort
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.model.TimeRange
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.PostRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class PostService(private val postRepo: PostRepository, private val memberRepo: CommunityMemberRepository) {
    private val logger = LoggerFactory.getLogger(PostService::class.java)

    fun getPost(id: UUID): Post = postRepo.findById(id) ?: throw PostNotFoundException(id)

    fun createPost(
        communityId: UUID,
        authorId: UUID,
        title: String,
        body: String? = null,
        url: String? = null,
        postType: PostType = PostType.TEXT,
    ): Post {
        if (memberRepo.isBanned(authorId, communityId)) {
            throw BannedFromCommunityException(communityId.toString())
        }
        val post =
            postRepo.create(
                Post(
                    id = UUID.randomUUID(),
                    communityId = communityId,
                    authorId = authorId,
                    title = title.trim(),
                    body = body?.trim(),
                    url = url?.trim(),
                    postType = postType,
                )
            )
        logger.info("Post '{}' created in community {}", post.title, communityId)
        return post
    }

    fun listByCommunity(
        communityId: UUID,
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary> = postRepo.listByCommunity(communityId, sort, timeRange, limit, offset)

    fun listFeed(
        communityIds: List<UUID>,
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary> = postRepo.listFeed(communityIds, sort, timeRange, limit, offset)

    fun listAll(
        sort: PostSort = PostSort.HOT,
        timeRange: TimeRange = TimeRange.ALL,
        limit: Int = 25,
        offset: Int = 0,
    ): List<PostSummary> = postRepo.listAll(sort, timeRange, limit, offset)

    fun search(query: String, limit: Int = 25, offset: Int = 0): List<PostSummary> =
        postRepo.search(query, limit, offset)
}
