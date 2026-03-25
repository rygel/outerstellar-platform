package io.github.rygel.stellar.model

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SummariesTest {

    @Test
    fun `PostSummary has correct defaults`() {
        val summary =
            PostSummary(
                id = UUID.randomUUID(),
                communityName = "kotlin",
                communityDisplayName = "Kotlin",
                authorUsername = "testuser",
                title = "Test post",
                postType = PostType.TEXT,
                score = 10,
                commentCount = 5,
                createdAt = Instant.now(),
            )

        assertNull(summary.userVote)
        assertFalse(summary.isSaved)
        assertFalse(summary.isPinned)
        assertNull(summary.previewText)
        assertNull(summary.url)
        assertNull(summary.thumbnailUrl)
        assertNull(summary.flairName)
        assertNull(summary.flairColor)
    }

    @Test
    fun `CommentSummary has correct defaults`() {
        val summary =
            CommentSummary(
                id = UUID.randomUUID(),
                postId = UUID.randomUUID(),
                authorUsername = "testuser",
                body = "Hello world",
                score = 5,
                depth = 0,
                createdAt = Instant.now(),
            )

        assertNull(summary.userVote)
        assertFalse(summary.isSaved)
        assertFalse(summary.isRemoved)
        assertEquals(0, summary.replyCount)
    }

    @Test
    fun `CommentTree with no children has empty children list`() {
        val summary =
            CommentSummary(
                id = UUID.randomUUID(),
                postId = UUID.randomUUID(),
                authorUsername = "user1",
                body = "Top level",
                score = 1,
                depth = 0,
                createdAt = Instant.now(),
            )
        val tree = CommentTree(comment = summary)

        assertTrue(tree.children.isEmpty())
    }

    @Test
    fun `CommentTree with children`() {
        val postId = UUID.randomUUID()
        val now = Instant.now()

        val child =
            CommentTree(
                comment =
                    CommentSummary(
                        id = UUID.randomUUID(),
                        postId = postId,
                        authorUsername = "replier",
                        body = "Reply",
                        score = 0,
                        depth = 1,
                        createdAt = now,
                    )
            )

        val parent =
            CommentTree(
                comment =
                    CommentSummary(
                        id = UUID.randomUUID(),
                        postId = postId,
                        authorUsername = "poster",
                        body = "Top level",
                        score = 3,
                        depth = 0,
                        replyCount = 1,
                        createdAt = now,
                    ),
                children = listOf(child),
            )

        assertEquals(1, parent.children.size)
        assertEquals("Reply", parent.children[0].comment.body)
        assertEquals(1, parent.comment.replyCount)
    }

    @Test
    fun `CommunitySummary has correct defaults`() {
        val summary =
            CommunitySummary(
                id = UUID.randomUUID(),
                name = "kotlin",
                displayName = "Kotlin",
                description = "A community for Kotlin",
                subscriberCount = 1000,
            )

        assertFalse(summary.isSubscribed)
        assertNull(summary.iconUrl)
    }

    @Test
    fun `PostSort enum has expected values`() {
        val values = PostSort.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(PostSort.HOT))
        assertTrue(values.contains(PostSort.NEW))
        assertTrue(values.contains(PostSort.TOP))
        assertTrue(values.contains(PostSort.RISING))
        assertTrue(values.contains(PostSort.CONTROVERSIAL))
    }

    @Test
    fun `CommentSort enum has expected values`() {
        val values = CommentSort.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(CommentSort.BEST))
        assertTrue(values.contains(CommentSort.NEW))
        assertTrue(values.contains(CommentSort.TOP))
        assertTrue(values.contains(CommentSort.CONTROVERSIAL))
    }

    @Test
    fun `TimeRange enum has expected values`() {
        val values = TimeRange.entries
        assertEquals(6, values.size)
        assertTrue(values.contains(TimeRange.HOUR))
        assertTrue(values.contains(TimeRange.DAY))
        assertTrue(values.contains(TimeRange.WEEK))
        assertTrue(values.contains(TimeRange.MONTH))
        assertTrue(values.contains(TimeRange.YEAR))
        assertTrue(values.contains(TimeRange.ALL))
    }
}
