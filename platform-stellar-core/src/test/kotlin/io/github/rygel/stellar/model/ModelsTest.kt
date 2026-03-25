package io.github.rygel.stellar.model

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ModelsTest {

    @Test
    fun `Community has correct defaults`() {
        val id = UUID.randomUUID()
        val creatorId = UUID.randomUUID()
        val community = Community(id = id, name = "test", displayName = "Test", creatorId = creatorId)

        assertEquals("", community.description)
        assertEquals("", community.sidebar)
        assertNull(community.iconUrl)
        assertNull(community.bannerUrl)
        assertFalse(community.isNsfw)
        assertEquals(0, community.subscriberCount)
    }

    @Test
    fun `Post has correct defaults`() {
        val post =
            Post(
                id = UUID.randomUUID(),
                communityId = UUID.randomUUID(),
                authorId = UUID.randomUUID(),
                title = "Test post",
            )

        assertEquals(0, post.score)
        assertEquals(0, post.commentCount)
        assertFalse(post.isLocked)
        assertFalse(post.isPinned)
        assertFalse(post.isRemoved)
        assertNull(post.body)
        assertNull(post.url)
        assertNull(post.imageUrl)
        assertNull(post.flairId)
        assertEquals(PostType.TEXT, post.postType)
    }

    @Test
    fun `Comment has correct defaults`() {
        val comment =
            Comment(id = UUID.randomUUID(), postId = UUID.randomUUID(), authorId = UUID.randomUUID(), body = "Hello")

        assertEquals(0, comment.score)
        assertEquals(0, comment.depth)
        assertFalse(comment.isRemoved)
        assertNull(comment.parentCommentId)
    }

    @Test
    fun `Vote can hold value of plus one`() {
        val vote =
            Vote(userId = UUID.randomUUID(), targetId = UUID.randomUUID(), targetType = VoteTarget.POST, value = 1)
        assertEquals(1, vote.value)
    }

    @Test
    fun `Vote can hold value of minus one`() {
        val vote =
            Vote(userId = UUID.randomUUID(), targetId = UUID.randomUUID(), targetType = VoteTarget.COMMENT, value = -1)
        assertEquals(-1, vote.value)
    }

    @Test
    fun `UserKarma totalKarma computes postKarma plus commentKarma`() {
        val karma = UserKarma(userId = UUID.randomUUID(), postKarma = 100, commentKarma = 50)
        assertEquals(150, karma.totalKarma)
    }

    @Test
    fun `UserKarma totalKarma handles negative values`() {
        val karma = UserKarma(userId = UUID.randomUUID(), postKarma = -10, commentKarma = 30)
        assertEquals(20, karma.totalKarma)
    }

    @Test
    fun `UserKarma totalKarma defaults to zero`() {
        val karma = UserKarma(userId = UUID.randomUUID())
        assertEquals(0, karma.totalKarma)
    }

    @Test
    fun `CommunityBan has correct defaults`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()
        val bannedBy = UUID.randomUUID()
        val ban = CommunityBan(userId = userId, communityId = communityId, bannedBy = bannedBy)

        assertNull(ban.reason)
        assertEquals(userId, ban.userId)
        assertEquals(communityId, ban.communityId)
        assertEquals(bannedBy, ban.bannedBy)
    }

    @Test
    fun `Post copy works correctly for isLocked`() {
        val post =
            Post(
                id = UUID.randomUUID(),
                communityId = UUID.randomUUID(),
                authorId = UUID.randomUUID(),
                title = "Original",
            )
        assertFalse(post.isLocked)

        val locked = post.copy(isLocked = true)
        assertTrue(locked.isLocked)
        assertEquals(post.id, locked.id)
        assertEquals(post.title, locked.title)
        assertEquals(post.score, locked.score)
    }

    @Test
    fun `Post copy works correctly for isPinned`() {
        val post =
            Post(
                id = UUID.randomUUID(),
                communityId = UUID.randomUUID(),
                authorId = UUID.randomUUID(),
                title = "Original",
            )

        val pinned = post.copy(isPinned = true)
        assertTrue(pinned.isPinned)
        assertFalse(pinned.isLocked)
    }

    @Test
    fun `Post copy works correctly for isRemoved`() {
        val post =
            Post(
                id = UUID.randomUUID(),
                communityId = UUID.randomUUID(),
                authorId = UUID.randomUUID(),
                title = "Original",
            )

        val removed = post.copy(isRemoved = true)
        assertTrue(removed.isRemoved)
        assertEquals(post.title, removed.title)
    }

    @Test
    fun `Post copy with score preserves other fields`() {
        val post =
            Post(
                id = UUID.randomUUID(),
                communityId = UUID.randomUUID(),
                authorId = UUID.randomUUID(),
                title = "Test",
                score = 10,
                commentCount = 5,
            )

        val updated = post.copy(score = 42)
        assertEquals(42, updated.score)
        assertEquals(5, updated.commentCount)
        assertEquals(post.title, updated.title)
    }
}
