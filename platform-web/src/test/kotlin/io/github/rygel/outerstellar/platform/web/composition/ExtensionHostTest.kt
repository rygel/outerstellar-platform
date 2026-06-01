package io.github.rygel.outerstellar.platform.web.composition

import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RegisteredRoute
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.composition.RouteOwner
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExtensionHostTest {

    private fun route(
        method: String = "GET",
        path: String = "/",
        owner: RouteOwner = RouteOwner.PlatformKernel,
        group: RouteGroup = RouteGroup.PublicUi,
        description: String = "",
    ) = RegisteredRoute(null, owner, group, path, method, description)

    private fun registerKernelRoutes(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/static",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Static,
                description = "Static assets",
            )
        )
        registry.register(
            route(
                path = "/health",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Health,
                description = "Health check",
            )
        )
        registry.register(
            route(
                path = "/metrics",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Health,
                description = "Metrics",
            )
        )
        registry.register(
            route(
                path = "/robots.txt",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Static,
                description = "Robots.txt",
            )
        )
        registry.register(
            route(
                path = "/sitemap.xml",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Static,
                description = "Sitemap",
            )
        )
    }

    private fun registerAuthRoutes(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/auth",
                method = "*",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "Auth",
            )
        )
        registry.register(
            route(
                path = "/auth/reset",
                method = "GET",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "Password reset",
            )
        )
        registry.register(
            route(
                path = "/auth/oauth",
                method = "*",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "OAuth",
            )
        )
        registry.register(
            route(
                path = "/errors",
                method = "GET",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "Error pages",
            )
        )
    }

    private fun registerApiRoutes(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/api/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Api,
                description = "API routes",
            )
        )
        registry.register(
            route(
                path = "/api/v1/sync/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Api,
                description = "Sync API",
            )
        )
        registry.register(
            route(
                path = "/api/v1/admin/api-openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Api,
                description = "Admin API",
            )
        )
    }

    private fun registerFullPlatformUiRoutes(registry: RouteRegistry) {
        registry.register(
            route(path = "/", owner = RouteOwner.PlatformUi, group = RouteGroup.PublicUi, description = "Home (public)")
        )
        registry.register(
            route(
                path = "/",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Home (protected)",
            )
        )
        registry.register(
            route(path = "/search", owner = RouteOwner.PlatformUi, group = RouteGroup.PublicUi, description = "Search")
        )
        registry.register(
            route(
                path = "/contacts",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Contacts",
            )
        )
        registry.register(
            route(
                path = "/settings",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Settings",
            )
        )
        registry.register(
            route(
                path = "/profile",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Profile",
            )
        )
        registry.register(
            route(
                path = "/settings/api-keys",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "API keys",
            )
        )
        registry.register(
            route(
                path = "/notifications",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Notifications",
            )
        )
        registry.register(
            route(
                path = "/components/notification-bell",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.PublicUi,
                description = "Notification bell",
            )
        )
    }

    private fun registerPageSet(registry: RouteRegistry, pageSet: PlatformPageSets) {
        when (pageSet) {
            PlatformPageSets.HOME -> registerTestHomePages(registry)
            PlatformPageSets.CONTACTS -> registerTestContactsPages(registry)
            PlatformPageSets.SETTINGS -> registerTestSettingsPages(registry)
            PlatformPageSets.SEARCH -> registerTestSearchPages(registry)
            PlatformPageSets.NOTIFICATIONS -> registerTestNotificationPages(registry)
            PlatformPageSets.PROFILE -> registerTestProfilePages(registry)
            PlatformPageSets.ADMIN -> {}
            PlatformPageSets.DEV_DASHBOARD -> {}
        }
    }

    private fun registerTestHomePages(registry: RouteRegistry) {
        registry.register(
            route(path = "/", owner = RouteOwner.PlatformUi, group = RouteGroup.PublicUi, description = "Home (public)")
        )
        registry.register(
            route(
                path = "/",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Home (protected)",
            )
        )
    }

    private fun registerTestContactsPages(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/contacts",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Contacts",
            )
        )
    }

    private fun registerTestSettingsPages(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/settings",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Settings",
            )
        )
    }

    private fun registerTestSearchPages(registry: RouteRegistry) {
        registry.register(
            route(path = "/search", owner = RouteOwner.PlatformUi, group = RouteGroup.PublicUi, description = "Search")
        )
    }

    private fun registerTestNotificationPages(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/notifications",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Notifications",
            )
        )
        registry.register(
            route(
                path = "/components/notification-bell",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.PublicUi,
                description = "Notification bell",
            )
        )
    }

    private fun registerTestProfilePages(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/profile",
                method = "*",
                owner = RouteOwner.PlatformUi,
                group = RouteGroup.ProtectedUi,
                description = "Profile",
            )
        )
    }

    private fun registerSharedKernelUiRoutes(registry: RouteRegistry) {
        registry.register(
            route(
                path = "/logout",
                method = "POST",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.ProtectedUi,
                description = "Logout",
            )
        )
        registry.register(
            route(
                path = "/totp",
                method = "*",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.ProtectedUi,
                description = "TOTP UI",
            )
        )
        registry.register(
            route(
                path = "/api/totp",
                method = "*",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Api,
                description = "TOTP API",
            )
        )
        registry.register(
            route(
                path = "/admin",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.Admin,
                description = "Admin dashboard",
            )
        )
        registry.register(
            route(
                path = "/components/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "Public components",
            )
        )
        registry.register(
            route(
                path = "/components-protected/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.ProtectedUi,
                description = "Protected components",
            )
        )
        registry.register(
            route(
                path = "/ui/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.PublicUi,
                description = "Public UI",
            )
        )
        registry.register(
            route(
                path = "/ui-protected/openapi.json",
                owner = RouteOwner.PlatformKernel,
                group = RouteGroup.ProtectedUi,
                description = "Protected UI",
            )
        )
    }

    private fun simulateMode(mode: PlatformMode, includedPages: Set<PlatformPageSets> = emptySet()): RouteRegistry {
        val registry = RouteRegistry()
        registerKernelRoutes(registry)
        registerAuthRoutes(registry)
        registerApiRoutes(registry)
        registerSharedKernelUiRoutes(registry)

        when (mode) {
            PlatformMode.FullPlatform -> registerFullPlatformUiRoutes(registry)
            PlatformMode.ExtensionHost -> {
                for (pageSet in includedPages) {
                    registerPageSet(registry, pageSet)
                }
                PlatformPageSets.entries
                    .filter { it !in includedPages }
                    .forEach { registry.registerExcludedPageSet(it.pageSet.id) }
            }
            PlatformMode.Headless ->
                PlatformPageSets.entries.forEach { registry.registerExcludedPageSet(it.pageSet.id) }
        }
        return registry
    }

    @Nested
    inner class KernelRoutes {
        @Test
        fun `kernel routes present in FullPlatform`() {
            val registry = simulateMode(PlatformMode.FullPlatform)
            val staticRoutes = registry.byGroup(RouteGroup.Static)
            val healthRoutes = registry.byGroup(RouteGroup.Health)
            assertTrue(staticRoutes.isNotEmpty()) { "Expected static routes" }
            assertTrue(healthRoutes.isNotEmpty()) { "Expected health routes" }
            assertTrue(staticRoutes.all { it.owner == RouteOwner.PlatformKernel })
            assertTrue(healthRoutes.all { it.owner == RouteOwner.PlatformKernel })
        }

        @Test
        fun `kernel routes present in ExtensionHost`() {
            val registry = simulateMode(PlatformMode.ExtensionHost)
            assertTrue(registry.byGroup(RouteGroup.Static).isNotEmpty())
            assertTrue(registry.byGroup(RouteGroup.Health).isNotEmpty())
        }

        @Test
        fun `kernel routes present in Headless`() {
            val registry = simulateMode(PlatformMode.Headless)
            assertTrue(registry.byGroup(RouteGroup.Static).isNotEmpty())
            assertTrue(registry.byGroup(RouteGroup.Health).isNotEmpty())
        }

        @Test
        fun `auth routes are always kernel-owned`() {
            val modes = PlatformMode.entries
            for (mode in modes) {
                val registry = simulateMode(mode)
                val authRoutes = registry.all().filter { it.pathPattern.startsWith("/auth") }
                assertTrue(authRoutes.isNotEmpty()) { "No auth routes in $mode" }
                assertTrue(authRoutes.all { it.owner == RouteOwner.PlatformKernel }) {
                    "Auth routes should be PlatformKernel in $mode"
                }
            }
        }
    }

    @Nested
    inner class FullPlatform {
        @Test
        fun `has PlatformUi routes`() {
            val registry = simulateMode(PlatformMode.FullPlatform)
            val uiRoutes = registry.byOwner(RouteOwner.PlatformUi)
            assertTrue(uiRoutes.isNotEmpty()) { "FullPlatform should have PlatformUi routes" }
        }

        @Test
        fun `includes all core page routes`() {
            val registry = simulateMode(PlatformMode.FullPlatform)
            val uiPaths = registry.byOwner(RouteOwner.PlatformUi).map { it.pathPattern }.toSet()
            assertTrue(uiPaths.contains("/")) { "Missing home route" }
            assertTrue(uiPaths.contains("/search")) { "Missing search route" }
            assertTrue(uiPaths.contains("/contacts")) { "Missing contacts route" }
            assertTrue(uiPaths.contains("/settings")) { "Missing settings route" }
            assertTrue(uiPaths.contains("/profile")) { "Missing profile route" }
            assertTrue(uiPaths.contains("/notifications")) { "Missing notifications route" }
        }

        @Test
        fun `no conflicts`() {
            val registry = simulateMode(PlatformMode.FullPlatform)
            registry.requireNoConflicts()
        }
    }

    @Nested
    inner class ExtensionHostNoPages {
        @Test
        fun `has no PlatformUi routes when no pages included`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, emptySet())
            val uiRoutes = registry.byOwner(RouteOwner.PlatformUi)
            assertTrue(uiRoutes.isEmpty()) { "ExtensionHost with no included pages should have no PlatformUi routes" }
        }

        @Test
        fun `still has kernel routes`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, emptySet())
            val kernelRoutes = registry.byOwner(RouteOwner.PlatformKernel)
            assertTrue(kernelRoutes.isNotEmpty()) { "Should have kernel routes" }
            val paths = kernelRoutes.map { it.pathPattern }.toSet()
            assertTrue(paths.contains("/health")) { "Missing health check" }
            assertTrue(paths.contains("/static")) { "Missing static assets" }
            assertTrue(paths.contains("/auth")) { "Missing auth" }
        }

        @Test
        fun `still has API routes`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, emptySet())
            val apiRoutes = registry.byGroup(RouteGroup.Api)
            assertTrue(apiRoutes.isNotEmpty()) { "Should have API routes" }
        }

        @Test
        fun `no conflicts`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, emptySet())
            registry.requireNoConflicts()
        }
    }

    @Nested
    inner class ExtensionHostWithIncludedPages {
        @Test
        fun `settings page set adds PlatformUi settings route`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.SETTINGS))
            val settingsRoutes = registry.byOwner(RouteOwner.PlatformUi).filter { it.pathPattern == "/settings" }
            assertEquals(1, settingsRoutes.size) { "Expected 1 settings route" }
            assertEquals(RouteGroup.ProtectedUi, settingsRoutes[0].group)
        }

        @Test
        fun `home page set adds PlatformUi home routes`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.HOME))
            val homeRoutes = registry.byOwner(RouteOwner.PlatformUi).filter { it.pathPattern == "/" }
            assertEquals(2, homeRoutes.size) { "Expected public + protected home routes" }
            val groups = homeRoutes.map { it.group }.toSet()
            assertTrue(groups.contains(RouteGroup.PublicUi)) { "Missing public home route" }
            assertTrue(groups.contains(RouteGroup.ProtectedUi)) { "Missing protected home route" }
        }

        @Test
        fun `contacts page set adds contacts route`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.CONTACTS))
            val contacts = registry.byOwner(RouteOwner.PlatformUi).filter { it.pathPattern == "/contacts" }
            assertEquals(1, contacts.size)
        }

        @Test
        fun `search page set adds search route`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.SEARCH))
            val search = registry.byOwner(RouteOwner.PlatformUi).filter { it.pathPattern == "/search" }
            assertEquals(1, search.size)
            assertEquals(RouteGroup.PublicUi, search[0].group)
        }

        @Test
        fun `notifications page set adds both notification routes`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.NOTIFICATIONS))
            val notifRoutes = registry.byOwner(RouteOwner.PlatformUi)
            assertEquals(2, notifRoutes.size) { "Expected notification list + bell routes" }
            val paths = notifRoutes.map { it.pathPattern }.toSet()
            assertTrue(paths.contains("/notifications")) { "Missing notifications route" }
            assertTrue(paths.contains("/components/notification-bell")) { "Missing bell route" }
        }

        @Test
        fun `profile page set adds profile route`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.PROFILE))
            val profile = registry.byOwner(RouteOwner.PlatformUi).filter { it.pathPattern == "/profile" }
            assertEquals(1, profile.size)
        }

        @Test
        fun `multiple page sets combine routes`() {
            val registry =
                simulateMode(
                    PlatformMode.ExtensionHost,
                    setOf(PlatformPageSets.SETTINGS, PlatformPageSets.CONTACTS, PlatformPageSets.SEARCH),
                )
            val uiRoutes = registry.byOwner(RouteOwner.PlatformUi)
            val paths = uiRoutes.map { it.pathPattern }.toSet()
            assertTrue(paths.contains("/settings")) { "Missing settings" }
            assertTrue(paths.contains("/contacts")) { "Missing contacts" }
            assertTrue(paths.contains("/search")) { "Missing search" }
            assertEquals(3, paths.size)
        }

        @Test
        fun `admin and dev-dashboard page sets register no PlatformUi routes`() {
            val registry =
                simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.ADMIN, PlatformPageSets.DEV_DASHBOARD))
            val uiRoutes = registry.byOwner(RouteOwner.PlatformUi)
            assertTrue(uiRoutes.isEmpty()) { "ADMIN and DEV_DASHBOARD should not register PlatformUi routes directly" }
        }

        @Test
        fun `included pages do not conflict with kernel routes`() {
            val registry =
                simulateMode(
                    PlatformMode.ExtensionHost,
                    setOf(PlatformPageSets.HOME, PlatformPageSets.SETTINGS, PlatformPageSets.NOTIFICATIONS),
                )
            registry.requireNoConflicts()
        }
    }

    @Nested
    inner class ConflictDetection {
        @Test
        fun `extension route conflicting with platform route is detected`() {
            val registry = RouteRegistry()
            registry.register(route(path = "/settings", owner = RouteOwner.PlatformUi, group = RouteGroup.ProtectedUi))
            registry.register(route(path = "/settings", owner = RouteOwner.Extension, group = RouteGroup.ProtectedUi))
            val conflicts = registry.conflicts()
            assertEquals(1, conflicts.size)
            assertEquals(RouteOwner.PlatformUi, conflicts[0].existing)
            assertEquals(RouteOwner.Extension, conflicts[0].challenger)
        }

        @Test
        fun `same owner same path is not a conflict`() {
            val registry = RouteRegistry()
            registry.register(route(path = "/api/data", owner = RouteOwner.PlatformKernel, group = RouteGroup.Api))
            registry.register(route(path = "/api/data", owner = RouteOwner.PlatformKernel, group = RouteGroup.Api))
            assertTrue(registry.conflicts().isEmpty())
        }

        @Test
        fun `different methods same path no conflict`() {
            val registry = RouteRegistry()
            registry.register(
                route(method = "GET", path = "/api/items", owner = RouteOwner.PlatformKernel, group = RouteGroup.Api)
            )
            registry.register(
                route(method = "POST", path = "/api/items", owner = RouteOwner.Extension, group = RouteGroup.Api)
            )
            assertTrue(registry.conflicts().isEmpty())
        }

        @Test
        fun `requireNoConflicts throws with descriptive message`() {
            val registry = RouteRegistry()
            registry.register(
                route(
                    path = "/dashboard",
                    owner = RouteOwner.PlatformUi,
                    group = RouteGroup.ProtectedUi,
                    description = "Platform dashboard",
                )
            )
            registry.register(
                route(
                    path = "/dashboard",
                    owner = RouteOwner.Extension,
                    group = RouteGroup.ProtectedUi,
                    description = "Extension dashboard",
                )
            )
            val ex = assertThrows<IllegalArgumentException> { registry.requireNoConflicts() }
            assertTrue(ex.message!!.contains("Route conflicts detected"))
            assertTrue(ex.message!!.contains("/dashboard"))
            assertTrue(ex.message!!.contains("existing: PlatformUi [ProtectedUi] Platform dashboard"))
            assertTrue(ex.message!!.contains("challenger: Extension [ProtectedUi] Extension dashboard"))
            assertTrue(ex.message!!.contains("manifest-owned prefix"))
        }

        @Test
        fun `multiple conflicts all reported`() {
            val registry = RouteRegistry()
            registry.register(route(path = "/a", owner = RouteOwner.PlatformKernel, group = RouteGroup.Api))
            registry.register(route(path = "/a", owner = RouteOwner.Extension, group = RouteGroup.Api))
            registry.register(route(path = "/b", owner = RouteOwner.PlatformUi, group = RouteGroup.ProtectedUi))
            registry.register(route(path = "/b", owner = RouteOwner.Extension, group = RouteGroup.ProtectedUi))
            assertEquals(2, registry.conflicts().size)
        }
    }

    @Nested
    inner class RouteTableFormatting {
        @Test
        fun `formatTable includes route count`() {
            val registry = simulateMode(PlatformMode.FullPlatform)
            val table = registry.formatTable()
            assertTrue(table.startsWith("Platform Route Table ("))
            assertTrue(table.contains("routes):"))
        }

        @Test
        fun `formatTable shows no conflicts for valid registry`() {
            val registry = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.SETTINGS))
            val table = registry.formatTable()
            assertTrue(table.contains("No conflicts detected."))
        }

        @Test
        fun `formatTable shows conflict count when conflicts exist`() {
            val registry = RouteRegistry()
            registry.register(route(path = "/x", owner = RouteOwner.PlatformKernel))
            registry.register(route(path = "/x", owner = RouteOwner.Extension))
            val table = registry.formatTable()
            assertTrue(table.contains("conflict(s) detected!"))
            assertFalse(table.contains("No conflicts detected."))
        }

        @Test
        fun `formatTable lists owner names`() {
            val registry = RouteRegistry()
            registry.register(
                route(
                    path = "/a",
                    owner = RouteOwner.PlatformKernel,
                    group = RouteGroup.Api,
                    description = "Kernel route",
                )
            )
            registry.register(
                route(
                    path = "/b",
                    owner = RouteOwner.PlatformUi,
                    group = RouteGroup.ProtectedUi,
                    description = "UI route",
                )
            )
            registry.register(
                route(
                    path = "/c",
                    owner = RouteOwner.Extension,
                    group = RouteGroup.PublicUi,
                    description = "Extension route",
                )
            )
            val table = registry.formatTable()
            assertTrue(table.contains("PlatformKernel")) { "Missing PlatformKernel in table" }
            assertTrue(table.contains("PlatformUi")) { "Missing PlatformUi in table" }
            assertTrue(table.contains("Extension")) { "Missing Extension in table" }
        }

        @Test
        fun `formatTable lists group names`() {
            val registry = RouteRegistry()
            registry.register(route(path = "/a", group = RouteGroup.Api))
            registry.register(route(path = "/b", group = RouteGroup.ProtectedUi))
            registry.register(route(path = "/c", group = RouteGroup.Static))
            val table = registry.formatTable()
            assertTrue(table.contains("[Api]")) { "Missing [Api] in table" }
            assertTrue(table.contains("[ProtectedUi]")) { "Missing [ProtectedUi] in table" }
            assertTrue(table.contains("[Static]")) { "Missing [Static] in table" }
        }
    }

    @Nested
    inner class Headless {
        @Test
        fun `has no UI routes`() {
            val registry = simulateMode(PlatformMode.Headless)
            val publicUi = registry.byGroup(RouteGroup.PublicUi).filter { it.owner == RouteOwner.PlatformUi }
            val protectedUi = registry.byGroup(RouteGroup.ProtectedUi).filter { it.owner == RouteOwner.PlatformUi }
            assertTrue(publicUi.isEmpty()) { "Headless should have no PlatformUi public routes" }
            assertTrue(protectedUi.isEmpty()) { "Headless should have no PlatformUi protected routes" }
        }

        @Test
        fun `still has API and health routes`() {
            val registry = simulateMode(PlatformMode.Headless)
            assertTrue(registry.byGroup(RouteGroup.Api).isNotEmpty()) { "Should have API routes" }
            assertTrue(registry.byGroup(RouteGroup.Health).isNotEmpty()) { "Should have health routes" }
        }

        @Test
        fun `no conflicts`() {
            val registry = simulateMode(PlatformMode.Headless)
            registry.requireNoConflicts()
        }
    }

    @Nested
    inner class ModeComparison {
        @Test
        fun `FullPlatform has more routes than ExtensionHost with no pages`() {
            val full = simulateMode(PlatformMode.FullPlatform)
            val extension = simulateMode(PlatformMode.ExtensionHost, emptySet())
            assertTrue(full.all().size > extension.all().size) {
                "FullPlatform (${full.all().size}) should have more routes than empty ExtensionHost (${extension.all().size})"
            }
        }

        @Test
        fun `ExtensionHost route count grows with included pages`() {
            val empty = simulateMode(PlatformMode.ExtensionHost, emptySet())
            val withOne = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.SETTINGS))
            val withMany =
                simulateMode(
                    PlatformMode.ExtensionHost,
                    setOf(PlatformPageSets.SETTINGS, PlatformPageSets.HOME, PlatformPageSets.CONTACTS),
                )
            assertTrue(withOne.all().size > empty.all().size) { "Adding a page set should increase route count" }
            assertTrue(withMany.all().size > withOne.all().size) { "Adding more page sets should increase route count" }
        }

        @Test
        fun `PlatformUi routes only present in FullPlatform or included pages`() {
            val full = simulateMode(PlatformMode.FullPlatform)
            val extensionEmpty = simulateMode(PlatformMode.ExtensionHost, emptySet())
            val extensionIncluded = simulateMode(PlatformMode.ExtensionHost, setOf(PlatformPageSets.SETTINGS))
            assertTrue(full.byOwner(RouteOwner.PlatformUi).isNotEmpty())
            assertTrue(extensionEmpty.byOwner(RouteOwner.PlatformUi).isEmpty())
            assertTrue(extensionIncluded.byOwner(RouteOwner.PlatformUi).isNotEmpty())
        }
    }
}
