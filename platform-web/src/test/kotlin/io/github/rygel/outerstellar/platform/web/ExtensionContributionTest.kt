package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.banner.Banner
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.extension.ExtensionHostContext
import io.github.rygel.outerstellar.platform.extension.HostApiKeys
import io.github.rygel.outerstellar.platform.extension.HostOAuth
import io.github.rygel.outerstellar.platform.extension.HostRendering
import io.github.rygel.outerstellar.platform.extension.HostSecurity
import io.github.rygel.outerstellar.platform.extension.HostUsers
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

class ExtensionContributionTest {
    @Test
    fun `empty contribution keeps host mode defaults`() {
        val contribution =
            ExtensionContribution.from(extension = null, fallbackMode = PlatformMode.Headless, context = null)

        assertEquals(PlatformMode.Headless, contribution.mode)
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
    fun `collects extension capabilities via contribute hook`() {
        val context = extensionContext()
        val textResolver =
            object : TextResolver {
                override fun resolve(key: String, vararg args: Any?): String = "extension:$key"
            }
        val layoutRenderer = ExtensionLayoutRenderer { _, content -> content }
        val filter = Filter { next -> { request -> next(request) } }
        val bannerProvider =
            object : BannerProvider {
                override fun getBanners(userId: UUID, userRole: String): List<Banner> = emptyList()
            }
        val routeRegistration =
            ExtensionRouteRegistration(
                route = ("/extension/tools" bindContract GET).to { _ -> Response(Status.OK) },
                group = RouteGroup.ProtectedUi,
                description = "Extension tools",
                pathPattern = "/extension/tools",
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
        val extension =
            object : PlatformExtension {
                override val id = "tools"
                override val appLabel = "Tools App"
                override val mode = PlatformMode.ExtensionHost
                override val textResolver = textResolver

                override fun contribute(ctx: ExtensionContributionContext) {
                    ctx.platformPages.include(PlatformPageSets.HOME)
                    ctx.routes.register(routeRegistration)
                    ctx.filters.add(filter)
                    ctx.admin.section(adminSection)
                    ctx.banners.provider(bannerProvider)
                    ctx.layout.replaceWith(layoutRenderer)
                    ctx.templates.override("some-template")
                }
            }

        val contribution = ExtensionContribution.from(extension, PlatformMode.FullPlatform, context)

        assertEquals(PlatformMode.ExtensionHost, contribution.mode)
        assertEquals("Tools App", contribution.appLabel)
        assertEquals(setOf(PlatformPageSets.HOME), contribution.includedPlatformPages)
        assertEquals(listOf(routeRegistration), contribution.routeRegistrations)
        assertEquals(listOf(filter), contribution.filters)
        assertEquals(listOf(adminSection), contribution.adminSections)
        assertEquals(listOf(bannerProvider), contribution.bannerProviders)
        assertSame(textResolver, contribution.options.textResolver)
        assertSame(layoutRenderer, contribution.options.layoutRenderer)
        assertEquals(setOf("some-template"), contribution.templateOverrides)
        assertEquals(
            listOf(io.github.rygel.outerstellar.platform.extension.AdminNavItem("Tools", "/admin/tools", "wrench")),
            contribution.options.adminNavItems,
        )
    }

    @Test
    fun `new contribution hook registers typed extension capabilities`() {
        val extensionHostContext = extensionContext()
        val layoutRenderer = ExtensionLayoutRenderer { _, content -> content }
        val filter = Filter { next -> { request -> next(request) } }
        val bannerProvider =
            object : BannerProvider {
                override fun getBanners(userId: UUID, userRole: String): List<Banner> = emptyList()
            }
        val reportsRoute = ("/extension/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val routeRegistration =
            ExtensionRouteRegistration(
                route = reportsRoute,
                group = RouteGroup.ProtectedUi,
                description = "Extension reports",
                pathPattern = "/extension/reports",
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
        val navItem = ExtensionNavItem("Reports", "/reports", "bar-chart")
        val extension =
            object : PlatformExtension {
                var contributeCalls = 0

                override val id = "reports"
                override val appLabel = "Reports App"
                override val mode = PlatformMode.ExtensionHost

                override fun contribute(context: ExtensionContributionContext) {
                    contributeCalls += 1
                    assertSame(extensionHostContext, context.host)
                    context.platformPages.include(PlatformPageSets.SEARCH, PlatformPageSets.PROFILE)
                    context.routes.protectedUi(reportsRoute, routeRegistration.description, "/extension/reports")
                    context.routes.staticAssets(
                        "/extensions/reports/assets",
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
                    context.assets.stylesheet("/extensions/reports/assets/reports.css")
                    context.assets.script("/extensions/reports/assets/reports.js")
                }
            }

        val contribution = ExtensionContribution.from(extension, PlatformMode.FullPlatform, extensionHostContext)

        assertEquals(PlatformMode.ExtensionHost, contribution.mode)
        assertEquals("Reports App", contribution.appLabel)
        assertEquals(setOf(PlatformPageSets.SEARCH, PlatformPageSets.PROFILE), contribution.includedPlatformPages)
        assertEquals(routeRegistration, contribution.routeRegistrations[0])
        assertEquals(RouteGroup.Static, contribution.routeRegistrations[1].group)
        assertEquals("Reports static assets", contribution.routeRegistrations[1].description)
        assertEquals("/extensions/reports/assets/*", contribution.routeRegistrations[1].pathPattern)
        assertEquals("GET", contribution.routeRegistrations[1].method)
        assertEquals(listOf("/", "/reports", "/extension/reports"), contribution.effectiveOwnership?.uiPrefixes)
        assertEquals(listOf(filter), contribution.filters)
        assertEquals(listOf(adminSection), contribution.adminSections)
        assertEquals(listOf(bannerProvider), contribution.bannerProviders)
        assertEquals(
            listOf(
                io.github.rygel.outerstellar.platform.extension.ExtensionNavItem("Reports", "/reports", "bar-chart")
            ),
            contribution.options.navItems,
        )
        assertSame(layoutRenderer, contribution.options.layoutRenderer)
        assertEquals(listOf("/extensions/reports/assets/reports.css"), contribution.options.assets.stylesheets)
        assertEquals(listOf("/extensions/reports/assets/reports.js"), contribution.options.assets.scripts)
        assertEquals(
            listOf(
                io.github.rygel.outerstellar.platform.extension.AdminNavItem("Reports", "/admin/reports", "bar-chart")
            ),
            contribution.options.adminNavItems,
        )
        assertEquals(1, extension.contributeCalls)

        val diagnostics = contribution.diagnostics()
        assertEquals("reports", diagnostics.extensionId)
        assertEquals("Reports App", diagnostics.appLabel)
        assertEquals("ExtensionHost", diagnostics.mode)
        assertEquals(listOf("profile", "search"), diagnostics.includedPlatformPages)
        assertEquals("/extension/reports", diagnostics.routes[0].pathPattern)
        assertEquals("/extensions/reports/assets/*", diagnostics.routes[1].pathPattern)
        assertEquals(2, diagnostics.capabilities.single { it.id == "routes" }.count)
        assertEquals(1, diagnostics.capabilities.single { it.id == "layout" }.count)
        assertEquals(2, diagnostics.capabilities.single { it.id == "assets" }.count)
        assertEquals(listOf("/", "/reports", "/extension/reports"), diagnostics.ownership?.uiPrefixes)
        assertEquals("/reports", ShellConfig.from(contribution).appHomeUrl)
    }

    @Test
    fun `extension host grants root ui ownership by default`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports App"
                override val mode = PlatformMode.ExtensionHost

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.protectedUi(
                        ("/" bindContract GET).to { _ -> Response(Status.OK) },
                        "Reports home",
                        "/",
                    )
                    context.routes.protectedUi(
                        ("/dashboard" bindContract GET).to { _ -> Response(Status.OK) },
                        "Reports dashboard",
                        "/dashboard",
                    )
                }
            }

        val contribution = ExtensionContribution.from(extension, PlatformMode.FullPlatform, extensionContext())

        assertEquals(listOf("/", "/dashboard"), contribution.routeRegistrations.map { it.pathPattern })
        assertEquals(listOf("/", "/reports", "/extension/reports"), contribution.effectiveOwnership?.uiPrefixes)
        assertEquals("/", ShellConfig.from(contribution).appHomeUrl)
    }

    @Test
    fun `extension manifest rejects routes outside owned prefixes`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports App"

                override fun contribute(context: ExtensionContributionContext) {
                    val route = ("/other" bindContract GET).to { _ -> Response(Status.OK) }
                    context.routes.protectedUi(route, "Other route", "/other")
                }
            }

        val error =
            assertFailsWith<IllegalArgumentException> {
                ExtensionContribution.from(extension, PlatformMode.FullPlatform, extensionContext())
            }

        val message = error.message.orEmpty()
        assert(message.contains("Route * /other (Other route) is outside extension 'reports' ownership")) { message }
        assert(message.contains("Allowed prefixes: /reports, /extension/reports")) { message }
        assert(message.contains("Fix the pathPattern, update ExtensionManifest.ownership")) { message }
    }

    @Test
    fun `full platform app still rejects root route without custom ownership`() {
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val appLabel = "Reports App"

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.protectedUi(
                        ("/" bindContract GET).to { _ -> Response(Status.OK) },
                        "Reports home",
                        "/",
                    )
                }
            }

        val error =
            assertFailsWith<IllegalArgumentException> {
                ExtensionContribution.from(extension, PlatformMode.FullPlatform, extensionContext())
            }

        val message = error.message.orEmpty()
        assert(message.contains("Route * / (Reports home) is outside extension 'reports' ownership")) { message }
        assert(message.contains("Allowed prefixes: /reports, /extension/reports")) { message }
        assert(message.contains("In ExtensionHost mode, UI routes also get '/' ownership automatically")) { message }
    }

    @Test
    fun `extension manifest can declare custom ownership prefixes`() {
        val customRoute = ("/reports" bindContract GET).to { _ -> Response(Status.OK) }
        val extension =
            object : PlatformExtension {
                override val id = "reports"
                override val manifest =
                    ExtensionManifest(
                        id = id,
                        appLabel = "Reports App",
                        ownership =
                            ExtensionOwnership(
                                uiPrefixes = listOf("/reports"),
                                apiPrefixes = listOf("/api/reports"),
                                adminPrefixes = listOf("/admin/reports"),
                                assetPrefixes = listOf("/assets/reports"),
                            ),
                    )

                override fun contribute(context: ExtensionContributionContext) {
                    context.routes.protectedUi(customRoute, "Reports", "/reports")
                    context.assets.stylesheet("/assets/reports/reports.css")
                }
            }

        val contribution = ExtensionContribution.from(extension, PlatformMode.FullPlatform, extensionContext())

        assertEquals("Reports App", contribution.appLabel)
        assertEquals(extension.manifest, contribution.manifest)
        assertEquals("/reports", contribution.routeRegistrations.single().pathPattern)
        assertEquals(listOf("/assets/reports/reports.css"), contribution.options.assets.stylesheets)
    }

    private fun extensionContext(): ExtensionHostContext {
        val context =
            ExtensionHostContext.forTesting(
                rendering = mockk<HostRendering>(relaxed = true),
                users = mockk<HostUsers>(relaxed = true),
                security =
                    HostSecurity(apiKeys = mockk<HostApiKeys>(relaxed = true), oauth = mockk<HostOAuth>(relaxed = true)),
            )
        assertNotNull(context)
        return context
    }
}
