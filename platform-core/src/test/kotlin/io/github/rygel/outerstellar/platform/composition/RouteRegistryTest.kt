package io.github.rygel.outerstellar.platform.composition

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RouteRegistryTest {

    @Test
    fun `empty registry has no routes`() {
        val registry = RouteRegistry()
        assert(registry.all().isEmpty()) { "Expected empty registry" }
    }

    @Test
    fun `register single route`() {
        val registry = RouteRegistry()
        val route = registeredRoute("GET", "/", "Home page")
        registry.register(route)
        val all = registry.all()
        assert(all.size == 1 && all[0] == route) { "Expected exactly one route" }
    }

    @Test
    fun `registerAll adds multiple routes`() {
        val registry = RouteRegistry()
        val routes = listOf(registeredRoute("GET", "/", "Home"), registeredRoute("GET", "/settings", "Settings"))
        registry.registerAll(routes)
        assert(registry.all().size == 2) { "Expected 2 routes but got ${registry.all().size}" }
    }

    @Test
    fun `byGroup filters by group`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", group = RouteGroup.PublicUi))
        registry.register(registeredRoute("GET", "/settings", group = RouteGroup.ProtectedUi))
        registry.register(registeredRoute("POST", "/api/v1/messages", group = RouteGroup.Api))
        assert(registry.byGroup(RouteGroup.Api).size == 1) { "Expected 1 Api route" }
        assert(registry.byGroup(RouteGroup.PublicUi).size == 1) { "Expected 1 PublicUi route" }
    }

    @Test
    fun `byOwner filters by owner`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/custom", owner = RouteOwner.Extension))
        assert(registry.byOwner(RouteOwner.Extension).size == 1) { "Expected 1 Extension route" }
        assert(registry.byOwner(RouteOwner.PlatformUi).size == 1) { "Expected 1 PlatformUi route" }
    }

    @Test
    fun `no conflicts when different paths`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/settings", owner = RouteOwner.PlatformUi))
        assert(registry.conflicts().isEmpty()) { "Expected no conflicts" }
    }

    @Test
    fun `no conflicts when same path but different methods`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/api/data", owner = RouteOwner.PlatformKernel))
        registry.register(registeredRoute("POST", "/api/data", owner = RouteOwner.Extension))
        assert(registry.conflicts().isEmpty()) { "Expected no conflicts" }
    }

    @Test
    fun `detects conflict when same path and method`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/", owner = RouteOwner.Extension))
        val conflicts = registry.conflicts()
        assert(conflicts.size == 1) { "Expected 1 conflict" }
        assert(conflicts[0].pathPattern == "/") { "Expected path /" }
        assert(conflicts[0].method == "GET") { "Expected method GET" }
    }

    @Test
    fun `conflict message identifies both owners`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/settings", "Settings page", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/settings", "Hosted settings", owner = RouteOwner.Extension))
        val conflict = registry.conflicts().first()
        assert(conflict.existing == RouteOwner.PlatformUi) { "Expected PlatformUi as existing" }
        assert(conflict.challenger == RouteOwner.Extension) { "Expected Extension as challenger" }
        assert(conflict.existingRoute?.description == "Settings page") { "Expected existing route details" }
        assert(conflict.challengerRoute?.description == "Hosted settings") { "Expected challenger route details" }
    }

    @Test
    fun `requireNoConflicts throws on conflicts`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", "Platform home", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/", "Extension home", owner = RouteOwner.Extension))
        val error = assertThrows<IllegalArgumentException> { registry.requireNoConflicts() }
        val message = error.message.orEmpty()
        assert(message.contains("existing: PlatformUi [ProtectedUi] Platform home")) { message }
        assert(message.contains("challenger: Extension [ProtectedUi] Extension home")) { message }
        assert(message.contains("Remediation: move the extension route")) { message }
    }

    @Test
    fun `requireNoConflicts passes when no conflicts`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", owner = RouteOwner.PlatformUi))
        registry.register(registeredRoute("GET", "/settings", owner = RouteOwner.PlatformUi))
        registry.requireNoConflicts()
    }

    @Test
    fun `formatTable produces readable output`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", "Home page", RouteOwner.PlatformUi, RouteGroup.ProtectedUi))
        registry.register(
            registeredRoute("POST", "/auth/login", "Login", RouteOwner.PlatformKernel, RouteGroup.PublicUi)
        )
        val table = registry.formatTable()
        assert(table.contains("Platform Route Table (2 routes)")) { "Expected header" }
        assert(table.contains("GET")) { "Expected GET" }
        assert(table.contains("POST")) { "Expected POST" }
        assert(table.contains("/")) { "Expected /" }
        assert(table.contains("/auth/login")) { "Expected /auth/login" }
        assert(table.contains("PlatformUi")) { "Expected PlatformUi" }
        assert(table.contains("PlatformKernel")) { "Expected PlatformKernel" }
    }

    @Test
    fun `excluded page sets are listed in formatTable`() {
        val registry = RouteRegistry()
        registry.register(registeredRoute("GET", "/", "Home page", RouteOwner.PlatformUi, RouteGroup.PublicUi))
        registry.registerExcludedPageSet("settings")
        registry.registerExcludedPageSet("contacts")

        val table = registry.formatTable()

        assert(table.contains("Excluded page sets: contacts, settings")) { "Expected excluded page sets" }
        assert(registry.excludedPageSets() == listOf("settings", "contacts")) { "Expected insertion order" }
    }

    private fun registeredRoute(
        method: String,
        path: String,
        description: String = "",
        owner: RouteOwner = RouteOwner.PlatformUi,
        group: RouteGroup = RouteGroup.ProtectedUi,
    ) =
        RegisteredRoute(
            httpRoute = null,
            owner = owner,
            group = group,
            pathPattern = path,
            method = method,
            description = description,
        )
}
