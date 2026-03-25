package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.BannedFromCommunityException
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostNotFoundException
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PostServiceTest {

    private lateinit var postRepo: PostRepository
    private lateinit var memberRepo: CommunityMemberRepository
    private lateinit var service: PostService

    @BeforeEach
    fun setUp() {
        postRepo = mockk()
        memberRepo = mockk()
        service = PostService(postRepo, memberRepo)
    }

    @Test
    fun `getPost returns post when found`() {
        val postId = UUID.randomUUID()
        val post = post(id = postId, title = "Hello World")
        every { postRepo.findById(postId) } returns post

        val result = service.getPost(postId)

        assertEquals("Hello World", result.title)
        assertEquals(postId, result.id)
    }

    @Test
    fun `getPost throws PostNotFoundException when not found`() {
        val postId = UUID.randomUUID()
        every { postRepo.findById(postId) } returns null

        assertThrows<PostNotFoundException> { service.getPost(postId) }
    }

    @Test
    fun `createPost succeeds when user is not banned`() {
        val communityId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val created = post(communityId = communityId, authorId = authorId, title = "My Post")

        every { memberRepo.isBanned(authorId, communityId) } returns false
        every { postRepo.create(any()) } returns created

        val result = service.createPost(communityId, authorId, "My Post", body = "Some content")

        assertNotNull(result)
        assertEquals("My Post", result.title)
        verify { postRepo.create(any()) }
    }

    @Test
    fun `createPost throws BannedFromCommunityException when user is banned`() {
        val communityId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        every { memberRepo.isBanned(authorId, communityId) } returns true

        assertThrows<BannedFromCommunityException> { service.createPost(communityId, authorId, "My Post") }

        verify(exactly = 0) { postRepo.create(any()) }
    }

    @Test
    fun `createPost trims title and body`() {
        val communityId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        every { memberRepo.isBanned(authorId, communityId) } returns false
        every { postRepo.create(any()) } answers { firstArg() }

        val result = service.createPost(communityId, authorId, "  Padded Title  ", body = "  Padded Body  ")

        assertEquals("Padded Title", result.title)
        assertEquals("Padded Body", result.body)
    }

    @Test
    fun `createPost passes correct post type`() {
        val communityId = UUID.randomUUID()
        val authorId = UUID.randomUUID()

        every { memberRepo.isBanned(authorId, communityId) } returns false
        every { postRepo.create(any()) } answers { firstArg() }

        val result =
            service.createPost(
                communityId,
                authorId,
                "Link Post",
                url = "https://example.com",
                postType = PostType.LINK,
            )

        assertEquals(PostType.LINK, result.postType)
        assertEquals("https://example.com", result.url)
    }

    private fun post(
        id: UUID = UUID.randomUUID(),
        communityId: UUID = UUID.randomUUID(),
        authorId: UUID = UUID.randomUUID(),
        title: String = "Test Post",
    ) = Post(id = id, communityId = communityId, authorId = authorId, title = title)
}
