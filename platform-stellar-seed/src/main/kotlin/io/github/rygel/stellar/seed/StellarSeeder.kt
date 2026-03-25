package io.github.rygel.stellar.seed

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import java.util.UUID
import org.slf4j.LoggerFactory

class StellarSeeder(
    private val communityRepo: CommunityRepository,
    private val postRepo: PostRepository,
    private val commentRepo: CommentRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val memberRepo: CommunityMemberRepository,
) {
    private val logger = LoggerFactory.getLogger(StellarSeeder::class.java)

    fun seed(adminUserId: UUID) {
        if (communityRepo.countAll() > 0) {
            logger.info("Stellar data already seeded, skipping")
            return
        }
        logger.info("Seeding Stellar sample data...")

        val kotlin =
            createCommunity(
                "kotlin",
                "Kotlin Programming",
                "Discuss Kotlin language features and libraries",
                adminUserId,
            )
        val gaming = createCommunity("gaming", "Gaming", "A community for gamers", adminUserId)
        val science = createCommunity("science", "Science", "Scientific discoveries and discussions", adminUserId)

        val post1 =
            createPost(
                kotlin.id,
                adminUserId,
                "Welcome to r/kotlin!",
                "This is the official Kotlin community. Share your projects, ask questions, and learn from each other.",
                PostType.TEXT,
            )
        val post2 =
            createPost(
                kotlin.id,
                adminUserId,
                "Kotlin 2.3 released with major improvements",
                "Check out the new features: https://kotlinlang.org",
                PostType.TEXT,
            )
        val post3 =
            createPost(
                gaming.id,
                adminUserId,
                "What are you playing this weekend?",
                "Looking for game recommendations!",
                PostType.TEXT,
            )
        val post4 =
            createPost(
                science.id,
                adminUserId,
                "New study reveals surprising findings about dark matter",
                null,
                PostType.LINK,
            )

        createComment(post1.id, adminUserId, "Great to be here! Kotlin is amazing.")
        createComment(post2.id, adminUserId, "The new context receivers are game-changing.")
        createComment(post3.id, adminUserId, "Currently playing through Elden Ring for the third time.")

        logger.info("Stellar seed complete: 3 communities, 4 posts, 3 comments")
    }

    private fun createCommunity(name: String, displayName: String, description: String, creatorId: UUID): Community {
        val community =
            communityRepo.create(
                Community(
                    id = UUID.randomUUID(),
                    name = name,
                    displayName = displayName,
                    description = description,
                    creatorId = creatorId,
                )
            )
        memberRepo.addMember(creatorId, community.id, CommunityRole.ADMIN)
        subscriptionRepo.subscribe(creatorId, community.id)
        communityRepo.incrementSubscriberCount(community.id, 1)
        return community
    }

    private fun createPost(communityId: UUID, authorId: UUID, title: String, body: String?, postType: PostType): Post =
        postRepo.create(
            Post(
                id = UUID.randomUUID(),
                communityId = communityId,
                authorId = authorId,
                title = title,
                body = body,
                postType = postType,
            )
        )

    private fun createComment(postId: UUID, authorId: UUID, body: String): Comment {
        val comment =
            commentRepo.create(Comment(id = UUID.randomUUID(), postId = postId, authorId = authorId, body = body))
        postRepo.incrementCommentCount(postId, 1)
        return comment
    }
}
