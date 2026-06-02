package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import io.github.rygel.outerstellar.platform.banner.Banner
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.banner.BannerSeverity
import java.util.UUID
import kotlin.test.Test
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus

class BannerIntegrationTest : WebTest() {

    private class StubBannerProvider(private val banners: List<Banner>) : BannerProvider {
        override fun getBanners(userId: UUID, userRole: String): List<Banner> = banners
    }

    private class TestBannerExtension(private val providers: List<BannerProvider>) : PlatformExtension {
        override val id: String = "test-banner-extension"

        override fun contribute(context: ExtensionContributionContext) {
            providers.forEach { context.banners.provider(it) }
        }
    }

    private val testBanner =
        Banner(
            id = "test-banner-1",
            title = "System Maintenance",
            body = "The system will be down for 2 hours.",
            severity = BannerSeverity.INFO,
            isDismissible = true,
            dismissUrl = "/api/v1/banners/test-banner-1/dismiss",
        )

    @Test
    fun `banners from BannerProvider are rendered in page layout`() {
        val provider = StubBannerProvider(listOf(testBanner))
        val extension = TestBannerExtension(listOf(provider))
        val app = buildApp(extension = extension)

        val (token, _, _) = withAuthenticatedUser()
        val response = app(Request(Method.GET, "/").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("System Maintenance"))
        assertThat(body, containsSubstring("The system will be down for 2 hours."))
        assertThat(body, containsSubstring("alert-info"))
        assertThat(body, containsSubstring("/api/v1/banners/test-banner-1/dismiss"))
    }

    @Test
    fun `no banners rendered when user is not authenticated`() {
        val provider = StubBannerProvider(listOf(testBanner))
        val extension = TestBannerExtension(listOf(provider))
        val app = buildApp(extension = extension)

        val response = app(Request(Method.GET, "/auth"))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, !containsSubstring("System Maintenance"))
    }

    @Test
    fun `critical banners are ordered before info banners`() {
        val infoBanner =
            Banner(
                id = "info-1",
                title = "Info Notice",
                body = "This is informational.",
                severity = BannerSeverity.INFO,
            )
        val criticalBanner =
            Banner(
                id = "critical-1",
                title = "Critical Alert",
                body = "Immediate action required.",
                severity = BannerSeverity.CRITICAL,
                isDismissible = false,
            )
        val provider = StubBannerProvider(listOf(infoBanner, criticalBanner))
        val extension = TestBannerExtension(listOf(provider))
        val app = buildApp(extension = extension)

        val (token, _, _) = withAuthenticatedUser()
        val response = app(Request(Method.GET, "/").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        val criticalIdx = body.indexOf("Critical Alert")
        val infoIdx = body.indexOf("Info Notice")
        assert(criticalIdx >= 0) { "Critical Alert should appear in response" }
        assert(infoIdx >= 0) { "Info Notice should appear in response" }
        assert(criticalIdx < infoIdx) { "Critical banner should appear before info banner in HTML" }
        assertThat(body, containsSubstring("alert-error"))
    }
}
