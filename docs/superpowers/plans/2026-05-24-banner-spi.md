# Banner SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `BannerProvider` SPI and `Banner` model so plugins can inject banners into the platform layout, rendered server-side between the header and main content.

**Architecture:** New `Banner`/`BannerProvider` types in `platform-core` (consistent with `SearchProvider`). `PlatformPlugin` gets a new `bannerProviders()` method. Providers are wired through `PluginOptions` → `WebContext` → `ShellView` → layout templates. A new `Banners.kte` partial renders them with DaisyUI alert styles and HTMX dismiss.

**Tech Stack:** Kotlin, http4k, JTE templates, DaisyUI alerts, HTMX

---

### Task 1: Banner model and BannerProvider interface

**Files:**
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/banner/Banner.kt`

- [ ] **Step 1: Create the Banner model and BannerProvider interface**

```kotlin
package io.github.rygel.outerstellar.platform.banner

import java.util.UUID

enum class BannerSeverity {
    CRITICAL,
    WARNING,
    INFO,
    SCHEDULED_MAINTENANCE,
}

data class Banner(
    val id: String,
    val title: String,
    val body: String,
    val severity: BannerSeverity = BannerSeverity.INFO,
    val isDismissible: Boolean = true,
    val dismissUrl: String? = null,
)

interface BannerProvider {
    fun getBanners(userId: UUID, userRole: String): List<Banner>
}
```

- [ ] **Step 2: Compile platform-core**

Run: `mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/banner/Banner.kt
git commit -m "feat(core): add Banner model and BannerProvider interface (#260)"
```

---

### Task 2: Add bannerProviders to PlatformPlugin and PluginOptions

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt:31-35` (PluginOptions)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt:168-169` (PlatformPlugin interface)

- [ ] **Step 1: Add `bannerProviders` field to PluginOptions**

In `PlatformPlugin.kt`, after line 34 (`val adminNavItems`), add:

```kotlin
    val bannerProviders: List<BannerProvider> = emptyList(),
```

Add import at top of file:
```kotlin
import io.github.rygel.outerstellar.platform.banner.BannerProvider
```

- [ ] **Step 2: Add `bannerProviders()` method to PlatformPlugin interface**

In `PlatformPlugin.kt`, after line 168 (`fun adminSections(...)`) and before line 169 (`}`), add:

```kotlin

    fun bannerProviders(context: PluginContext): List<BannerProvider> = emptyList()
```

- [ ] **Step 3: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt
git commit -m "feat(web): add bannerProviders to PlatformPlugin and PluginOptions (#260)"
```

---

### Task 3: Add banners to ShellView

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt:52` (ShellView)

- [ ] **Step 1: Add `banners` field to ShellView**

In `ViewModels.kt`, after line 52 (`val ogImage: String = "",`), add:

```kotlin
    val banners: List<Banner> = emptyList(),
```

Add import at top of file:
```kotlin
import io.github.rygel.outerstellar.platform.banner.Banner
```

- [ ] **Step 2: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt
git commit -m "feat(web): add banners field to ShellView (#260)"
```

---

### Task 4: Wire bannerProviders through WebContext

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt:22-31` (constructor)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt:220-266` (shell method)

- [ ] **Step 1: Add `bannerProviders` parameter to WebContext constructor**

In `WebContext.kt`, after line 31 (`private val sidebarFactory`), add:

```kotlin
    private val bannerProviders: List<BannerProvider> = emptyList(),
```

Add import:
```kotlin
import io.github.rygel.outerstellar.platform.banner.BannerProvider
```

- [ ] **Step 2: Collect banners in `shell()` method**

In `WebContext.kt`, inside the `shell()` method, before the `return ShellView(` on line 225, add banner collection logic:

```kotlin
        val user = this.user
        val banners = if (user != null && bannerProviders.isNotEmpty()) {
            bannerProviders
                .flatMap { it.getBanners(user.id, user.role.name) }
                .sortedBy { it.severity.ordinal }
        } else {
            emptyList()
        }
```

Then add `banners = banners,` as the last parameter to the `ShellView(...)` constructor, after line 264 (`appBaseUrl = appBaseUrl,`).

**Note:** The existing `this.user` property on WebContext returns the authenticated user (or null). Check if it's already available in `shell()` scope. If `user` is referenced elsewhere in `shell()` for the `username` and `isLoggedIn` fields, reuse the same pattern. If not, use the existing `user` property directly.

- [ ] **Step 3: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt
git commit -m "feat(web): wire BannerProviders through WebContext to ShellView (#260)"
```

---

### Task 5: Wire PluginOptions.bannerProviders in App.kt and Filters.kt

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt:623-627` (PluginOptions construction)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt:282-297` (stateFilter)

- [ ] **Step 1: Pass bannerProviders in PluginOptions construction in App.kt**

In `App.kt`, at the PluginOptions construction (around line 623-627), after `adminNavItems = adminNavItems,`, add:

```kotlin
                    bannerProviders = plugin?.bannerProviders(ctx.pluginContext()) ?: emptyList(),
```

- [ ] **Step 2: Pass bannerProviders through stateFilter to WebContext in Filters.kt**

In `Filters.kt`, update `stateFilter()` to accept and pass `bannerProviders`. The `stateFilter` function signature (around line 282) needs a new parameter. The `WebContext` constructor call (around line 287-297) needs to pass it through.

This requires reading `Filters.kt` to see the exact signature and constructor call. The pattern is:
1. Add `bannerProviders: List<BannerProvider> = emptyList()` parameter to `stateFilter()`
2. Add `bannerProviders` to the `WebContext(...)` constructor call inside the filter

- [ ] **Step 3: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git commit -m "feat(web): wire bannerProviders from plugin through filter chain (#260)"
```

---

### Task 6: Create Banners.kte partial template

**Files:**
- Create: `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/components/Banners.kte`

- [ ] **Step 1: Create the Banners.kte template**

```jte
@param banners: List<io.github.rygel.outerstellar.platform.banner.Banner>
<div class="flex flex-col gap-2 px-6 pt-4" aria-live="polite">
@foreach(val banner : banners)
    <div class="alert
        @if(banner.severity == io.github.rygel.outerstellar.platform.banner.BannerSeverity.CRITICAL) alert-error
        @elseif(banner.severity == io.github.rygel.outerstellar.platform.banner.BannerSeverity.WARNING) alert-warning
        @elseif(banner.severity == io.github.rygel.outerstellar.platform.banner.BannerSeverity.SCHEDULED_MAINTENANCE) alert-accent
        @else alert-info
        @endif
        flex items-center gap-3">
        <div class="flex-1">
            <h3 class="font-bold text-sm">${banner.title}</h3>
            <p class="text-sm">${banner.body}</p>
        </div>
        @if(banner.isDismissible && banner.dismissUrl != null)
            <button class="btn btn-sm btn-ghost"
                    hx-post="${banner.dismissUrl}"
                    hx-target="closest .alert"
                    hx-swap="outerHTML"
                    aria-label="Dismiss ${banner.title}">
                &times;
            </button>
        @endif
    </div>
@endforeach
</div>
```

- [ ] **Step 2: Commit**

```bash
git add platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/components/Banners.kte
git commit -m "feat(web): add Banners.kte partial template (#260)"
```

---

### Task 7: Render banners in layout templates

**Files:**
- Modify: `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/layouts/SidebarLayout.kte:59-60`
- Modify: `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/layouts/TopbarLayout.kte:69-70`

- [ ] **Step 1: Add banner rendering to SidebarLayout.kte**

Between line 59 (`</header>`) and line 60 (`<main>`), insert:

```jte
        @if(!shell.banners.isEmpty())
            @template.io.github.rygel.outerstellar.platform.web.components.Banners(banners = shell.banners)
        @endif
```

- [ ] **Step 2: Add banner rendering to TopbarLayout.kte**

Between line 69 (`<div class="flex-1 flex flex-col overflow-hidden">`) and line 70 (`<main>`), insert:

```jte
        @if(!shell.banners.isEmpty())
            @template.io.github.rygel.outerstellar.platform.web.components.Banners(banners = shell.banners)
        @endif
```

- [ ] **Step 3: Compile platform-web (includes JTE precompilation)**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/layouts/SidebarLayout.kte platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/layouts/TopbarLayout.kte
git commit -m "feat(web): render banners in SidebarLayout and TopbarLayout (#260)"
```

---

### Task 8: Integration test — banners rendered for authenticated users

**Files:**
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/BannerIntegrationTest.kt`

- [ ] **Step 1: Write the integration test**

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.banner.Banner
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.banner.BannerSeverity
import io.github.rygel.outerstellar.platform.web.PlatformPlugin
import java.util.UUID
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.shouldHaveStatus
import org.junit.jupiter.api.Test

class BannerIntegrationTest : WebTest() {

    @Test
    fun `banners from BannerProvider are rendered in page layout`() {
        val provider = object : BannerProvider {
            override fun getBanners(userId: UUID, userRole: String): List<Banner> =
                listOf(
                    Banner(
                        id = "test:1",
                        title = "Test Banner",
                        body = "This is a test banner",
                        severity = BannerSeverity.INFO,
                        isDismissible = true,
                        dismissUrl = "/api/v1/banners/test:1/dismiss",
                    ),
                )
        }

        val app = buildApp()
        val (token, _, _) = withAuthenticatedUser()

        val response = app(Request(Method.GET, "/").header("Cookie", "app_session=$token"))

        response shouldHaveStatus Status.OK
        val body = response.bodyString()
        assert(body.contains("Test Banner")) { "Expected banner title in response" }
        assert(body.contains("This is a test banner")) { "Expected banner body in response" }
        assert(body.contains("alert-info")) { "Expected alert-info CSS class for INFO severity" }
        assert(body.contains("/api/v1/banners/test:1/dismiss")) { "Expected dismiss URL in response" }
    }

    @Test
    fun `no banners rendered when user is not authenticated`() {
        val app = buildApp()

        val response = app(Request(Method.GET, "/auth"))

        response shouldHaveStatus Status.OK
        val body = response.bodyString()
        assert(!body.contains("Test Banner")) { "Expected no banner for unauthenticated request" }
    }

    @Test
    fun `critical banners are ordered before info banners`() {
        val provider = object : BannerProvider {
            override fun getBanners(userId: UUID, userRole: String): List<Banner> =
                listOf(
                    Banner(id = "test:info", title = "Info Banner", body = "info body", severity = BannerSeverity.INFO),
                    Banner(id = "test:critical", title = "Critical Banner", body = "critical body", severity = BannerSeverity.CRITICAL),
                )
        }

        val app = buildApp()
        val (token, _, _) = withAuthenticatedUser()

        val response = app(Request(Method.GET, "/").header("Cookie", "app_session=$token"))

        response shouldHaveStatus Status.OK
        val body = response.bodyString()
        val criticalPos = body.indexOf("Critical Banner")
        val infoPos = body.indexOf("Info Banner")
        assert(criticalPos < infoPos) { "Critical banner should appear before info banner" }
        assert(body.contains("alert-error")) { "Expected alert-error CSS class for CRITICAL severity" }
    }
}
```

**Important:** This test cannot work yet because `WebTest.buildApp()` does not accept a `BannerProvider` or plugin. The test needs to be updated after Task 5 is verified to wire correctly. The test may need to use a test `PlatformPlugin` or pass providers through a modified `buildApp()`.

**Revision:** Since `WebTest.buildApp()` calls `app(...)` which constructs the full http4k app, and the plugin is not currently a parameter, the test needs a different approach. The simplest path:

1. Create a stub `PlatformPlugin` that returns the test `BannerProvider` from `bannerProviders()`
2. Add a `plugin` parameter to `WebTest.buildApp()` (or create a separate helper)

However, since modifying `WebTest` affects all existing tests, the safer approach is to add an optional `plugin: PlatformPlugin? = null` parameter to `buildApp()` and thread it through to `app()`. Read `App.kt`'s `app()` function signature to understand how the plugin is passed.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl platform-web -am test -Dtest=BannerIntegrationTest`
Expected: FAIL (banners not yet wired through buildApp)

- [ ] **Step 3: Wire the test plugin through buildApp and verify tests pass**

This step requires reading `app()` in `App.kt` to see how the plugin parameter works, then adding the optional `plugin` parameter to `WebTest.buildApp()`.

Run: `mvn -pl platform-web -am test -Dtest=BannerIntegrationTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/BannerIntegrationTest.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt
git commit -m "test(web): add BannerProvider integration tests (#260)"
```

---

### Task 9: Full reactor build and verification

- [ ] **Step 1: Run the full non-desktop reactor**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Run the new banner tests explicitly**

Run: `mvn -pl platform-web -am test -Dtest=BannerIntegrationTest`
Expected: All 3 tests pass
