# Simplify Extension API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the extension developer experience by removing deprecated methods from PlatformExtension, adding a page() convenience, cleaning up ExtensionHostContext, and providing a zero-config JTE parent POM.

**Architecture:** Remove 8 deprecated/redundant methods from PlatformExtension so contribute() is the single entry point. Add page()/publicPage() convenience methods to the route registry. Move template overrides into the contribution flow. Remove deprecated aliases from ExtensionHostContext. Publish an extension-parent POM with JTE plugin config.

**Tech Stack:** Kotlin, Maven, http4k, JTE

---

### Task 1: Add template overrides to ExtensionContributionContext

Move `templateOverrides()` from the PlatformExtension interface into the contribution flow so everything goes through `contribute()`.

**Files:**
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContributionContext.kt`
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContribution.kt`

- [ ] **Step 1: Add ExtensionTemplateContributionRegistry to ExtensionContributionContext.kt**

Add after `ExtensionAssetContributionRegistry` (after line 207):

```kotlin
class ExtensionTemplateContributionRegistry internal constructor() {
    private val overrides = mutableSetOf<String>()

    fun override(templateName: String) {
        overrides += templateName
    }

    fun override(vararg templateNames: String) {
        overrides += templateNames.toSet()
    }

    internal fun snapshot(): Set<String> = overrides.toSet()
}
```

Add the registry field to `ExtensionContributionContext` constructor (after line 26, before `) {`):

```kotlin
    internal val templateOverrideRegistry: ExtensionTemplateContributionRegistry = ExtensionTemplateContributionRegistry(),
```

Add public accessor (after line 35, before `}`):

```kotlin
    val templates: ExtensionTemplateContributionRegistry = templateOverrideRegistry
```

- [ ] **Step 2: Add templateOverrides field to ExtensionContribution data class**

In `ExtensionContribution.kt`, add field to data class constructor (after line 20, before `)`):

```kotlin
    val templateOverrides: Set<String> = emptySet(),
```

In `ExtensionContribution.from()` companion method, add to the returned ExtensionContribution (after line 122, before the closing `)` of ExtensionOptions):

Add `templateOverrides = contributionContext.templateOverrideRegistry.snapshot()` as a new constructor parameter of `ExtensionContribution(...)` (after the `options = ...` parameter, around line 123).

- [ ] **Step 3: Compile platform-extension-api**

Run: `mvn -pl platform-extension-api -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContributionContext.kt platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContribution.kt
git commit -m "feat: add template override registry to ExtensionContributionContext"
```

---

### Task 2: Add page() convenience to ExtensionRouteContributionRegistry

**Files:**
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContributionContext.kt`

- [ ] **Step 1: Add host parameter to ExtensionRouteContributionRegistry constructor**

Change line 38 from:

```kotlin
class ExtensionRouteContributionRegistry internal constructor() {
```

to:

```kotlin
class ExtensionRouteContributionRegistry internal constructor(
    private val host: ExtensionHostContext,
) {
```

Update the construction in `ExtensionContributionContext` — change the `routeRegistry` default (line 18) from:

```kotlin
    internal val routeRegistry: ExtensionRouteContributionRegistry = ExtensionRouteContributionRegistry(),
```

to:

```kotlin
    internal val routeRegistry: ExtensionRouteContributionRegistry = ExtensionRouteContributionRegistry(host),
```

- [ ] **Step 2: Add page() and publicPage() methods**

The `model` lambda returns `Any` — the JTE ViewModel object. The renderer calls `template()` on it to find the template. No `templateName` parameter needed since JTE ViewModels are self-describing.

Add after the `staticAssets` method (after line 78, before `internal fun snapshot()`):

```kotlin
    fun page(
        path: String,
        description: String = path,
        group: RouteGroup = RouteGroup.ProtectedUi,
        model: (Request) -> Any = { Unit },
    ) {
        val route =
            path meta { summary = description } bindContract GET to
                { req: Request ->
                    val viewModel = model(req)
                    val html = host.rendering.renderer(viewModel)
                    Response(OK)
                        .header("content-type", "text/html; charset=utf-8")
                        .body(html.toString())
                }
        register(route, group, description, path, "GET")
    }

    fun publicPage(
        path: String,
        description: String = path,
        model: (Request) -> Any = { Unit },
    ) = page(path, description, RouteGroup.PublicUi, model)
```

Add missing imports to the file header:

```kotlin
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.contract.meta
import org.http4k.contract.bindContract
```

- [ ] **Step 3: Compile platform-extension-api**

Run: `mvn -pl platform-extension-api -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContributionContext.kt
git commit -m "feat: add page() and publicPage() convenience to route registry"
```

---

### Task 3: Clean up PlatformExtension interface and ExtensionHostContext

Remove all deprecated methods and aliases.

**Files:**
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/PlatformExtensionApi.kt`
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ExtensionContributionContext.kt`

- [ ] **Step 1: Remove deprecated methods from PlatformExtension interface**

In `PlatformExtensionApi.kt`, replace the entire `PlatformExtension` interface (lines 252-314) with:

```kotlin
interface PlatformExtension {
    val id: String
    val appLabel: String
        get() = "Outerstellar"

    val manifest: ExtensionManifest
        get() = ExtensionManifest(id = id, appLabel = appLabel)

    val mode: PlatformMode
        get() = PlatformMode.FullPlatform

    val textResolver: TextResolver?
        get() = null

    val migrations: ExtensionMigrations?
        get() = null

    fun contribute(context: ExtensionContributionContext) {}
}
```

This removes: `migrationLocation`, `migrationHistoryTable`, `migrationNames`, `templateOverrides`, `routeRegistrations`, `includePlatformPages`, `layoutRenderer`, `filters`, `adminSections`, `bannerProviders`.

- [ ] **Step 2: Remove unused imports from PlatformExtensionApi.kt**

Remove these imports that are no longer needed after removing deprecated methods:

```kotlin
import io.github.rygel.outerstellar.platform.banner.BannerProvider
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.web.AdminSection
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.routing.RoutingHttpHandler
```

- [ ] **Step 3: Remove deprecated aliases from ExtensionHostContext**

In `PlatformExtensionApi.kt`, remove lines 179-201 (the 6 `@Deprecated` properties: `renderer`, `config`, `apiKeyService`, `oauthService`, `userRepository`, `notificationService`).

- [ ] **Step 4: Remove ExtensionContext typealias**

In `PlatformExtensionApi.kt`, remove line 234:

```kotlin
typealias ExtensionContext = ExtensionHostContext
```

- [ ] **Step 5: Update web/ExtensionContributionContext.kt typealias file**

Add a new typealias for `ExtensionTemplateContributionRegistry`:

```kotlin
typealias ExtensionTemplateContributionRegistry =
    io.github.rygel.outerstellar.platform.extension.ExtensionTemplateContributionRegistry
```

- [ ] **Step 6: Compile platform-extension-api**

Run: `mvn -pl platform-extension-api -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS (there will be compile errors in downstream modules — that's expected, we fix them next)

- [ ] **Step 7: Commit**

```bash
git add platform-extension-api/
git commit -m "feat: remove deprecated methods from PlatformExtension and ExtensionHostContext"
```

---

### Task 4: Update ExtensionContribution.from() to only call contribute()

**Files:**
- Modify: `platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContribution.kt`

- [ ] **Step 1: Simplify ExtensionContribution.from()**

Replace the body of `from()` (lines 81-125) with:

```kotlin
        fun from(
            extension: PlatformExtension?,
            fallbackMode: PlatformMode,
            context: ExtensionHostContext?,
        ): ExtensionContribution {
            if (extension == null || context == null) {
                return ExtensionContribution(mode = fallbackMode, appLabel = "Outerstellar")
            }

            val contributionContext = ExtensionContributionContext(host = context)
            extension.contribute(contributionContext)

            val adminSections = contributionContext.adminRegistry.snapshot()
            val manifest = extension.manifest
            val effectiveOwnership = manifest.ownership.withMode(extension.mode)
            val routeRegistrations = contributionContext.routeRegistry.snapshot()
            val assets = contributionContext.assetRegistry.snapshot()
            validateExtensionContribution(manifest, effectiveOwnership, routeRegistrations, assets)

            return ExtensionContribution(
                mode = extension.mode,
                appLabel = manifest.appLabel,
                manifest = manifest,
                effectiveOwnership = effectiveOwnership,
                includedPlatformPages = contributionContext.platformPageRegistry.snapshot(),
                routeRegistrations = routeRegistrations,
                filters = contributionContext.filterRegistry.snapshot(),
                adminSections = adminSections,
                bannerProviders = contributionContext.bannerRegistry.snapshot(),
                templateOverrides = contributionContext.templateOverrideRegistry.snapshot(),
                options =
                    ExtensionOptions(
                        navItems = contributionContext.navigationRegistry.snapshot(),
                        textResolver = extension.textResolver,
                        adminNavItems =
                            adminSections.map { section ->
                                AdminNavItem(section.navLabel, section.summaryCard.linkUrl, section.navIcon)
                            },
                        layoutRenderer = contributionContext.layoutRegistry.snapshot(),
                        assets = assets,
                    ),
            )
        }
```

The removed lines are the calls to deprecated extension methods:
- `extension.includePlatformPages()` → now done in `contribute()`
- `extension.routeRegistrations(context)` → now done in `contribute()`
- `extension.filters(context)` → now done in `contribute()`
- `extension.adminSections(context)` → now done in `contribute()`
- `extension.bannerProviders(context)` → now done in `contribute()`
- `extension.layoutRenderer(context)` → now done in `contribute()`

- [ ] **Step 2: Compile platform-extension-api**

Run: `mvn -pl platform-extension-api -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-extension-api/src/main/kotlin/io/github/rygel/outerstellar/platform/extension/ExtensionContribution.kt
git commit -m "refactor: ExtensionContribution.from() only calls contribute()"
```

---

### Task 5: Update platform-web consumers

Fix compile errors in platform-web caused by the API changes.

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/di/WebFactory.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/ExtensionContributionTest.kt`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/AdminSectionTest.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`

- [ ] **Step 1: Update WebFactory.kt to accept templateOverrides from ExtensionContribution**

Change `createWebComponents()` signature to accept `ExtensionContribution?` instead of `PlatformExtension?`. Replace the `extension: PlatformExtension? = null` parameter with `extensionContribution: ExtensionContribution? = null`.

Replace lines 83-90:

```kotlin
    val baseRenderer = createRenderer(config.runtime)
    val overrides = extensionContribution?.templateOverrides
    val extensionClassLoader = extensionContribution?.manifest?.id
    val templateRenderer: TemplateRenderer =
        if (extensionClassLoader != null && overrides != null && overrides.isNotEmpty()) {
            ExtensionTemplateRenderer(baseRenderer, overrides, null)
        } else {
            baseRenderer
        }
```

Note: The `ExtensionTemplateRenderer` currently takes `extension::class.java.classLoader` as the 3rd parameter. Since we no longer have the extension object here, pass `null` for the classLoader (the classLoader was used for template resolution in the extension's classpath — this needs review; if the extension's templates are on the classpath via the dependency mechanism, `null` should work since the platform's classloader will find them). If this breaks, we'll need to pass the extension classloader separately.

Also add the import: `import io.github.rygel.outerstellar.platform.extension.ExtensionContribution`

And remove the import: `import io.github.rygel.outerstellar.platform.extension.PlatformExtension`

- [ ] **Step 2: Update App.kt to pass ExtensionContribution to WebFactory**

In `App.kt`, the `createServerComponents` function already creates `extensionContribution` at line 42. Find where `createWebComponents` is called and change the `extension = extension` parameter to `extensionContribution = extensionContribution`.

- [ ] **Step 3: Update ExtensionContributionTest.kt**

The test at line 80-141 (`collects extension extension points once into a single contribution`) tests the old deprecated methods (`routeRegistrations()`, `filters()`, `adminSections()`, `bannerProviders()`, `layoutRenderer()`). Rewrite this test to use `contribute()` instead:

```kotlin
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
```

Also update the `extensionContext()` helper method (lines 370-380) to use `ExtensionHostContext.forTesting` directly instead of `ExtensionContext.forTesting`, and use the new grouped parameter names:

```kotlin
    private fun extensionContext(): ExtensionHostContext {
        val context =
            ExtensionHostContext.forTesting(
                rendering = mockk<HostRendering>(relaxed = true),
                users = mockk<HostUsers>(relaxed = true),
                security = HostSecurity(
                    apiKeys = mockk<HostApiKeys>(relaxed = true),
                    oauth = mockk<HostOAuth>(relaxed = true),
                ),
            )
        assertNotNull(context)
        return context
    }
```

Add missing imports: `import io.github.rygel.outerstellar.platform.extension.HostRendering`, `import io.github.rygel.outerstellar.platform.extension.HostApiKeys`, `import io.github.rygel.outerstellar.platform.extension.HostOAuth`, `import io.github.rygel.outerstellar.platform.extension.HostSecurity`, `import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext`

Remove unused imports: `import io.github.rygel.outerstellar.platform.persistence.UserRepository`, `import io.github.rygel.outerstellar.platform.security.ApiKeyService`, `import io.github.rygel.outerstellar.platform.security.OAuthService`

- [ ] **Step 4: Update AdminSectionTest.kt**

The first test (`default PlatformExtension adminSections returns empty list`) calls `extension.adminSections(context)` which is being removed from the interface. Replace the entire test with a test that verifies an empty `contribute()` produces no admin sections:

```kotlin
    @Test
    fun `default PlatformExtension contribute produces no admin sections`() {
        val extension =
            object : PlatformExtension {
                override val id: String = "test-extension"
            }
        val context = extensionHostContext()
        val contribution = ExtensionContribution.from(extension, PlatformMode.FullPlatform, context)
        assertTrue(contribution.adminSections.isEmpty())
    }
```

Also update the helper to use the new API:

```kotlin
    private fun extensionHostContext(): ExtensionHostContext =
        ExtensionHostContext.forTesting(
            rendering = mockk<HostRendering>(relaxed = true),
            users = mockk<HostUsers>(relaxed = true),
            security = HostSecurity(
                apiKeys = mockk<HostApiKeys>(relaxed = true),
                oauth = mockk<HostOAuth>(relaxed = true),
            ),
        )
```

Add imports: `import io.github.rygel.outerstellar.platform.extension.*`, `import io.github.rygel.outerstellar.platform.extension.ExtensionContribution`

Remove unused imports: `import io.github.rygel.outerstellar.platform.persistence.UserRepository`, `import io.github.rygel.outerstellar.platform.security.ApiKeyService`, `import io.github.rygel.outerstellar.platform.security.OAuthService`, `import org.http4k.template.TemplateRenderer`

- [ ] **Step 5: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS

- [ ] **Step 6: Run tests**

Run: `mvn -pl platform-web -am test -Dtest=ExtensionContributionTest -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add platform-web/
git commit -m "refactor: update platform-web consumers for simplified extension API"
```

---

### Task 6: Update starter-extension-app

**Files:**
- Modify: `starter-extension-app/starter-extension/src/main/kotlin/com/example/outerstellar/starter/extension/StarterPlatformExtension.kt`
- Modify: `starter-extension-app/starter-extension/src/test/kotlin/com/example/outerstellar/starter/extension/StarterPlatformExtensionTestContext.kt`

- [ ] **Step 1: Simplify StarterPlatformExtension to use publicPage()**

The `page()` method's `model` lambda returns the ViewModel directly (type `Any`). JTE's renderer calls `template()` on the ViewModel to find the template. Usage:

```kotlin
        context.routes.publicPage("/", "Starter home") { _ ->
            StarterIndexPage(platformVersion = context.host.app.version)
        }
```

Replace lines 22-36 (the home route boilerplate) with the single line above. Keep the health route and current-user route as manual `publicUi`/`protectedUi` registrations (they return plain text, not templates).

- [ ] **Step 2: Update StarterPlatformExtensionTestContext.kt**

Change `ExtensionContext.forTesting(...)` to `ExtensionHostContext.forTesting(...)` with the new grouped parameters. Same pattern as Task 5.

- [ ] **Step 3: Compile starter-extension-app**

Run: `mvn -pl starter-extension-app/starter-extension -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true" -f starter-extension-app/pom.xml`

Note: The starter-extension-app is NOT part of the main reactor. It has its own parent POM and is built separately. Build from its directory or use `-f`.

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add starter-extension-app/
git commit -m "refactor: update starter-extension-app to use publicPage() and cleaned API"
```

---

### Task 7: Create outerstellar-platform-extension-parent POM

**Files:**
- Create: `outerstellar-platform-extension-parent/pom.xml`
- Modify: `pom.xml` (root — add module)

- [ ] **Step 1: Create the parent POM module**

Create `outerstellar-platform-extension-parent/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.rygel</groupId>
        <artifactId>outerstellar-platform-parent</artifactId>
        <version>3.6.8</version>
    </parent>

    <artifactId>outerstellar-platform-extension-parent</artifactId>
    <packaging>pom</packaging>
    <name>Outerstellar Platform Extension Parent</name>
    <description>Maven parent POM for building outerstellar-platform extensions. Provides JTE plugin configuration and dependency management.</description>

    <properties>
        <jte.version>${jte.version}</jte.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.rygel</groupId>
                <artifactId>outerstellar-platform-extension-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>gg.jte</groupId>
                <artifactId>jte-runtime</artifactId>
                <version>${jte.version}</version>
            </dependency>
            <dependency>
                <groupId>gg.jte</groupId>
                <artifactId>jte-kotlin</artifactId>
                <version>${jte.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-maven-plugin</artifactId>
                    <version>${kotlin.version}</version>
                    <executions>
                        <execution>
                            <id>compile</id>
                            <phase>compile</phase>
                            <goals>
                                <goal>compile</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>test-compile</id>
                            <phase>test-compile</phase>
                            <goals>
                                <goal>test-compile</goal>
                            </goals>
                        </execution>
                    </executions>
                    <configuration>
                        <jvmTarget>${java.version}</jvmTarget>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>gg.jte</groupId>
                    <artifactId>jte-maven-plugin</artifactId>
                    <version>${jte.version}</version>
                    <dependencies>
                        <dependency>
                            <groupId>gg.jte</groupId>
                            <artifactId>jte-native-resources</artifactId>
                            <version>${jte.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>io.github.rygel</groupId>
                            <artifactId>outerstellar-platform-jte-extensions</artifactId>
                            <version>${project.version}</version>
                        </dependency>
                    </dependencies>
                    <executions>
                        <execution>
                            <id>jte-generate</id>
                            <phase>generate-sources</phase>
                            <goals>
                                <goal>generate</goal>
                            </goals>
                            <configuration>
                                <sourceDirectory>${project.basedir}/src/main/jte</sourceDirectory>
                                <contentType>Html</contentType>
                                <packageName>gg.jte.generated.precompiled.extension</packageName>
                                <targetResourceDirectory>${project.build.outputDirectory}</targetResourceDirectory>
                                <extensions>
                                    <extension>
                                        <className>gg.jte.nativeimage.NativeResourcesExtension</className>
                                    </extension>
                                    <extension>
                                        <className>io.github.rygel.outerstellar.jte.JteClassRegistryExtension</className>
                                    </extension>
                                </extensions>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Add module to root pom.xml**

Add `<module>outerstellar-platform-extension-parent</module>` to the `<modules>` section (after `platform-extension-api`).

- [ ] **Step 3: Compile the new module**

Run: `mvn -pl outerstellar-platform-extension-parent compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add outerstellar-platform-extension-parent/ pom.xml
git commit -m "feat: add outerstellar-platform-extension-parent POM for zero-config JTE"
```

---

### Task 8: Update Dockerfile.build module lists

The `docker/Dockerfile.build` has dependency-cache stages that copy `pom.xml` files. Adding `outerstellar-platform-extension-parent` to the reactor requires updating both partial-POM copy lists.

**Files:**
- Modify: `docker/Dockerfile.build`

- [ ] **Step 1: Add outerstellar-platform-extension-parent to Dockerfile.build**

Find the `deps` stage's partial POM copy list and add:

```
COPY outerstellar-platform-extension-parent/pom.xml outerstellar-platform-extension-parent/pom.xml
```

Find the `desktop-deps` stage's partial POM copy list and add the same line.

- [ ] **Step 2: Commit**

```bash
git add docker/Dockerfile.build
git commit -m "chore: add extension-parent to Dockerfile.build module lists"
```

---

### Task 9: Full build verification

- [ ] **Step 1: Run full reactor build**

Run: `mvn clean verify -T4 -pl outerstellar-i18n,platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`

Expected: BUILD SUCCESS

- [ ] **Step 2: Run platform-web tests specifically**

Run: `mvn -pl platform-web -am test -Dexec.skip=true`

Expected: All tests pass

- [ ] **Step 3: Commit any remaining fixes**

If any fixes were needed during verification, commit them with an appropriate message.
