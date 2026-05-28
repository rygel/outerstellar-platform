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

class PluginAdminDashboardIntegrationTest : WebTest() {
    @Test
    fun `plugin dashboard shows hosted app diagnostics`() {
        val reportsRoute = ("/plugin/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val reportsAdminRoute = ("/admin/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val plugin =
            object : PlatformPlugin {
                override val id = "reports"
                override val appLabel = "Reports App"

                override fun contribute(context: HostedAppContributionContext) {
                    context.routes.register(reportsRoute, RouteGroup.ProtectedUi, "Reports UI", "/plugin/reports")
                    context.routes.staticAssets("/plugins/reports/assets", ResourceLoader.Classpath("static"))
                    context.assets.stylesheet("/plugins/reports/assets/reports.css")
                    context.assets.script("/plugins/reports/assets/reports.js")
                    context.layout.replaceWith(PluginLayoutRenderer { _, content -> content })
                    context.admin.section(
                        id = "reports",
                        navLabel = "Reports",
                        navIcon = "bar-chart",
                        route = reportsAdminRoute,
                        linkUrl = "/admin/reports",
                    )
                }
            }

        val app = buildApp(plugin = plugin)
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val response = app(Request(GET, "/admin/plugins").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("Hosted app diagnostics"))
        assertThat(body, containsSubstring("Reports App"))
        assertThat(body, containsSubstring("reports"))
        assertThat(body, containsSubstring("/plugin/reports"))
        assertThat(body, containsSubstring("/plugins/reports/assets/*"))
        assertThat(body, containsSubstring("/plugins/reports/assets/reports.css"))
        assertThat(body, containsSubstring("Layout"))
        assertThat(body, containsSubstring("Admin sections"))
    }
}
