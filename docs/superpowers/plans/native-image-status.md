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

### JTE template rendering in native-image

JTE 3.2.4 does not provide the native-image resource extension needed for the normal precompiled renderer path. The project therefore uses a registry-based production renderer as a workaround.

**Original symptom:** `TemplateNotFoundException: io/github/rygel/outerstellar/platform/web/AuthPage.kte not found`

**Original root cause:** JTE 3.2.4's `TemplateEngine.createPrecompiled(...)` path delegates to `RuntimeTemplateLoader.load()`, which constructs a generated template class name from the template path and package prefix, then calls `ClassLoader.loadClass(...)`.

That is reliable on a normal JVM, where generated classes live in jars or directories and the classloader can discover them dynamically. It is not reliable in a GraalVM native image:

1. The class IS in the fat jar (95 JTE classes confirmed)
2. The class IS in the reachability-metadata.json reflection config with `allDeclaredFields: true`
3. The reflection metadata allows reflective member access, but it does not make arbitrary string-based `ClassLoader.loadClass(...)` work in a closed-world native image
4. `findPrecompiledDir()` is also a poor native-image dependency: a non-null path makes JTE create a `URLClassLoader` over a filesystem directory, while `null` falls back to the context classloader. Neither maps reliably to classes embedded inside the native executable.

**Implemented workaround:**
- `JteClassRegistry` keeps direct compile-time references to all 33 generated JTE template classes.
- Production rendering no longer calls `TemplateEngine.createPrecompiled(...)`.
- `JteInfra.kt` resolves the generated class from `JteClassRegistry` and renders via JTE runtime `Template(templateName, templateClass)`.
- This bypasses string-based classloader resolution and avoids filesystem probing for a precompiled template directory.
- The workaround should be removable when a released JTE version includes native-image support equivalent to the unreleased `NativeResourcesExtension` from 3.2.5-SNAPSHOT.

**Validation so far:**
- `mvn -pl platform-web -DskipTests compile` passes.
- `mvn -pl platform-web -Dtest=TestRender test` passes with `jte.production=true`, exercising the production renderer path.
- The runtime diagnostic reports `JTE: preloaded 33 template classes, 0 not found`.
- Full native-image validation is still blocked if Maven cannot resolve `org.graalvm.buildtools:native-image-maven-plugin:0.10.5` from the configured repositories or local cache.

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
- Removed native production dependence on `findPrecompiledDir()` and `TemplateEngine.createPrecompiled(...)`
- Added registry-based production rendering for precompiled JTE templates

### Reachability metadata
- Added 84 JTE generated template classes to reflection config with `allDeclaredFields: true` and `allDeclaredMethods: true`
- Added `themes.json`, `themes/default.json`, `themes/dark.json`, `messages.properties`, `messages_fr.properties` to resource includes

### JTE precompile goal
- Added `jte-precompile` execution to `platform-web/pom.xml` (runs at `process-classes` phase)
- Kept on the released `gg.jte:jte-maven-plugin` 3.2.4 API; do not configure `gg.jte.nativeimage.NativeResourcesExtension` until it exists in a released JTE dependency
