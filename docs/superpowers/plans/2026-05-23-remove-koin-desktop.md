# Remove Koin from Desktop Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Koin from all desktop modules (platform-desktop, platform-desktop-javafx, platform-sync-client), then delete the deprecated Koin modules from platform-core, platform-security, and platform-persistence-jdbi, and remove the Koin dependency entirely.

**Architecture:** Same factory pattern as the server Koin removal (PR #351). Each module gets a typed component holder + factory function. Desktop entry points (`SwingSyncApp.kt`, `JavaFxApp.kt`) construct their object graphs explicitly. JavaFX controllers receive dependencies via constructor parameters instead of `by inject()`.

**Tech Stack:** Kotlin, Swing (FlatLaf), JavaFX, JFoenix, MockK, Testcontainers

---

## Scope

### In scope
- `platform-sync-client` — replace `apiClientModule` with `SyncClientComponents` factory
- `platform-desktop` — replace `desktopModule` + `DesktopComponent` with `DesktopComponents` factory
- `platform-desktop-javafx` — replace `fxModule` + all `KoinComponent` controllers with explicit wiring
- All desktop test files
- Delete deprecated Koin modules from platform-core, platform-security, platform-persistence-jdbi
- Remove Koin from all POM files
- Remove Koin from root pom.xml version property

### Out of scope
- Server runtime (already Koin-free via PR #351)

## Key Reference: SwingAppE2ETest

`SwingAppE2ETest.kt` already demonstrates manual construction: it creates `SyncViewModel` with MockK mocks, no Koin at all. The production code should follow this pattern.

## File Structure

### New files
- `platform-sync-client/src/main/kotlin/.../di/SyncClientComponents.kt` — replaces ApiClientModule.kt
- `platform-desktop/src/main/kotlin/.../di/DesktopComponents.kt` — replaces DesktopModule.kt

### Deleted files
- `platform-sync-client/src/main/kotlin/.../di/ApiClientModule.kt`
- `platform-desktop/src/main/kotlin/.../di/DesktopModule.kt`
- `platform-desktop-javafx/src/main/kotlin/.../fx/di/FxModule.kt`
- `platform-core/src/main/kotlin/.../di/CoreModule.kt`
- `platform-security/src/main/kotlin/.../security/SecurityModule.kt`
- `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`
- `platform-desktop/src/test/kotlin/.../di/KoinModuleTest.kt`
- `platform-sync-client/src/test/kotlin/.../di/KoinModuleTest.kt`

### Modified files
- `platform-desktop/src/main/kotlin/.../swing/SwingSyncApp.kt` — replace Koin with explicit construction
- `platform-desktop-javafx/src/main/kotlin/.../fx/app/JavaFxApp.kt` — replace Koin, pass deps to controllers
- All 12 JavaFX controllers — remove KoinComponent, accept deps via constructor or from parent
- 5 desktop test files — replace Koin with manual construction
- 4 POM files — remove Koin dependencies
- Root `pom.xml` — remove Koin version property

---

### Task 1: Create SyncClientComponents factory

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/di/SyncClientComponents.kt`
- Reference: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/di/ApiClientModule.kt`

- [ ] **Step 1: Read the reference files**

Read:
- `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/di/ApiClientModule.kt`
- All 5 HTTP client files in `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/`
- All module interface files in `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/`

- [ ] **Step 2: Write SyncClientComponents.kt**

The factory must produce all the same objects as `apiClientModule`:
- 5 HTTP clients: `HttpAuthClient`, `HttpSyncClient`, `HttpProfileClient`, `HttpAdminClient`, `HttpNotificationClient`
- `ApiSession` (stateful, holds auth tokens)
- 5 module implementations: `AuthModuleImpl`, `SyncDataModuleImpl`, `ProfileModuleImpl`, `AdminModuleImpl`, `NotificationModuleImpl`
- `AnalyticsService` and `I18nService` (passed through from caller)

The factory takes `serverBaseUrl: String` and optional services, returns a `SyncClientComponents` holder.

```kotlin
package io.github.rygel.outerstellar.platform.di

class SyncClientComponents(
    val httpHandler: org.http4k.core.HttpHandler,
    val apiSession: ApiSession,
    val authClient: AuthClient,
    val syncClient: SyncClient,
    val profileClient: ProfileClient,
    val adminClient: AdminClient,
    val notificationClient: NotificationClient,
    val authModule: AuthModule,
    val syncDataModule: SyncDataModule,
    val profileModule: ProfileModule,
    val adminModule: AdminModule,
    val notificationModule: NotificationModule,
)

fun createSyncClientComponents(
    serverBaseUrl: String,
    messageRepository: MessageRepository,
    outboxRepository: OutboxRepository,
    messageCache: MessageCache,
    analyticsService: AnalyticsService,
    i18nService: I18nService,
    connectivityChecker: ConnectivityChecker? = null,
    moduleNotifier: ModuleNotifier? = null,
    contactService: ContactService? = null,
): SyncClientComponents {
    // Construct all clients and modules explicitly, matching the logic in ApiClientModule.kt
}
```

IMPORTANT: Read the actual `ApiClientModule.kt` to understand how the module implementations are constructed. They use lazy lambdas for circular dependency avoidance — replicate this with direct references since we have all objects in scope.

- [ ] **Step 3: Compile platform-sync-client**

```
mvn -pl platform-sync-client -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 4: Commit**

```
git add platform-sync-client/src/main/kotlin/.../di/SyncClientComponents.kt
git commit -m "feat(sync-client): add explicit SyncClientComponents factory alongside Koin module"
```

---

### Task 2: Create DesktopComponents factory (Swing)

**Files:**
- Create: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopComponents.kt`
- Reference: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt`

- [ ] **Step 1: Read the reference files**

Read:
- `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt`
- `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingSyncApp.kt` (DesktopComponent + swingRuntimeModules)
- `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/viewmodel/SyncViewModel.kt` (constructor)
- `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SyncWindowMenu.kt` and `SyncWindowNav.kt` (SystemTrayNotifier usage)

- [ ] **Step 2: Write DesktopComponents.kt**

The factory composes `PersistenceComponents` + `CoreComponents` + `SyncClientComponents` into a Swing-ready object graph:

```kotlin
package io.github.rygel.outerstellar.platform.di

class DesktopComponents(
    val appConfig: SwingAppConfig,
    val config: AppConfig,
    val persistence: PersistenceComponents,
    val core: CoreComponents,
    val syncClient: SyncClientComponents,
    val syncViewModel: SyncViewModel,
    val systemTrayNotifier: SystemTrayNotifier,
    val i18nService: I18nService,
    val analyticsService: AnalyticsService,
)

fun createDesktopComponents(): DesktopComponents {
    val appConfig = DesktopAppConfig.fromEnvironment()
    val config = AppConfig(jdbcUrl = appConfig.jdbcUrl, jdbcUser = appConfig.jdbcUser, jdbcPassword = appConfig.jdbcPassword)
    val persistence = createPersistenceComponents(config)
    val core = createCoreComponents(config, persistence.messageRepository, persistence.contactRepository, persistence.outboxRepository, NoOpMessageCache)
    val syncClient = createSyncClientComponents(
        serverBaseUrl = appConfig.serverBaseUrl,
        messageRepository = persistence.messageRepository,
        outboxRepository = persistence.outboxRepository,
        messageCache = NoOpMessageCache,
        analyticsService = core.analyticsService,  // check if this exists
        i18nService = I18nService.create("messages"),
    )
    val syncViewModel = SyncViewModel(
        syncClient.authModule,
        syncClient.syncDataModule,
        syncClient.profileModule,
        syncClient.adminModule,
        syncClient.notificationModule,
        syncClient.i18nService,  // or create separately
    )
    val systemTrayNotifier = SystemTrayNotifier(syncViewModel)
    val i18nService = I18nService.create("messages")
    val analyticsService = ...
    return DesktopComponents(appConfig, config, persistence, core, syncClient, syncViewModel, systemTrayNotifier, i18nService, analyticsService)
}
```

Check the actual `AnalyticsService` construction in `DesktopModule.kt` — it may use a `PersistentBatchingAnalyticsService` with file storage.

- [ ] **Step 3: Compile platform-desktop**

```
mvn -pl platform-desktop -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 4: Commit**

```
git add platform-desktop/src/main/kotlin/.../di/DesktopComponents.kt
git commit -m "feat(desktop): add explicit DesktopComponents factory alongside Koin module"
```

---

### Task 3: Rewrite SwingSyncApp.kt

**Files:**
- Modify: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingSyncApp.kt`

- [ ] **Step 1: Read SwingSyncApp.kt fully**

Read the entire file. Understand:
- `DesktopComponent` object and its 6 eager `get()` calls
- `swingRuntimeModules()` function
- `main()` function and `startKoin` call
- `initializeUi()` — how SyncViewModel is constructed manually
- All other methods that reference `DesktopComponent`

- [ ] **Step 2: Rewrite SwingSyncApp.kt**

Replace:
- `startKoin { modules(swingRuntimeModules()) }` → `val components = createDesktopComponents()`
- `DesktopComponent : KoinComponent` → use `components.xxx` directly
- `DesktopComponent.authModule` → `components.syncClient.authModule`
- `DesktopComponent.config` → `components.appConfig`
- Delete `swingRuntimeModules()` function
- Delete `DesktopComponent` object
- Remove ALL Koin imports

The `SyncViewModel` is already constructed manually in `initializeUi()` — just change the source from `DesktopComponent.xxx` to `components.syncClient.xxx`.

- [ ] **Step 3: Compile platform-desktop**

```
mvn -pl platform-desktop -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 4: Commit**

```
git add platform-desktop/src/main/kotlin/.../swing/SwingSyncApp.kt
git commit -m "feat(desktop): replace Koin with explicit DesktopComponents in SwingSyncApp"
```

---

### Task 4: Rewrite JavaFxApp.kt and JavaFX controllers

**Files:**
- Modify: `platform-desktop-javafx/src/main/kotlin/.../fx/app/JavaFxApp.kt`
- Modify: All 12 controllers in `platform-desktop-javafx/src/main/kotlin/.../fx/controller/`
- Delete: `platform-desktop-javafx/src/main/kotlin/.../fx/di/FxModule.kt`

This is the largest task. JavaFX controllers are loaded by FXML, which requires a no-arg constructor. The controllers currently use `KoinComponent` + `by inject()` to get dependencies. Without Koin, we need a different approach.

**Strategy:** Use a companion object `ApplicationContext` that holds the `FxSyncViewModel`, `FxThemeManager`, `I18nService`, and `FxAppConfig` singletons. JavaFxApp sets these before showing the stage. Controllers read from the companion object instead of `by inject()`.

- [ ] **Step 1: Read all JavaFX files**

Read:
- `JavaFxApp.kt`
- `FxModule.kt`
- All 12 controller files in `fx/controller/`
- `FxSyncViewModel.kt` constructor

- [ ] **Step 2: Create an ApplicationContext in JavaFxApp.kt**

Add a companion object to `JavaFxApp` (or a top-level object) that holds the shared singletons:

```kotlin
object FxAppContext {
    lateinit var viewModel: FxSyncViewModel
    lateinit var themeManager: FxThemeManager
    lateinit var i18nService: I18nService
    lateinit var appConfig: FxAppConfig
}
```

- [ ] **Step 3: Rewrite JavaFxApp.kt**

Replace:
- `class JavaFxApp : Application(), KoinComponent` → `class JavaFxApp : Application()`
- `startKoin { modules(fxRuntimeModules()) }` → explicit construction using `createDesktopComponents()` or a JavaFX-specific factory
- `get<FxSyncViewModel>()` → read from `FxAppContext.viewModel`
- Delete `fxRuntimeModules()` function
- Remove ALL Koin imports

The `init()` method should:
1. Create `FxAppConfig.fromEnvironment()`
2. Create `AppConfig` from FxAppConfig
3. Create persistence, core, sync client components
4. Create `FxSyncViewModel`
5. Set `FxAppContext` fields
6. Create `FxThemeManager`

- [ ] **Step 4: Rewrite all 12 controllers**

For each controller that implements `KoinComponent`:
- Remove `KoinComponent` interface
- Remove `by inject()` calls
- Replace with `FxAppContext.viewModel`, `FxAppContext.themeManager`, etc.
- Remove Koin imports

Example for `MessagesController.kt`:
```kotlin
// BEFORE
class MessagesController : KoinComponent {
    val viewModel: FxSyncViewModel by inject()
}

// AFTER
class MessagesController {
    val viewModel: FxSyncViewModel get() = FxAppContext.viewModel
}
```

For `MainController.kt` (most complex — injects AuthModule, FxThemeManager, FxSyncViewModel, FxAppConfig):
- Replace all `by inject()` with reads from `FxAppContext`

- [ ] **Step 5: Delete FxModule.kt**

```
rm platform-desktop-javafx/src/main/kotlin/.../fx/di/FxModule.kt
```

- [ ] **Step 6: Compile platform-desktop-javafx**

```
mvn -pl platform-desktop-javafx -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 7: Commit**

```
git add -A
git commit -m "feat(javafx): replace Koin with explicit FxAppContext in JavaFxApp and all controllers"
```

---

### Task 5: Rewrite desktop tests

**Files:**
- Modify: `platform-desktop/src/test/kotlin/.../swing/SwingStartupE2ETest.kt`
- Delete: `platform-desktop/src/test/kotlin/.../di/KoinModuleTest.kt`
- Modify: `platform-desktop-javafx/src/test/kotlin/.../fx/e2e/FxAppE2ETest.kt`
- Modify: `platform-desktop-javafx/src/test/kotlin/.../fx/e2e/ThemeSwitchE2ETest.kt`
- Delete: `platform-sync-client/src/test/kotlin/.../di/KoinModuleTest.kt`

- [ ] **Step 1: Rewrite SwingStartupE2ETest.kt**

Replace `stopKoin()/startKoin{}` with:
1. Create `SwingAppConfig` with test container JDBC URL
2. Create `AppConfig` from it
3. Create persistence components
4. Create core components
5. Assert services are non-null

Remove `KoinTest` interface and all Koin imports.

- [ ] **Step 2: Delete desktop KoinModuleTest**

```
rm platform-desktop/src/test/kotlin/.../di/KoinModuleTest.kt
rm platform-sync-client/src/test/kotlin/.../di/KoinModuleTest.kt
```

- [ ] **Step 3: Rewrite FxAppE2ETest.kt**

Replace `GlobalContext.startKoin { modules(testModule) }` with manual construction:
1. Set `FxAppContext` fields directly with MockK mocks
2. Create `FxSyncViewModel` with mocks
3. Test the app

- [ ] **Step 4: Rewrite ThemeSwitchE2ETest.kt**

Same pattern — replace Koin with direct `FxAppContext` setup.

- [ ] **Step 5: Compile all desktop test code**

```
mvn -pl platform-desktop,platform-desktop-javafx -am test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 6: Commit**

```
git add -A
git commit -m "test(desktop): replace Koin in desktop tests with explicit construction, delete KoinModuleTests"
```

---

### Task 6: Delete deprecated Koin modules and remove Koin from all POMs

**Files:**
- Delete: `platform-core/src/main/kotlin/.../di/CoreModule.kt`
- Delete: `platform-security/src/main/kotlin/.../security/SecurityModule.kt`
- Delete: `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../di/ApiClientModule.kt`
- Delete: `platform-desktop/src/main/kotlin/.../di/DesktopModule.kt`
- Modify: All POM files — remove `koin-core-jvm` and `koin-test-junit5` dependencies
- Modify: Root `pom.xml` — remove `<koin.version>` property

- [ ] **Step 1: Delete all deprecated Koin module files**

```bash
rm platform-core/src/main/kotlin/.../di/CoreModule.kt
rm platform-security/src/main/kotlin/.../security/SecurityModule.kt
rm platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceModule.kt
rm platform-sync-client/src/main/kotlin/.../di/ApiClientModule.kt
rm platform-desktop/src/main/kotlin/.../di/DesktopModule.kt
```

- [ ] **Step 2: Remove Koin from all POM files**

For each module POM that has `koin-core-jvm` or `koin-test-junit5`:
- `platform-core/pom.xml` — remove `koin-core-jvm`
- `platform-security/pom.xml` — remove `koin-core-jvm` and `koin-test-junit5`
- `platform-persistence-jdbi/pom.xml` — remove `koin-core-jvm` and `koin-test-junit5`
- `platform-sync-client/pom.xml` — remove `koin-core-jvm` and `koin-test-junit5`
- `platform-desktop/pom.xml` — remove `koin-core-jvm` and `koin-test-junit5`
- `platform-desktop-javafx/pom.xml` — remove `koin-core-jvm` and `koin-test-junit5`

- [ ] **Step 3: Remove Koin version from root pom.xml**

Remove:
```xml
<koin.version>4.2.1</koin.version>
```

And remove the Koin dependency management entries if any.

- [ ] **Step 4: Remove Koin SpotBugs exclusions**

In `config/spotbugs-exclude.xml`, remove the `SeedComponent` exclusion (already removed) and any other Koin-specific exclusions that are no longer needed.

- [ ] **Step 5: Compile full reactor**

```
mvn clean compile -T4 "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

- [ ] **Step 6: Commit**

```
git add -A
git commit -m "refactor: delete all Koin modules, remove Koin dependency from entire project"
```

---

### Task 7: Full test suite verification

- [ ] **Step 1: Run non-desktop reactor verify**

```
mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run desktop tests in Podman container**

```
pwsh scripts/test-desktop.ps1

# Bash equivalent:
bash docker/run-desktop-tests.sh

# Manual equivalent:
podman build --target desktop-test -t outerstellar-test-desktop -f docker/Dockerfile.build .
podman run --rm --network host -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml:ro" -e "DOCKER_HOST=unix:///var/run/docker.sock" -v "/var/run/docker.sock:/var/run/docker.sock" outerstellar-test-desktop
```

Expected: All tests pass

- [ ] **Step 3: Run sync-client tests**

```
mvn -pl platform-sync-client -am test
```

Expected: All tests pass

- [ ] **Step 4: Fix any failures**

If any test fails, read the error, identify root cause, fix, recompile, re-run.

- [ ] **Step 5: Commit any fixes**

```
git add -A
git commit -m "fix: resolve test failures after complete Koin removal"
```

---

## Self-Review Checklist

### Spec coverage
- [x] Sync client no longer uses Koin — Task 1 + Task 6
- [x] Swing desktop no longer uses Koin — Task 3 + Task 6
- [x] JavaFX desktop no longer uses Koin — Task 4 + Task 6
- [x] All deprecated Koin modules deleted — Task 6
- [x] Koin dependency removed from all POMs — Task 6
- [x] All tests pass — Task 7

### Placeholder scan
- Some factory function bodies show `...` — these need to be filled with actual code from the reference files during execution

### Type consistency
- `SyncClientComponents` fields match the types created in `createSyncClientComponents()`
- `DesktopComponents` fields match the types created in `createDesktopComponents()`
- `FxAppContext` fields match what JavaFX controllers expect
- Controller field types (`FxSyncViewModel`, `FxThemeManager`, etc.) unchanged
