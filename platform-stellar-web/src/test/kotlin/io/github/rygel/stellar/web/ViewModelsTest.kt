package io.github.rygel.stellar.web

import io.github.rygel.stellar.model.CommunitySummary
import io.github.rygel.stellar.model.PostSummary
import io.github.rygel.stellar.model.PostType
import io.github.rygel.stellar.web.TimeFormatting.toCardViewModel
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ViewModelsTest {

    @Nested
    inner class TimeFormattingTest {

        @Test
        fun `just now for less than 1 minute ago`() {
            val now = Instant.now()
            assertEquals("just now", TimeFormatting.formatTimeAgo(now))
        }

        @Test
        fun `just now for 30 seconds ago`() {
            val thirtySecondsAgo = Instant.now().minusSeconds(30)
            assertEquals("just now", TimeFormatting.formatTimeAgo(thirtySecondsAgo))
        }

        @Test
        fun `minutes format for 5 minutes ago`() {
            val fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES)
            assertEquals("5m ago", TimeFormatting.formatTimeAgo(fiveMinutesAgo))
        }

        @Test
        fun `minutes format for 59 minutes ago`() {
            val fiftyNineMinutesAgo = Instant.now().minus(59, ChronoUnit.MINUTES)
            assertEquals("59m ago", TimeFormatting.formatTimeAgo(fiftyNineMinutesAgo))
        }

        @Test
        fun `hours format for 1 hour ago`() {
            val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS)
            assertEquals("1h ago", TimeFormatting.formatTimeAgo(oneHourAgo))
        }

        @Test
        fun `hours format for 23 hours ago`() {
            val twentyThreeHoursAgo = Instant.now().minus(23, ChronoUnit.HOURS)
            assertEquals("23h ago", TimeFormatting.formatTimeAgo(twentyThreeHoursAgo))
        }

        @Test
        fun `days format for 1 day ago`() {
            val oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS)
            assertEquals("1d ago", TimeFormatting.formatTimeAgo(oneDayAgo))
        }

        @Test
        fun `days format for 29 days ago`() {
            val twentyNineDaysAgo = Instant.now().minus(29, ChronoUnit.DAYS)
            assertEquals("29d ago", TimeFormatting.formatTimeAgo(twentyNineDaysAgo))
        }

        @Test
        fun `months format for 30 days ago`() {
            val thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS)
            assertEquals("1mo ago", TimeFormatting.formatTimeAgo(thirtyDaysAgo))
        }

        @Test
        fun `months format for 180 days ago`() {
            val sixMonthsAgo = Instant.now().minus(180, ChronoUnit.DAYS)
            assertEquals("6mo ago", TimeFormatting.formatTimeAgo(sixMonthsAgo))
        }

        @Test
        fun `years format for 365 days ago`() {
            val oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS)
            assertEquals("1y ago", TimeFormatting.formatTimeAgo(oneYearAgo))
        }

        @Test
        fun `years format for 730 days ago`() {
            val twoYearsAgo = Instant.now().minus(730, ChronoUnit.DAYS)
            assertEquals("2y ago", TimeFormatting.formatTimeAgo(twoYearsAgo))
        }
    }

    @Nested
    inner class PostSummaryToCardViewModelTest {

        private val postId = UUID.randomUUID()
        private val createdAt = Instant.now().minus(3, ChronoUnit.HOURS)

        private val postSummary =
            PostSummary(
                id = postId,
                communityName = "kotlin",
                communityDisplayName = "Kotlin Programming",
                authorUsername = "testuser",
                title = "Hello World",
                previewText = "This is a preview",
                url = "https://example.com",
                postType = PostType.LINK,
                score = 42,
                commentCount = 7,
                flairName = "Discussion",
                flairColor = "#ff0000",
                userVote = 1,
                isPinned = true,
                createdAt = createdAt,
            )

        @Test
        fun `maps id correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(postId, vm.id)
        }

        @Test
        fun `maps communityName correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("kotlin", vm.communityName)
        }

        @Test
        fun `maps communityDisplayName correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("Kotlin Programming", vm.communityDisplayName)
        }

        @Test
        fun `maps authorUsername correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("testuser", vm.authorUsername)
        }

        @Test
        fun `maps title correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("Hello World", vm.title)
        }

        @Test
        fun `maps previewText correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("This is a preview", vm.previewText)
        }

        @Test
        fun `maps url correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("https://example.com", vm.url)
        }

        @Test
        fun `maps postType correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(PostType.LINK, vm.postType)
        }

        @Test
        fun `maps score correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(42, vm.score)
        }

        @Test
        fun `maps commentCount correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(7, vm.commentCount)
        }

        @Test
        fun `maps flairName correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("Discussion", vm.flairName)
        }

        @Test
        fun `maps flairColor correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("#ff0000", vm.flairColor)
        }

        @Test
        fun `maps userVote correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(1, vm.userVote)
        }

        @Test
        fun `maps isPinned correctly`() {
            val vm = postSummary.toCardViewModel()
            assertEquals(true, vm.isPinned)
        }

        @Test
        fun `maps timeAgo from createdAt`() {
            val vm = postSummary.toCardViewModel()
            assertEquals("3h ago", vm.timeAgo)
        }

        @Test
        fun `null optional fields map to null`() {
            val minimal =
                postSummary.copy(previewText = null, url = null, flairName = null, flairColor = null, userVote = null)
            val vm = minimal.toCardViewModel()
            assertNull(vm.previewText)
            assertNull(vm.url)
            assertNull(vm.flairName)
            assertNull(vm.flairColor)
            assertNull(vm.userVote)
        }
    }

    @Nested
    inner class CommunitySummaryToCardViewModelTest {

        private val communityId = UUID.randomUUID()

        private val communitySummary =
            CommunitySummary(
                id = communityId,
                name = "kotlin",
                displayName = "Kotlin Programming",
                description = "All about Kotlin",
                iconUrl = "https://example.com/icon.png",
                subscriberCount = 15000L,
                isSubscribed = true,
            )

        @Test
        fun `maps id correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals(communityId, vm.id)
        }

        @Test
        fun `maps name correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals("kotlin", vm.name)
        }

        @Test
        fun `maps displayName correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals("Kotlin Programming", vm.displayName)
        }

        @Test
        fun `maps description correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals("All about Kotlin", vm.description)
        }

        @Test
        fun `maps iconUrl correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals("https://example.com/icon.png", vm.iconUrl)
        }

        @Test
        fun `maps subscriberCount correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals(15000L, vm.subscriberCount)
        }

        @Test
        fun `maps isSubscribed correctly`() {
            val vm = communitySummary.toCardViewModel()
            assertEquals(true, vm.isSubscribed)
        }

        @Test
        fun `null iconUrl maps to null`() {
            val noIcon = communitySummary.copy(iconUrl = null)
            val vm = noIcon.toCardViewModel()
            assertNull(vm.iconUrl)
        }

        @Test
        fun `default isSubscribed is false`() {
            val notSubscribed = communitySummary.copy(isSubscribed = false)
            val vm = notSubscribed.toCardViewModel()
            assertEquals(false, vm.isSubscribed)
        }
    }

    @Nested
    inner class ViewModelTemplatePathTest {

        @Test
        fun `FeedPage template path`() {
            val page = FeedPage("home", "hot", null, emptyList(), emptyList())
            assertEquals("io/github/rygel/stellar/web/FeedPage", page.template())
        }

        @Test
        fun `CommunityPage template path`() {
            val detail = CommunityDetailViewModel(UUID.randomUUID(), "test", "Test", "desc", "sidebar", 0, 0)
            val page = CommunityPage(detail, emptyList(), "hot", null, false)
            assertEquals("io/github/rygel/stellar/web/CommunityPage", page.template())
        }

        @Test
        fun `PostDetailPage template path`() {
            val post =
                PostDetailViewModel(
                    UUID.randomUUID(),
                    "test",
                    "author",
                    "title",
                    null,
                    null,
                    PostType.TEXT,
                    0,
                    0,
                    null,
                    false,
                    "just now",
                )
            val page = PostDetailPage(post, emptyList(), "best", "test")
            assertEquals("io/github/rygel/stellar/web/PostDetailPage", page.template())
        }

        @Test
        fun `SubmitPostPage template path`() {
            val page = SubmitPostPage("test", "Test", UUID.randomUUID())
            assertEquals("io/github/rygel/stellar/web/SubmitPostPage", page.template())
        }

        @Test
        fun `CommunitiesPage template path`() {
            val page = CommunitiesPage(emptyList())
            assertEquals("io/github/rygel/stellar/web/CommunitiesPage", page.template())
        }

        @Test
        fun `SearchResultsPage template path`() {
            val page = SearchResultsPage("query", emptyList(), emptyList())
            assertEquals("io/github/rygel/stellar/web/SearchResultsPage", page.template())
        }

        @Test
        fun `VoteButtonsFragment template path`() {
            val fragment = VoteButtonsFragment(UUID.randomUUID(), "POST", 5, 1)
            assertEquals("io/github/rygel/stellar/web/components/VoteButtons", fragment.template())
        }

        @Test
        fun `FeedListFragment template path`() {
            val fragment = FeedListFragment(emptyList(), false, 0, "hot", "home")
            assertEquals("io/github/rygel/stellar/web/components/FeedList", fragment.template())
        }

        @Test
        fun `SubscribeButtonFragment template path`() {
            val fragment = SubscribeButtonFragment(UUID.randomUUID(), "test", true)
            assertEquals("io/github/rygel/stellar/web/components/SubscribeButton", fragment.template())
        }
    }
}
