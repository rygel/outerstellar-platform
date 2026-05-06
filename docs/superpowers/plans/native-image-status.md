# Native Image Status — 2026-05-06

## What works

- **Native binary builds successfully** — `outerstellar-web.exe` ~211MB, builds in ~2min
- **Server starts** — connects to PostgreSQL, Flyway migrations run correctly
- **Health endpoint** — `/health` returns `{"status":"UP","database":{"status":"UP"}}`
- **API endpoints** — JSON responses work (health, 404s, etc.)
- **Netty server** — migrated from Jetty, works in both JVM and native-image modes
- **Flyway in native-image** — fixed by registering migrations as `JavaMigration` objects loaded via `ClassLoader.getResourceAsStream()`, bypassing Flyway's broken classpath scanner
- **OTel manual wiring** — replaced auto-configure with explicit `SdkTracerProvider`
- **All 538 JVM tests pass**

## What's broken

### JTE template rendering in native-image (CRITICAL)

All HTML page routes return 500 because JTE cannot find precompiled template classes.

**Symptom:** `TemplateNotFoundException: io/github/rygel/outerstellar/platform/web/AuthPage.kte not found`

**Root cause:** `Class.forName("gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteAuthPageGenerated")` returns `ClassNotFoundException` in native-image, even though:

1. The class IS in the fat jar (95 JTE classes confirmed)
2. The class IS in the reachability-metadata.json reflection config with `allDeclaredFields: true`
3. A direct compile-time reference (`JteAuthPageGenerated::class.java`) was added — still excluded
4. `--initialize-at-build-time=gg.jte.generated.precompiled.outerstellar` was tried — still excluded

**Diagnosis so far:**
- `RuntimeTemplateLoader.load()` constructs the class name from template name + package prefix
- It calls `ClassLoader.loadClass()` which fails with `ClassNotFoundException`
- The classes exist in the jar but GraalVM's reachability analysis excludes them as "unreachable dead code"
- Neither reflection config entries nor direct code references force inclusion
- JTE's `jte-native-resources` extension (generates proper GraalVM metadata) exists but is only in 3.2.5-SNAPSHOT, not released

**Possible fixes to try next:**
1. **`-H:IncludeResources` with class listing** — explicitly tell native-image to include all JTE classes
2. **Feature class** — implement a GraalVM `Feature` that registers all JTE classes at build time via `BeforeAnalysisAccess.registerForReflectiveAccess()`
3. **Custom `RuntimeTemplateLoader`** — bypass `ClassLoader.loadClass()` by maintaining a hard-coded class map
4. **Wait for JTE 3.2.5** — use `jte-native-resources` extension when released
5. **Use `native-image-agent` with thorough exercise** — re-run the tracing agent while actually rendering all templates

### Other open issues

- **Detekt formatting violations** — 20 pre-existing issues in `platform-core` (import ordering, indentation) from AOT migration edits. Not from this branch's logging work.
- **`logback.xml` not loading in native-image** — no structured log output, only raw stderr
- **Dockerfile.native not tested** — Linux multi-stage build not verified yet
- **Binary size** — 211MB, should be compressible with UPX to ~50-80MB

## Changes in this commit

### Flyway native-image fix (`DatabaseInfra.kt`)
- Loads migration SQL files via `ClassLoader.getResourceAsStream()` and registers them as `JavaMigration` objects
- Bypasses Flyway's `ClassPathScanner` which can't enumerate classpath in native-image

### Swallowed exceptions audit
- Enabled `SwallowedException` rule in `config/detekt.yml`
- Fixed 6 CRITICAL silently-swallowed exceptions (added logging):
  - `SecurityService.kt` — SSRF URI parsing
  - `Filters.kt` — analytics page-view
  - `PlatformPlugin.kt` — lens extraction
  - `NotificationRoutes.kt` — invalid UUID
  - `PasswordEncoder.kt` — malformed BCrypt
  - `ThemeCatalog.kt` — color parsing
- Upgraded 11 log levels from DEBUG/TRACE to WARN
- Upgraded 3 TRACE to DEBUG

### JTE diagnostic logging (`JteInfra.kt`)
- Added `ensureTemplateClassesLoaded()` probe that counts how many JTE classes `Class.forName()` can find
- Added detailed diagnostic output on template render failure (class name, ClassLoader type, loadClass result)
- Added `findPrecompiledDir()` to locate compiled templates on disk (for non-native-image production)

### Reachability metadata
- Added 84 JTE generated template classes to reflection config with `allDeclaredFields: true` and `allDeclaredMethods: true`
- Added `themes.json`, `themes/default.json`, `themes/dark.json`, `messages.properties`, `messages_fr.properties` to resource includes

### JTE precompile goal
- Added `jte-precompile` execution to `platform-web/pom.xml` (runs at `process-classes` phase)
- Does not yet produce `.bin` metadata files — needs `jte-native-resources` extension (unreleased)
