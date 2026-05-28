package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.banner.Banner
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.http4k.contract.bindContract
import org.http4k.core.Filter
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.ResourceLoader
import org.http4k.template.TemplateRenderer

class PluginContributionTest {
    @Test
    fun `empty contribution keeps host mode defaults`() {
        val contribution =
            HostedAppContribution.from(plugin = null, fallbackMode = PlatformMode.HeadlessKernel, context = null)

        assertEquals(PlatformMode.HeadlessKernel, contribution.mode)
        assertEquals("Outerstellar", contribution.appLabel)
        assertEquals(emptySet(), contribution.includedPlatformPages)
        assertEquals(emptyList(), contribution.routeRegistrations)
        assertEquals(emptyList(), contribution.filters)
        assertEquals(emptyList(), contribution.adminSections)
        assertEquals(emptyList(), contribution.bannerProviders)
        assertNull(contribution.options.textResolver)
        assertNull(contribution.options.layoutRenderer)
    }

    @Test
    fun `collects plugin extension points once into a single contribution`() {
        val context = pluginContext()
        val textResolver =
            object : TextResolver {
                override fun resolve(key: String, vararg args: Any?): String = "plugin:$key"
            }
        val layoutRenderer = PluginLayoutRenderer { _, content -> content }
        val filter = Filter { next -> { request -> next(request) } }
        val bannerProvider =
            object : BannerProvider {
                override fun getBanners(userId: UUID, userRole: String): List<Banner> = emptyList()
            }
        val routeRegistration =
            PluginRouteRegistration(
                route = ("/plugin/tools" bindContract GET).to { _ -> Response(Status.OK) },
                group = RouteGroup.ProtectedUi,
                description = "Plugin tools",
                pathPattern = "/plugin/tools",
            )
        val adminSection =
            AdminSection(
                id = "tools",
                navLabel = "Tools",
                navIcon = "wrench",
                summaryCard =
                    AdminSummaryCard(
                        title = "Tools",
                        metrics = emptyList(),
                        linkLabel = "Open",
                        linkUrl = "/admin/tools",
                    ),
                route = ("/admin/tools" bindContract GET).to { _ -> Response(Status.OK) },
            )
        val plugin =
            object : PlatformPlugin {
                var routeCalls = 0
                var filterCalls = 0
                var adminCalls = 0
                var bannerCalls = 0
                var layoutCalls = 0

                override val id = "tools"
                override val appLabel = "Tools App"
                override val mode = PlatformMode.PluginHostedApp
                override val textResolver = textResolver

                override fun includePlatformPages(): Set<PlatformPageSets> = setOf(PlatformPageSets.HOME)

                override fun routeRegistrations(context: HostedAppContext): List<PluginRouteRegistration> {
                    routeCalls += 1
                    return listOf(routeRegistration)
                }

                override fun filters(context: HostedAppContext): List<Filter> {
                    filterCalls += 1
                    return listOf(filter)
                }

                override fun adminSections(context: HostedAppContext): List<AdminSection> {
                    adminCalls += 1
                    return listOf(adminSection)
                }

                override fun bannerProviders(context: HostedAppContext): List<BannerProvider> {
                    bannerCalls += 1
                    return listOf(bannerProvider)
                }

                override fun layoutRenderer(context: HostedAppContext): PluginLayoutRenderer? {
                    layoutCalls += 1
                    return layoutRenderer
                }
            }

        val contribution = HostedAppContribution.from(plugin, PlatformMode.FullPlatformApp, context)

        assertEquals(PlatformMode.PluginHostedApp, contribution.mode)
        assertEquals("Tools App", contribution.appLabel)
        assertEquals(setOf(PlatformPageSets.HOME), contribution.includedPlatformPages)
        assertEquals(listOf(routeRegistration), contribution.routeRegistrations)
        assertEquals(listOf(filter), contribution.filters)
        assertEquals(listOf(adminSection), contribution.adminSections)
        assertEquals(listOf(bannerProvider), contribution.bannerProviders)
        assertSame(textResolver, contribution.options.textResolver)
        assertSame(layoutRenderer, contribution.options.layoutRenderer)
        assertEquals(listOf(AdminNavItem("Tools", "/admin/tools", "wrench")), contribution.options.adminNavItems)
        assertEquals(1, plugin.routeCalls)
        assertEquals(1, plugin.filterCalls)
        assertEquals(1, plugin.adminCalls)
        assertEquals(1, plugin.bannerCalls)
        assertEquals(1, plugin.layoutCalls)
    }

    @Test
    fun `new contribution hook registers typed plugin capabilities`() {
        val pluginHostContext = pluginContext()
        val layoutRenderer = PluginLayoutRenderer { _, content -> content }
        val filter = Filter { next -> { request -> next(request) } }
        val bannerProvider =
            object : BannerProvider {
                override fun getBanners(userId: UUID, userRole: String): List<Banner> = emptyList()
            }
        val reportsRoute = ("/plugin/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val routeRegistration =
            PluginRouteRegistration(
                route = reportsRoute,
                group = RouteGroup.ProtectedUi,
                description = "Plugin reports",
                pathPattern = "/plugin/reports",
            )
        val adminSection =
            AdminSection(
                id = "reports",
                navLabel = "Reports",
                navIcon = "bar-chart",
                summaryCard =
                    AdminSummaryCard(
                        title = "Reports",
                        metrics = emptyList(),
                        linkLabel = "Open",
                        linkUrl = "/admin/reports",
                    ),
                route = ("/admin/reports" bindContract GET).to { _ -> Response(Status.OK) },
            )
        val navItem = PluginNavItem("Reports", "/reports", "bar-chart")
        val plugin =
            object : PlatformPlugin {
                var contributeCalls = 0

                override val id = "reports"
                override val appLabel = "Reports App"
                override val mode = PlatformMode.PluginHostedApp

                override fun contribute(context: HostedAppContributionContext) {
                    contributeCalls += 1
                    assertSame(pluginHostContext, context.host)
                    context.platformPages.include(PlatformPageSets.SEARCH, PlatformPageSets.PROFILE)
                    context.routes.protectedUi(reportsRoute, routeRegistration.description, "/plugin/reports")
                    context.routes.staticAssets(
                        "/plugins/reports/assets",
                        ResourceLoader.Classpath("reports-static"),
                        "Reports static assets",
                    )
                    context.filters.add(filter)
                    context.admin.section(
                        id = adminSection.id,
                        navLabel = adminSection.navLabel,
                        navIcon = adminSection.navIcon,
                        route = adminSection.route,
                        title = adminSection.summaryCard.title,
                        linkUrl = adminSection.summaryCard.linkUrl,
                        metrics = adminSection.summaryCard.metrics,
                        linkLabel = adminSection.summaryCard.linkLabel,
                    )
                    context.banners.provider(bannerProvider)
                    context.navigation.item(navItem.label, navItem.url, navItem.icon)
                    context.layout.replaceWith(layoutRenderer)
                    context.assets.stylesheet("/plugins/reports/assets/reports.css")
                    context.assets.script("/plugins/reports/assets/reports.js")
                }
            }

        val contribution = HostedAppContribution.from(plugin, PlatformMode.FullPlatformApp, pluginHostContext)

        assertEquals(PlatformMode.PluginHostedApp, contribution.mode)
        assertEquals("Reports App", contribution.appLabel)
        assertEquals(setOf(PlatformPageSets.SEARCH, PlatformPageSets.PROFILE), contribution.includedPlatformPages)
        assertEquals(routeRegistration, contribution.routeRegistrations[0])
        assertEquals(RouteGroup.Static, contribution.routeRegistrations[1].group)
        assertEquals("Reports static assets", contribution.routeRegistrations[1].description)
        assertEquals("/plugins/reports/assets/*", contribution.routeRegistrations[1].pathPattern)
        assertEquals("GET", contribution.routeRegistrations[1].method)
        assertEquals(listOf(filter), contribution.filters)
        assertEquals(listOf(adminSection), contribution.adminSections)
        assertEquals(listOf(bannerProvider), contribution.bannerProviders)
        assertEquals(listOf(navItem), contribution.options.navItems)
        assertSame(layoutRenderer, contribution.options.layoutRenderer)
        assertEquals(listOf("/plugins/reports/assets/reports.css"), contribution.options.assets.stylesheets)
        assertEquals(listOf("/plugins/reports/assets/reports.js"), contribution.options.assets.scripts)
        assertEquals(listOf(AdminNavItem("Reports", "/admin/reports", "bar-chart")), contribution.options.adminNavItems)
        assertEquals(1, plugin.contributeCalls)

        val diagnostics = contribution.diagnostics()
        assertEquals("reports", diagnostics.hostedAppId)
        assertEquals("Reports App", diagnostics.appLabel)
        assertEquals("PluginHostedApp", diagnostics.mode)
        assertEquals(listOf("profile", "search"), diagnostics.includedPlatformPages)
        assertEquals("/plugin/reports", diagnostics.routes[0].pathPattern)
        assertEquals("/plugins/reports/assets/*", diagnostics.routes[1].pathPattern)
        assertEquals(2, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "layout" }.count)
        assertEquals(2, diagnostics.capabilities.single { it.id == "assets" }.count)
        assertEquals(listOf("/reports", "/plugin/reports"), diagnostics.ownership?.uiPrefixes)
    }

    @Test
    fun `hosted app manifest rejects routes outside owned prefixes`() {
        val plugin =
            object : PlatformPlugin {
                override val id = "reports"
                override val appLabel = "Reports App"

                override fun contribute(context: HostedAppContributionContext) {
                    val route = ("/other" bindContract GET).to { _ -> Response(Status.OK) }
                    context.routes.protectedUi(route, "Other route", "/other")
                }
            }

        val error =
            assertFailsWith<IllegalArgumentException> {
                HostedAppContribution.from(plugin, PlatformMode.FullPlatformApp, pluginContext())
            }

        assertEquals(
            "Route * /other (Other route) is outside hosted app 'reports' ownership. " +
                "Allowed prefixes: /reports, /plugin/reports",
            error.message,
        )
    }

    @Test
    fun `hosted app manifest can declare custom ownership prefixes`() {
        val customRoute = ("/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val plugin =
            object : PlatformPlugin {
                override val id = "reports"
                override val manifest =
                    HostedAppManifest(
                        id = id,
                        appLabel = "Reports App",
                        ownership =
                            HostedAppOwnership(
                                uiPrefixes = listOf("/reports"),
                                apiPrefixes = listOf("/api/reports"),
                                adminPrefixes = listOf("/admin/reports"),
                                assetPrefixes = listOf("/assets/reports"),
                            ),
                    )

                override fun contribute(context: HostedAppContributionContext) {
                    context.routes.protectedUi(customRoute, "Reports", "/reports")
                    context.assets.stylesheet("/assets/reports/reports.css")
                }
            }

        val contribution = HostedAppContribution.from(plugin, PlatformMode.FullPlatformApp, pluginContext())

        assertEquals("Reports App", contribution.appLabel)
        assertEquals(plugin.manifest, contribution.manifest)
        assertEquals("/reports", contribution.routeRegistrations.single().pathPattern)
        assertEquals(listOf("/assets/reports/reports.css"), contribution.options.assets.stylesheets)
    }

    private fun pluginContext(): HostedAppContext {
        val context =
            HostedAppContext.forTesting(
                renderer = mockk<TemplateRenderer>(relaxed = true),
                apiKeyService = mockk<ApiKeyService>(relaxed = true),
                oauthService = mockk<OAuthService>(relaxed = true),
                userRepository = mockk<UserRepository>(relaxed = true),
            )
        assertNotNull(context)
        return context
    }
}
