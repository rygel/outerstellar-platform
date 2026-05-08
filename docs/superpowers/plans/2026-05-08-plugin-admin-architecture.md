# Plugin Admin Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `PlatformPlugin` with an `adminSections()` method so plugins can contribute dashboard summary cards and full detail pages to the admin section.

**Architecture:** Three new data types (`AdminMetric`, `AdminSummaryCard`, `AdminSection`) define what a plugin contributes. A new `adminSections()` method on `PlatformPlugin` returns these sections. App.kt wires them into the existing admin contract (which already has ADMIN-role security). A new JTE template renders a dashboard grid of summary cards. Admin nav items are derived from plugin sections and injected via `PluginOptions`.

**Tech Stack:** Kotlin, http4k contract routing, JTE templates, DaisyUI/Tailwind CSS

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-web/src/main/kotlin/.../web/AdminMetric.kt` | Single metric data class |
| Create | `platform-web/src/main/kotlin/.../web/AdminSummaryCard.kt` | Card with metrics + link |
| Create | `platform-web/src/main/kotlin/.../web/AdminSection.kt` | Section = nav + card + route |
| Modify | `platform-web/src/main/kotlin/.../web/PlatformPlugin.kt` | Add `adminSections()`, `AdminNavItem` |
| Modify | `platform-web/src/main/kotlin/.../web/ViewModels.kt` | Add `PluginAdminDashboardPage` |
| Create | `platform-web/src/main/jte/.../web/PluginAdminDashboard.kte` | Dashboard grid template |
| Modify | `platform-web/src/main/kotlin/.../web/WebContext.kt` | Append plugin admin nav items |
| Modify | `platform-web/src/main/kotlin/.../App.kt` | Wire plugin admin routes + nav |
| Create | `platform-web/src/test/kotlin/.../web/AdminSectionTest.kt` | All tests |

---

### Task 1: Add AdminMetric data class

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminMetric.kt`

- [ ] **Step 1: Create AdminMetric.kt**

```kotlin
package io.github.rygel.outerstellar.platform.web

data class AdminMetric(
    val label: String,
    val value: String,
    val trend: String? = null,
)
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminMetric.kt
git commit -m "feat: add AdminMetric data class for plugin admin infrastructure"
```

---

### Task 2: Add AdminSummaryCard data class

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminSummaryCard.kt`

- [ ] **Step 1: Create AdminSummaryCard.kt**

```kotlin
package io.github.rygel.outerstellar.platform.web

data class AdminSummaryCard(
    val title: String,
    val metrics: List<AdminMetric>,
    val linkUrl: String,
    val linkLabel: String = "View details",
)
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminSummaryCard.kt
git commit -m "feat: add AdminSummaryCard data class for plugin admin infrastructure"
```

---

### Task 3: Add AdminSection data class

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminSection.kt`

- [ ] **Step 1: Create AdminSection.kt**

```kotlin
package io.github.rygel.outerstellar.platform.web

import org.http4k.contract.ContractRoute

data class AdminSection(
    val id: String,
    val navLabel: String,
    val navIcon: String,
    val summaryCard: AdminSummaryCard,
    val route: ContractRoute,
)
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AdminSection.kt
git commit -m "feat: add AdminSection data class for plugin admin infrastructure"
```

---

### Task 4: Extend PlatformPlugin with adminSections() and AdminNavItem

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt`

The existing `PlatformPlugin.kt` has:
- `PluginNavItem(label, url, icon, activeSection)` at line ~16
- `PluginOptions(navItems, textResolver)` at line ~24
- `PlatformPlugin` interface starting ~line 55

- [ ] **Step 1: Add AdminNavItem data class after PluginNavItem**

After the existing `PluginNavItem` data class, add:

```kotlin
data class AdminNavItem(
    val label: String,
    val url: String,
    val icon: String,
)
```

- [ ] **Step 2: Add adminNavItems to PluginOptions**

In `PluginOptions`, add a new field after `textResolver`:

```kotlin
data class PluginOptions(
    val navItems: List<PluginNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
)
```

- [ ] **Step 3: Add adminSections() to PlatformPlugin interface**

In the `PlatformPlugin` interface, after the existing `filters(context)` method, add:

```kotlin
fun adminSections(context: PluginContext): List<AdminSection> = emptyList()
```

- [ ] **Step 4: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt
git commit -m "feat: extend PlatformPlugin with adminSections() and AdminNavItem"
```

---

### Task 5: Add PluginAdminDashboardPage view model

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt`

The existing file has `DevDashboardPage` (line ~145) as a model for admin dashboard pages.

- [ ] **Step 1: Add PluginAdminDashboardPage after DevDashboardPage**

After the `DevDashboardPage` class definition, add:

```kotlin
class PluginAdminDashboardPage(
    val cards: List<AdminSummaryCard>,
    override val shell: ShellViewModel,
) : PageViewModel
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt
git commit -m "feat: add PluginAdminDashboardPage view model"
```

---

### Task 6: Create PluginAdminDashboard JTE template

**Files:**
- Create: `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/PluginAdminDashboard.kte`

Follow the pattern from `DevDashboard.kte`: uses `@template.LayoutRouter`, DaisyUI cards, grid layout.

- [ ] **Step 1: Create the template**

```jte
@param model: io.github.rygel.outerstellar.platform.web.Page<io.github.rygel.outerstellar.platform.web.PluginAdminDashboardPage>
@import io.github.rygel.outerstellar.platform.web.AdminMetric
@template.io.github.rygel.outerstellar.platform.web.layouts.LayoutRouter(shell = model.shell, content = @`
<div class="p-6">
    <h2 class="text-2xl font-bold mb-6">Plugin Dashboard</h2>
    <div class="grid grid-cols-[repeat(auto-fit,minmax(280px,1fr))] gap-4">
        @for(model.data.cards.indices)
        <div class="card bg-base-200 border border-base-300">
            <div class="card-body">
                <h3 class="card-title text-lg">${model.data.cards.get(it).title}</h3>
                <div class="space-y-1 mt-2">
                    @for(model.data.cards.get(it).metrics)
                    <div class="flex justify-between text-sm">
                        <span class="opacity-70">${it.label}</span>
                        <span class="font-semibold">${it.value}</span>
                        @if(it.trend != null)
                        <span class="text-xs opacity-60 ml-2">${it.trend}</span>
                        @endif
                    </div>
                    @endfor
                </div>
                <div class="card-actions justify-end mt-4">
                    <a href="${model.data.cards.get(it).linkUrl}" class="btn btn-primary btn-sm">${model.data.cards.get(it).linkLabel}</a>
                </div>
            </div>
        </div>
        @endfor
    </div>
    @if(model.data.cards.isEmpty())
    <div class="text-center opacity-60 mt-12">
        <p>No plugin admin sections registered.</p>
    </div>
    @endif
</div>
`)
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/PluginAdminDashboard.kte
git commit -m "feat: add PluginAdminDashboard JTE template"
```

---

### Task 7: Wire plugin admin nav items in WebContext

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`

The existing `appendAdminLinks()` method (line ~147) adds hardcoded admin nav links. It's called from `buildNavLinks()` which is called from `shell()` which receives `PluginOptions` via `ctx.pluginOptions`.

- [ ] **Step 1: Add plugin admin nav items in appendAdminLinks()**

In `appendAdminLinks()`, after the existing `devDashboardEnabled` block, add:

```kotlin
pluginOptions.adminNavItems.forEach { item ->
    links.add(ShellLink(item.label, url(item.url), item.icon, activeSection == item.url))
}
```

Note: `appendAdminLinks` currently has no access to `pluginOptions`. It's a private method on `WebContext`. The `WebContext` constructor receives `pluginOptions: PluginOptions` and stores it in a field. Add `pluginOptions` as a parameter to `appendAdminLinks`, or use the existing field. Check the constructor — `pluginOptions` is already a constructor parameter stored as a val, so it can be accessed directly.

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git commit -m "feat: append plugin admin nav items in WebContext"
```

---

### Task 8: Wire plugin admin routes and dashboard in App.kt

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`

This is the core wiring task. Changes needed:

1. Pass `plugin` to `buildAdminRoutes()` 
2. Add plugin admin sections' routes to the admin contract
3. Add a plugin admin dashboard route (`/admin/plugins`) if plugin has sections
4. Derive `AdminNavItem` list from `plugin.adminSections()` and pass into `PluginOptions`

- [ ] **Step 1: Add plugin parameter to buildAdminRoutes()**

The current signature is:
```kotlin
private fun buildAdminRoutes(
    appLabel: String,
    outboxRepository: OutboxRepository?,
    cache: MessageCache?,
    pageFactory: WebPageFactory,
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    securityService: SecurityService,
): RoutingHttpHandler
```

Add `plugin: PlatformPlugin` parameter:
```kotlin
private fun buildAdminRoutes(
    appLabel: String,
    outboxRepository: OutboxRepository?,
    cache: MessageCache?,
    pageFactory: WebPageFactory,
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    securityService: SecurityService,
    plugin: PlatformPlugin,
): RoutingHttpHandler
```

- [ ] **Step 2: Add plugin admin section routes inside buildAdminRoutes()**

After the existing `routes += DevDashboardRoutes(...)` and `routes += UserAdminRoutes(...)`, add:

```kotlin
val pluginContext = PluginContext.forTesting(jteRenderer, config, securityService)
val sections = plugin.adminSections(pluginContext)
if (sections.isNotEmpty()) {
    routes += prefix("/admin/plugins") {
        sections.forEach { section ->
            routes += section.route
        }
    }
    routes += "/admin/plugins" bind Method.GET to { req ->
        val ctx = WebContext(req, PluginOptions(adminNavItems = sections.map {
            AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon)
        }))
        val shell = ctx.shell("Plugin Dashboard")
        val page = Page(PluginAdminDashboardPage(sections.map { it.summaryCard }, shell))
        Response(Status.OK).body(jteRenderer.render("PluginAdminDashboard.kte", page))
    }
}
```

Note: The exact `PluginContext` construction and `WebContext` usage may need adjustment based on how the existing code builds these in `buildBaseApp()`. Check how `PluginContext.forTesting()` is used and replicate the pattern from the existing `buildUiRoutes()` method.

- [ ] **Step 3: Pass plugin to buildAdminRoutes() at the call site**

In `buildBaseApp()`, find the call to `buildAdminRoutes(...)` and add the `plugin` parameter.

- [ ] **Step 4: Derive adminNavItems for PluginOptions**

In `buildFilterChain()` where `PluginOptions` is constructed, derive admin nav items from the plugin:

```kotlin
val adminNavItems = plugin.adminSections(PluginContext.forTesting(jteRenderer, config, securityService))
    .map { AdminNavItem(it.navLabel, "/admin/plugins", it.navIcon) }
PluginOptions(
    navItems = plugin.navItems(PluginContext.forTesting(jteRenderer, config, securityService)),
    textResolver = plugin.textResolver,
    adminNavItems = adminNavItems,
)
```

- [ ] **Step 5: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "feat: wire plugin admin routes and dashboard in App.kt"
```

---

### Task 9: Write tests

**Files:**
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminSectionTest.kt`

Follow the test patterns from `AdminHtmlRoutesIntegrationTest.kt` and `PluginTextOverrideTest.kt`. The test base class is `WebTest` which provides an in-memory `HttpHandler` with Testcontainers PostgreSQL.

- [ ] **Step 1: Create test file**

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.web.WebTest
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.contract.ContractRoute
import org.http4k.contract.RouteMetaDsl
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdminSectionTest : WebTest() {

    @Test
    fun `default PlatformPlugin adminSections returns empty list`() {
        val plugin = object : PlatformPlugin {
            override val id = "test-plugin"
        }
        val context = PluginContext.forTesting()
        assertEquals(emptyList<AdminSection>(), plugin.adminSections(context))
    }

    @Test
    fun `AdminMetric holds label value and optional trend`() {
        val metric = AdminMetric("Users", "42", "+5")
        assertEquals("Users", metric.label)
        assertEquals("42", metric.value)
        assertEquals("+5", metric.trend)
        val noTrend = AdminMetric("Active", "10")
        assertNull(noTrend.trend)
    }

    @Test
    fun `AdminSummaryCard holds title metrics and link`() {
        val card = AdminSummaryCard(
            title = "Payments",
            metrics = listOf(AdminMetric("Revenue", "$1,200")),
            linkUrl = "/admin/plugins/payments",
            linkLabel = "Open",
        )
        assertEquals("Payments", card.title)
        assertEquals(1, card.metrics.size)
        assertEquals("/admin/plugins/payments", card.linkUrl)
        assertEquals("Open", card.linkLabel)
    }

    @Test
    fun `AdminSection holds id navLabel navIcon card and route`() {
        val route: ContractRoute = "/admin/plugins/test" meta { summary = "Test section" } bindContract Method.GET to { Response(OK) }
        val section = AdminSection(
            id = "test-section",
            navLabel = "Test",
            navIcon = "ri-test-line",
            summaryCard = AdminSummaryCard("Test", emptyList(), "/admin/plugins/test"),
            route = route,
        )
        assertEquals("test-section", section.id)
        assertEquals("Test", section.navLabel)
    }

    @Test
    fun `AdminNavItem holds label url and icon`() {
        val item = AdminNavItem("Billing", "/admin/plugins/billing", "ri-money-dollar-circle-line")
        assertEquals("Billing", item.label)
        assertEquals("/admin/plugins/billing", item.url)
        assertEquals("ri-money-dollar-circle-line", item.icon)
    }

    @Test
    fun `PluginOptions carries adminNavItems`() {
        val opts = PluginOptions(
            adminNavItems = listOf(AdminNavItem("Test", "/test", "ri-test-line")),
        )
        assertEquals(1, opts.adminNavItems.size)
        val empty = PluginOptions()
        assertTrue(empty.adminNavItems.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn -pl platform-web test -Dtest=AdminSectionTest -q`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminSectionTest.kt
git commit -m "test: add AdminSectionTest for plugin admin infrastructure"
```

---

### Task 10: Run full test suite and verify

- [ ] **Step 1: Run full platform-web tests**

Run: `mvn -pl platform-web test`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run full reactor verify**

Run: `mvn clean verify -Pfast -T 4`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit (if any formatting fixes needed)**

```bash
git add -A
git commit -m "chore: formatting fixes from full test run"
```
