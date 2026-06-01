# Extension Admin Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `PlatformExtension` with an `adminSections()` method so extensions can contribute dashboard summary cards and full detail pages to the admin section.

**Architecture:** Three new data types (`AdminMetric`, `AdminSummaryCard`, `AdminSection`) define what an extension contributes. A new `adminSections()` method on `PlatformExtension` returns these sections. App.kt wires them into the existing admin contract (which already has ADMIN-role security). A new JTE template renders a dashboard grid of summary cards. Admin nav items are derived from extension sections and injected via `ExtensionOptions`.

**Tech Stack:** Kotlin, http4k contract routing, JTE templates, DaisyUI/Tailwind CSS

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-web/src/main/kotlin/.../web/AdminMetric.kt` | Single metric data class |
| Create | `platform-web/src/main/kotlin/.../web/AdminSummaryCard.kt` | Card with metrics + link |
| Create | `platform-web/src/main/kotlin/.../web/AdminSection.kt` | Section = nav + card + route |
| Modify | `platform-web/src/main/kotlin/.../web/PlatformExtension.kt` | Add `adminSections()`, `AdminNavItem` |
| Modify | `platform-web/src/main/kotlin/.../web/ViewModels.kt` | Add `ExtensionAdminDashboardPage` |
| Create | `platform-web/src/main/jte/.../web/ExtensionAdminDashboard.kte` | Dashboard grid template |
| Modify | `platform-web/src/main/kotlin/.../web/WebContext.kt` | Append extension admin nav items |
| Modify | `platform-web/src/main/kotlin/.../App.kt` | Wire extension admin routes + nav |
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
git commit -m "feat: add AdminMetric data class for extension admin infrastructure"
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
git commit -m "feat: add AdminSummaryCard data class for extension admin infrastructure"
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
git commit -m "feat: add AdminSection data class for extension admin infrastructure"
```

---

### Task 4: Extend PlatformExtension with adminSections() and AdminNavItem

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformExtension.kt`

The existing `PlatformExtension.kt` has:
- `ExtensionNavItem(label, url, icon, activeSection)` at line ~16
- `ExtensionOptions(navItems, textResolver)` at line ~24
- `PlatformExtension` interface starting ~line 55

- [ ] **Step 1: Add AdminNavItem data class after ExtensionNavItem**

After the existing `ExtensionNavItem` data class, add:

```kotlin
data class AdminNavItem(
    val label: String,
    val url: String,
    val icon: String,
)
```

- [ ] **Step 2: Add adminNavItems to ExtensionOptions**

In `ExtensionOptions`, add a new field after `textResolver`:

```kotlin
data class ExtensionOptions(
    val navItems: List<ExtensionNavItem> = emptyList(),
    val textResolver: TextResolver? = null,
    val adminNavItems: List<AdminNavItem> = emptyList(),
)
```

- [ ] **Step 3: Add adminSections() to PlatformExtension interface**

In the `PlatformExtension` interface, after the existing `filters(context)` method, add:

```kotlin
fun adminSections(context: ExtensionContext): List<AdminSection> = emptyList()
```

- [ ] **Step 4: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformExtension.kt
git commit -m "feat: extend PlatformExtension with adminSections() and AdminNavItem"
```

---

### Task 5: Add ExtensionAdminDashboardPage view model

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt`

The existing file has `DevDashboardPage` (line ~145) as a model for admin dashboard pages.

- [ ] **Step 1: Add ExtensionAdminDashboardPage after DevDashboardPage**

After the `DevDashboardPage` class definition, add:

```kotlin
class ExtensionAdminDashboardPage(
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
git commit -m "feat: add ExtensionAdminDashboardPage view model"
```

---

### Task 6: Create ExtensionAdminDashboard JTE template

**Files:**
- Create: `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/ExtensionAdminDashboard.kte`

Follow the pattern from `DevDashboard.kte`: uses `@template.LayoutRouter`, DaisyUI cards, grid layout.

- [ ] **Step 1: Create the template**

```jte
@param model: io.github.rygel.outerstellar.platform.web.Page<io.github.rygel.outerstellar.platform.web.ExtensionAdminDashboardPage>
@import io.github.rygel.outerstellar.platform.web.AdminMetric
@template.io.github.rygel.outerstellar.platform.web.layouts.LayoutRouter(shell = model.shell, content = @`
<div class="p-6">
    <h2 class="text-2xl font-bold mb-6">Extension Dashboard</h2>
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
        <p>No extension admin sections registered.</p>
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
git add platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/ExtensionAdminDashboard.kte
git commit -m "feat: add ExtensionAdminDashboard JTE template"
```

---

### Task 7: Wire extension admin nav items in WebContext

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`

The existing `appendAdminLinks()` method (line ~147) adds hardcoded admin nav links. It's called from `buildNavLinks()` which is called from `shell()` which receives `ExtensionOptions` via `ctx.extensionOptions`.

- [ ] **Step 1: Add extension admin nav items in appendAdminLinks()**

In `appendAdminLinks()`, after the existing `devDashboardEnabled` block, add:

```kotlin
extensionOptions.adminNavItems.forEach { item ->
    links.add(ShellLink(item.label, url(item.url), item.icon, activeSection == item.url))
}
```

Note: `appendAdminLinks` currently has no access to `extensionOptions`. It's a private method on `WebContext`. The `WebContext` constructor receives `extensionOptions: ExtensionOptions` and stores it in a field. Add `extensionOptions` as a parameter to `appendAdminLinks`, or use the existing field. Check the constructor — `extensionOptions` is already a constructor parameter stored as a val, so it can be accessed directly.

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git commit -m "feat: append extension admin nav items in WebContext"
```

---

### Task 8: Wire extension admin routes and dashboard in App.kt

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`

This is the core wiring task. Changes needed:

1. Pass `extension` to `buildAdminRoutes()`
2. Add extension admin sections' routes to the admin contract
3. Add an extension admin dashboard route (`/admin/extensions`) if the extension has sections
4. Derive `AdminNavItem` list from `extension.adminSections()` and pass into `ExtensionOptions`

- [ ] **Step 1: Add extension parameter to buildAdminRoutes()**

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

Add `extension: PlatformExtension` parameter:
```kotlin
private fun buildAdminRoutes(
    appLabel: String,
    outboxRepository: OutboxRepository?,
    cache: MessageCache?,
    pageFactory: WebPageFactory,
    jteRenderer: TemplateRenderer,
    config: AppConfig,
    securityService: SecurityService,
    extension: PlatformExtension,
): RoutingHttpHandler
```

- [ ] **Step 2: Add extension admin section routes inside buildAdminRoutes()**

After the existing `routes += DevDashboardRoutes(...)` and `routes += UserAdminRoutes(...)`, add:

```kotlin
val extensionContext = ExtensionContext.forTesting(jteRenderer, config, securityService)
val sections = extension.adminSections(extensionContext)
if (sections.isNotEmpty()) {
    routes += prefix("/admin/extensions") {
        sections.forEach { section ->
            routes += section.route
        }
    }
    routes += "/admin/extensions" bind Method.GET to { req ->
        val ctx = WebContext(req, ExtensionOptions(adminNavItems = sections.map {
            AdminNavItem(it.navLabel, it.summaryCard.linkUrl, it.navIcon)
        }))
        val shell = ctx.shell("Extension Dashboard")
        val page = Page(ExtensionAdminDashboardPage(sections.map { it.summaryCard }, shell))
        Response(Status.OK).body(jteRenderer.render("ExtensionAdminDashboard.kte", page))
    }
}
```

Note: The exact `ExtensionContext` construction and `WebContext` usage may need adjustment based on how the existing code builds these in `buildBaseApp()`. Check how `ExtensionContext.forTesting()` is used and replicate the pattern from the existing `buildUiRoutes()` method.

- [ ] **Step 3: Pass extension to buildAdminRoutes() at the call site**

In `buildBaseApp()`, find the call to `buildAdminRoutes(...)` and add the `extension` parameter.

- [ ] **Step 4: Derive adminNavItems for ExtensionOptions**

In `buildFilterChain()` where `ExtensionOptions` is constructed, derive admin nav items from the extension:

```kotlin
val adminNavItems = extension.adminSections(ExtensionContext.forTesting(jteRenderer, config, securityService))
    .map { AdminNavItem(it.navLabel, "/admin/extensions", it.navIcon) }
ExtensionOptions(
    navItems = extension.navItems(ExtensionContext.forTesting(jteRenderer, config, securityService)),
    textResolver = extension.textResolver,
    adminNavItems = adminNavItems,
)
```

- [ ] **Step 5: Verify compilation**

Run: `mvn -pl platform-web compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt
git commit -m "feat: wire extension admin routes and dashboard in App.kt"
```

---

### Task 9: Write tests

**Files:**
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminSectionTest.kt`

Follow the test patterns from `AdminHtmlRoutesIntegrationTest.kt` and `ExtensionTextOverrideTest.kt`. The test base class is `WebTest` which provides an in-memory `HttpHandler` with Testcontainers PostgreSQL.

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
    fun `default PlatformExtension adminSections returns empty list`() {
        val extension = object : PlatformExtension {
            override val id = "test-extension"
        }
        val context = ExtensionContext.forTesting()
        assertEquals(emptyList<AdminSection>(), extension.adminSections(context))
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
            linkUrl = "/admin/extensions/payments",
            linkLabel = "Open",
        )
        assertEquals("Payments", card.title)
        assertEquals(1, card.metrics.size)
        assertEquals("/admin/extensions/payments", card.linkUrl)
        assertEquals("Open", card.linkLabel)
    }

    @Test
    fun `AdminSection holds id navLabel navIcon card and route`() {
        val route: ContractRoute = "/admin/extensions/test" meta { summary = "Test section" } bindContract Method.GET to { Response(OK) }
        val section = AdminSection(
            id = "test-section",
            navLabel = "Test",
            navIcon = "ri-test-line",
            summaryCard = AdminSummaryCard("Test", emptyList(), "/admin/extensions/test"),
            route = route,
        )
        assertEquals("test-section", section.id)
        assertEquals("Test", section.navLabel)
    }

    @Test
    fun `AdminNavItem holds label url and icon`() {
        val item = AdminNavItem("Billing", "/admin/extensions/billing", "ri-money-dollar-circle-line")
        assertEquals("Billing", item.label)
        assertEquals("/admin/extensions/billing", item.url)
        assertEquals("ri-money-dollar-circle-line", item.icon)
    }

    @Test
    fun `ExtensionOptions carries adminNavItems`() {
        val opts = ExtensionOptions(
            adminNavItems = listOf(AdminNavItem("Test", "/test", "ri-test-line")),
        )
        assertEquals(1, opts.adminNavItems.size)
        val empty = ExtensionOptions()
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
git commit -m "test: add AdminSectionTest for extension admin infrastructure"
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
