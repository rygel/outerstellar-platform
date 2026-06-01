package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import kotlin.test.Test
import org.http4k.contract.bindContract
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.ResourceLoader

class ExtensionAdminDashboardIntegrationTest : WebTest() {
    @Test
    fun `extension dashboard shows extension diagnostics`() {
        val reportsRoute = ("/extension/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val reportsAdminRoute = ("/admin/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports App"

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.register(reportsRoute, RouteGroup.ProtectedUi, "Reports UI", "/extension/reports")
                    context.routes.staticAssets("/extensions/reports/assets", ResourceLoader.Classpath("static"))
                    context.assets.stylesheet("/extensions/reports/assets/reports.css")
                    context.assets.script("/extensions/reports/assets/reports.js")
                    context.layout.replaceWith(ExtensionLayoutRenderer { _, content -> content })
                    context.admin.section(
                        id = "reports",
                        navLabel = "Reports",
                        navIcon = "bar-chart",
                        route = reportsAdminRoute,
                        linkUrl = "/admin/reports",
                    )
                }
            }

        val app = buildApp(extension = extension)
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val response = app(Request(GET, "/admin/extensions").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("Extension diagnostics"))
        assertThat(body, containsSubstring("Reports App"))
        assertThat(body, containsSubstring("reports"))
        assertThat(body, containsSubstring("/extension/reports"))
        assertThat(body, containsSubstring("/extensions/reports/assets/*"))
        assertThat(body, containsSubstring("/extensions/reports/assets/reports.css"))
        assertThat(body, containsSubstring("version dev"))
        assertThat(body, containsSubstring("Layout"))
        assertThat(body, containsSubstring("Admin sections"))
    }
}
