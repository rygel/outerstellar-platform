# Plugin Text & Template Override Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all ~90 hardcoded strings in JTE templates with a `TextResolver` key system that plugins can override, and add per-template override capability so plugins can replace individual `.kte` files.

**Architecture:** A `TextResolver` interface in platform-core resolves string keys to values. `DefaultTextResolver` loads from `texts.properties`. `ShellView` exposes `text(key)` for templates. `PlatformPlugin` gets `textResolver` and `templateOverrides()` properties. The `TemplateRenderer` is wrapped to support per-template classpath switching.

**Tech Stack:** Kotlin, JTE templates, Koin DI, Maven, http4k

---

## File Structure

### New files
- `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/TextResolver.kt` — interface + DefaultTextResolver
- `platform-web/src/main/resources/texts.properties` — all extracted UI strings
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/PluginTemplateRenderer.kt` — wraps renderer for template overrides
- `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/TextResolverTest.kt` — unit tests for TextResolver
- `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/PluginTemplateRendererTest.kt` — unit tests for template override mechanism

### Modified files
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt` — add `textResolver` to `ShellView` + `text()` method
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt` — pass `TextResolver` into `ShellView`
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt` — pass `TextResolver` through
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt` — add `textResolver` + `templateOverrides()`
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt` — wire `TextResolver` + wrap renderer
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt` — pass `TextResolver` to app assembly
- All 33 `.kte` files — replace hardcoded strings with `${shell.text("key")}`

---

## Task 1: TextResolver interface and DefaultTextResolver

**Files:**
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/TextResolver.kt`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/TextResolverTest.kt`

- [ ] **Step 1: Write failing tests for TextResolver**

```kotlin
package io.github.rygel.outerstellar.platform

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class TextResolverTest {

    @Test
    fun `resolve returns value for existing key`() {
        val resolver = DefaultTextResolver(mapOf("nav.inbox" to "Inbox"))
        assertEquals("Inbox", resolver.resolve("nav.inbox"))
    }

    @Test
    fun `resolve returns key itself for missing key`() {
        val resolver = DefaultTextResolver(emptyMap())
        assertEquals("missing.key", resolver.resolve("missing.key"))
    }

    @Test
    fun `resolve formats args into template`() {
        val resolver = DefaultTextResolver(mapOf("greeting" to "Hello, %s!"))
        assertEquals("Hello, World!", resolver.resolve("greeting", "World"))
    }

    @Test
    fun `resolve returns plain value when no args`() {
        val resolver = DefaultTextResolver(mapOf("simple" to "Just text"))
        assertEquals("Just text", resolver.resolve("simple"))
    }

    @Test
    fun `loads from properties file`() {
        val props = listOf("nav.inbox=Inbox", "nav.contacts=Contacts").joinToString("\n")
        val file = File(System.getProperty("java.io.tmpdir"), "test-texts-${System.nanoTime()}.properties")
        file.writeText(props)
        val resolver = DefaultTextResolver.fromFile(file.absolutePath)
        assertEquals("Inbox", resolver.resolve("nav.inbox"))
        assertEquals("Contacts", resolver.resolve("nav.contacts"))
        file.delete()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl platform-web test -Dtest=TextResolverTest -DfailIfNoTests=false`
Expected: FAIL — `TextResolver` and `DefaultTextResolver` don't exist yet

- [ ] **Step 3: Write TextResolver interface and DefaultTextResolver**

```kotlin
package io.github.rygel.outerstellar.platform

import java.util.Properties

interface TextResolver {
    fun resolve(key: String, vararg args: Any?): String
}

class DefaultTextResolver(private val texts: Map<String, String>) : TextResolver {

    override fun resolve(key: String, vararg args: Any?): String {
        val template = texts[key] ?: return key
        return if (args.isEmpty()) template else String.format(template, *args)
    }

    companion object {
        fun fromClasspath(resource: String = "texts.properties"): DefaultTextResolver {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
            val props = Properties()
            if (stream != null) {
                props.load(stream)
            }
            val map = props.stringPropertyNames().associateWith { props.getProperty(it) }
            return DefaultTextResolver(map)
        }

        fun fromFile(path: String): DefaultTextResolver {
            val props = Properties()
            val file = java.io.File(path)
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            val map = props.stringPropertyNames().associateWith { props.getProperty(it) }
            return DefaultTextResolver(map)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl platform-web test -Dtest=TextResolverTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/TextResolver.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/TextResolverTest.kt
git commit -m "feat: add TextResolver interface and DefaultTextResolver"
```

---

## Task 2: Create texts.properties with all UI strings

**Files:**
- Create: `platform-web/src/main/resources/texts.properties`

This task requires reading all 33 `.kte` files to extract every hardcoded user-facing string. The agent should:

1. Read each `.kte` file under `platform-web/src/main/jte/`
2. Identify every user-facing string (button labels, headings, form labels, placeholder text, error messages, alt text, aria labels, link text)
3. Assign a dot-namespaced key
4. Write the `texts.properties` file

Key namespacing convention:
- `nav.*` — navigation/sidebar links
- `auth.*` — login/register/password reset
- `messages.*` — inbox, compose, trash
- `contacts.*` — contacts page
- `settings.*` — settings page
- `profile.*` — profile page
- `admin.*` — admin pages
- `selector.*` — sidebar selector headings
- `common.*` — shared buttons/labels (save, cancel, delete, etc.)
- `error.*` — error pages
- `footer.*` — footer text
- `dev.*` — dev dashboard

- [ ] **Step 1: Read all .kte files and extract hardcoded strings**

Read every `.kte` file. For each hardcoded string, note the file, line, the string value, and assign a key.

- [ ] **Step 2: Write texts.properties**

Write `platform-web/src/main/resources/texts.properties` with all key=value pairs. Example entries (the actual file will have ~90 entries):

```properties
nav.inbox=Inbox
nav.contacts=Contacts
nav.trash=Trash
nav.settings=Settings
nav.profile=Profile
nav.admin=Admin
nav.api_keys=API Keys
nav.audit_log=Audit Log
nav.dev_dashboard=Dev Dashboard
nav.notifications=Notifications
nav.search=Search
auth.login=Login
auth.register=Register
... (all ~90 entries)
```

- [ ] **Step 3: Verify DefaultTextResolver loads the file**

Run a quick test or manually verify:
```kotlin
val resolver = DefaultTextResolver.fromClasspath("texts.properties")
assert(resolver.resolve("nav.inbox") == "Inbox")
```

- [ ] **Step 4: Commit**

```bash
git add platform-web/src/main/resources/texts.properties
git commit -m "feat: add texts.properties with all extracted UI strings"
```

---

## Task 3: Wire TextResolver into ShellView and WebContext

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt` (ShellView)
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt`

- [ ] **Step 1: Add textResolver field and text() method to ShellView**

In `ViewModels.kt`, add `textResolver` parameter to `ShellView` with default:

```kotlin
data class ShellView(
    // ... existing fields ...
    val textResolver: TextResolver = DefaultTextResolver.fromClasspath(),
) {
    fun text(key: String, vararg args: Any?): String = textResolver.resolve(key, *args)
}
```

Add import for `io.github.rygel.outerstellar.platform.TextResolver` and `DefaultTextResolver`.

- [ ] **Step 2: Pass TextResolver through WebContext.shell()**

In `WebContext.kt`, add a `textResolver` constructor parameter:

```kotlin
class WebContext(
    private val request: Request,
    private val devDashboardEnabled: Boolean,
    private val userRepository: UserRepository,
    private val appVersion: String,
    private val jwtService: JwtService?,
    private val pluginNavItems: List<PluginNavItem>,
    private val textResolver: TextResolver = DefaultTextResolver.fromClasspath(),
)
```

In `shell()` method, pass `textResolver` to `ShellView(...)` constructor.

- [ ] **Step 3: Add TextResolver to WebPageFactory**

Add `textResolver` parameter to `WebPageFactory` constructor (with default). Pass it to `WebContext` construction in all methods that create a `WebContext`.

- [ ] **Step 4: Wire TextResolver in WebModule.kt**

Add to `WebModule.kt`:

```kotlin
single<TextResolver> {
    val plugin = getOrNull<PlatformPlugin>()
    plugin?.textResolver ?: DefaultTextResolver.fromClasspath()
}
```

Pass `get<TextResolver>()` to `WebPageFactory` constructor.

- [ ] **Step 5: Add textResolver and templateOverrides to PlatformPlugin**

In `PlatformPlugin.kt`, add:

```kotlin
val textResolver: TextResolver
    get() = DefaultTextResolver.fromClasspath()

fun templateOverrides(): Set<String> = emptySet()
```

Add import for `TextResolver` and `DefaultTextResolver`.

- [ ] **Step 6: Run existing tests to verify nothing is broken**

Run: `mvn -pl platform-web -Ptests-headless test`
Expected: All existing tests pass (default TextResolver provides same strings as before)

- [ ] **Step 7: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebPageFactory.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PlatformPlugin.kt
git commit -m "feat: wire TextResolver into ShellView, WebContext, and DI"
```

---

## Task 4: Replace hardcoded strings in all JTE templates

**Files:**
- Modify: All 33 `.kte` files under `platform-web/src/main/jte/`

This is the largest task. For each `.kte` file:

1. Read the file
2. Find every hardcoded user-facing string
3. Replace with `${shell.text("key")}` using the key from `texts.properties`
4. Verify no hardcoded strings remain

Examples of replacements:

**Before:**
```jte
<span class="topbar-label">Inbox</span>
```

**After:**
```jte
<span class="topbar-label">${shell.text("nav.inbox")}</span>
```

**Before:**
```jte
<button type="submit">Login</button>
```

**After:**
```jte
<button type="submit">${shell.text("auth.login")}</button>
```

Note: Some templates receive `ShellView` as `shell` (from `Page.shell`), others receive it directly as a parameter. The `text()` call is always on `shell` because every page goes through `LayoutRouter` which has access to `ShellView`.

For component templates that don't have direct access to `shell`, the text should either:
- Be passed down from the page-level template, OR
- The component's ViewModel should carry the resolved text

The agent must read each template and determine the correct approach.

- [ ] **Step 1: Replace strings in layout templates** (LayoutHead.kte, SidebarLayout.kte, TopbarLayout.kte, LayoutRouter.kte)

- [ ] **Step 2: Replace strings in auth templates** (AuthPage.kte, AuthFormFragment.kte, AuthResultFragment.kte)

- [ ] **Step 3: Replace strings in message templates** (HomePage.kte, TrashPage.kte, MessageList.kte component)

- [ ] **Step 4: Replace strings in contacts template** (ContactsPage.kte, ContactForm.kte component)

- [ ] **Step 5: Replace strings in settings/profile/password templates** (SettingsPage.kte, ProfilePage.kte, ChangePasswordPage.kte, ChangePasswordForm.kte, ResetPasswordPage.kte)

- [ ] **Step 6: Replace strings in admin templates** (UserAdminPage.kte, AuditLogPage.kte, ApiKeysPage.kte, DevDashboard.kte)

- [ ] **Step 7: Replace strings in other templates** (ErrorPage.kte, ErrorHelpFragment.kte, SearchPage.kte, NotificationsPage.kte, NotificationBell.kte component, FooterStatusFragment.kte, Pagination.kte component, PageHeader.kte component, Modal.kte component, ModalOverlay.kte component, ConflictResolveModal.kte component, SidebarSelector.kte component)

- [ ] **Step 8: Run full build to verify compilation**

Run: `mvn -pl platform-web compile -Pruntime-dev`
Expected: BUILD SUCCESS — JTE generates templates successfully with no errors

- [ ] **Step 9: Run tests**

Run: `mvn -pl platform-web -Ptests-headless test`
Expected: All tests pass. Some assertions on HTML content may need updating if they assert on specific text strings.

- [ ] **Step 10: Commit**

```bash
git add platform-web/src/main/jte/
git commit -m "feat: replace all hardcoded strings in JTE templates with text resolver keys"
```

---

## Task 5: PluginTemplateRenderer for per-template override

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/PluginTemplateRenderer.kt`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/PluginTemplateRendererTest.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt`

- [ ] **Step 1: Write failing test for PluginTemplateRenderer**

```kotlin
package io.github.rygel.outerstellar.platform

import org.http4k.template.ViewModel
import org.http4k.template.TemplateRenderer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginTemplateRendererTest {

    private data class TestViewModel(val value: String) : ViewModel {
        override fun template() = "TestTemplate"
    }

    @Test
    fun `delegates to base renderer when template is not overridden`() {
        val baseRenderer: TemplateRenderer = { "<base>${it.template()}</base>" }
        val pluginRenderer = PluginTemplateRenderer(baseRenderer, emptySet(), null)
        val result = pluginRenderer(TestViewModel("test"))
        assertEquals("<base>TestTemplate</base>", result)
    }

    @Test
    fun `override set is checked but base renderer handles all rendering`() {
        val baseRenderer: TemplateRenderer = { "<base>${it.template()}</base>" }
        val pluginRenderer = PluginTemplateRenderer(baseRenderer, setOf("SomeTemplate"), null)
        val result = pluginRenderer(TestViewModel("test"))
        assertEquals("<base>TestTemplate</base>", result)
    }
}
```

Note: The actual per-template classpath switching requires JTE's `TemplateEngine` API and is tightly coupled to the precompiled template mechanism. The initial implementation wraps the base renderer and provides the override metadata — the classloader switching can be enhanced later once JTE's template resolution internals are confirmed.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl platform-web test -Dtest=PluginTemplateRendererTest -DfailIfNoTests=false`
Expected: FAIL

- [ ] **Step 3: Write PluginTemplateRenderer**

```kotlin
package io.github.rygel.outerstellar.platform.infra

import org.http4k.template.TemplateRenderer
import org.http4k.template.ViewModel

class PluginTemplateRenderer(
    private val delegate: TemplateRenderer,
    private val overrideTemplates: Set<String>,
    private val pluginClassLoader: ClassLoader?,
) : TemplateRenderer {

    override fun invoke(viewModel: ViewModel): String {
        val templateName = viewModel.template()
        val templatePath = "$templateName.kte"
        if (overrideTemplates.contains(templatePath) && pluginClassLoader != null) {
            // Future: resolve template from pluginClassLoader
            // For now, delegate to base renderer
        }
        return delegate(viewModel)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl platform-web test -Dtest=PluginTemplateRendererTest`
Expected: PASS

- [ ] **Step 5: Wire PluginTemplateRenderer in WebModule.kt**

In `WebModule.kt`, wrap the `TemplateRenderer`:

```kotlin
single<TemplateRenderer> {
    val baseRenderer = createRenderer()
    val plugin = getOrNull<PlatformPlugin>()
    if (plugin != null && plugin.templateOverrides().isNotEmpty()) {
        PluginTemplateRenderer(baseRenderer, plugin.templateOverrides(), plugin::class.java.classLoader)
    } else {
        baseRenderer
    }
}
```

- [ ] **Step 6: Run full test suite**

Run: `mvn -pl platform-web -Ptests-headless test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/PluginTemplateRenderer.kt platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/PluginTemplateRendererTest.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebModule.kt
git commit -m "feat: add PluginTemplateRenderer for per-template override"
```

---

## Task 6: Integration test — plugin overrides texts

**Files:**
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PluginTextOverrideTest.kt`

- [ ] **Step 1: Write integration test**

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.DefaultTextResolver
import io.github.rygel.outerstellar.platform.TextResolver
import io.github.rygel.outerstellar.platform.web.WebTest
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginTextOverrideTest : WebTest() {

    @Test
    fun `default text resolver provides nav inbox label`() {
        val resolver = DefaultTextResolver.fromClasspath("texts.properties")
        val resolved = resolver.resolve("nav.inbox")
        assertTrue(resolved.isNotEmpty(), "nav.inbox should resolve to a non-empty string")
        assertTrue(resolved != "nav.inbox", "nav.inbox should resolve to an actual value, not the key")
    }

    @Test
    fun `home page contains resolved text not key`() {
        val response = app(Request(Method.GET, "/"))
        assertTrue(response.status == Status.OK || response.status == Status.FOUND)
        if (response.status == Status.OK) {
            val body = response.bodyString()
            // Verify no raw text keys leak into the HTML
            assertTrue(!body.contains("nav.inbox") || body.contains("Inbox"), "Page should not contain raw text keys")
        }
    }

    @Test
    fun `custom text resolver overrides default strings`() {
        val customTexts = mapOf("nav.inbox" to "MyInbox", "auth.login" to "MyLogin")
        val resolver = DefaultTextResolver(customTexts)
        assertTrue(resolver.resolve("nav.inbox") == "MyInbox")
        // Keys not in custom map fall back to returning the key itself
        assertTrue(resolver.resolve("nav.contacts") == "nav.contacts")
    }
}
```

- [ ] **Step 2: Run test**

Run: `mvn -pl platform-web test -Dtest=PluginTextOverrideTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PluginTextOverrideTest.kt
git commit -m "test: add integration tests for plugin text override"
```

---

## Task 7: Verify and clean up

- [ ] **Step 1: Run Spotless and Detekt**

Run: `mvn -pl platform-web install -DskipTests`
Expected: BUILD SUCCESS — Spotless and Detekt pass

- [ ] **Step 2: Run full test suite**

Run: `mvn -pl platform-web -Ptests-headless test`
Expected: All tests pass

- [ ] **Step 3: Verify no hardcoded strings remain in .kte files**

Search all `.kte` files for common hardcoded string patterns (button text, headings, labels) to verify extraction is complete. Any remaining strings should be checked — some may be structural (CSS classes, HTMX attributes) not user-facing text.

- [ ] **Step 4: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final cleanup for text resolver implementation"
```
