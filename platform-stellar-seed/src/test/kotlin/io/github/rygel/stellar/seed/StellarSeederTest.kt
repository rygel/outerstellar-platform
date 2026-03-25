package io.github.rygel.stellar.seed

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.CommunityRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test

class StellarSeederTest {

    private val communityRepo = mockk<CommunityRepository>(relaxed = true)
    private val postRepo = mockk<PostRepository>(relaxed = true)
    private val commentRepo = mockk<CommentRepository>(relaxed = true)
    private val subscriptionRepo = mockk<SubscriptionRepository>(relaxed = true)
    private val memberRepo = mockk<CommunityMemberRepository>(relaxed = true)

    private val seeder = StellarSeeder(communityRepo, postRepo, commentRepo, subscriptionRepo, memberRepo)
    private val adminId = UUID.randomUUID()

    @Test
    fun `seed creates 3 communities`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 3) { communityRepo.create(any()) }
    }

    @Test
    fun `seed creates 4 posts`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 4) { postRepo.create(any()) }
    }

    @Test
    fun `seed creates 3 comments`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 3) { commentRepo.create(any()) }
    }

    @Test
    fun `seed makes creator admin of each community`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 3) { memberRepo.addMember(adminId, any(), CommunityRole.ADMIN) }
    }

    @Test
    fun `seed subscribes creator to each community`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 3) { subscriptionRepo.subscribe(adminId, any()) }
        verify(exactly = 3) { communityRepo.incrementSubscriberCount(any(), 1) }
    }

    @Test
    fun `seed skips when data already exists`() {
        every { communityRepo.countAll() } returns 5

        seeder.seed(adminId)

        verify(exactly = 0) { communityRepo.create(any()) }
        verify(exactly = 0) { postRepo.create(any()) }
        verify(exactly = 0) { commentRepo.create(any()) }
    }

    @Test
    fun `seed increments comment count for each post with comments`() {
        every { communityRepo.countAll() } returns 0
        every { communityRepo.create(any()) } answers { firstArg<Community>() }
        every { postRepo.create(any()) } answers { firstArg<Post>() }

        seeder.seed(adminId)

        verify(exactly = 3) { postRepo.incrementCommentCount(any(), 1) }
    }
}
