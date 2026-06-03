# TODO

## Completed work: fix JTE renderer mode handling

The renderer in `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/JteInfra.kt` was corrected after
an in-progress change introduced a silent fallback from the development source-template renderer to the precompiled JTE
registry when a source template was missing. That fallback made local development look successful even when the source
template layout was wrong, and it could hide missing templates until production or tests exercised a different path.

The implemented fix is an explicit mode split:

- Development mode resolves and renders source templates from the project tree.
- Production mode renders only through the generated `JteClassRegistry`.
- Tests should use the same precompiled registry path as production unless a test explicitly constructs a source-template
  engine for a narrow unit case.
- There should be no per-template fallback from source rendering to the precompiled registry.

### Observed repository state

- Branch: `feat/extension-clean-slate`.
- Modified files found:
  - `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/JteInfra.kt`
  - `outerstellar-i18n-validator-maven-plugin/pom.xml`
- The Maven plugin POM change appears unrelated to this renderer task and should not be touched as part of the JTE fix
  unless later investigation proves it is required.
- Source templates live under:
  - `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/...`
- Generated/precompiled templates are present under:
  - `platform-web/target/generated-sources/jte/gg/jte/generated/precompiled/outerstellar/...`
  - `platform-web/target/classes/gg/jte/generated/precompiled/outerstellar/...`
- Existing test added for the fallback:
  - `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/infra/JteInfraTest.kt`
  - Test name: `dev renderer falls back to precompiled platform template when source is absent`
  - This test asserts the behavior we want to remove.

### Implementation completed

1. Reworked `createRenderer()` in `JteInfra.kt` so it has three clear phases:
   - Determine whether JTE production mode is enabled:
     - `System.getProperty("jte.production") == "true"`
     - or `System.getenv("JTE_PRODUCTION") == "true"`
   - Preload generated template classes when production is enabled or `runtime.jtePreloadEnabled` is true.
   - Return a renderer from exactly one path:
     - production: `renderUsingPrecompiledRegistry()`
     - development: a source-template renderer created from a `DirectoryCodeResolver`

2. Preserved the useful source-path discovery improvement, but made it accurate for this repository:
   - Continue honoring `jte.sourceDir`, then `JTE_SOURCE_DIR`, then `user.dir`.
   - Resolve the source template root from the selected project directory.
   - The current in-progress code checks `web/src/main/jte`, but this repository module is named `platform-web`.
   - The resolver should find `platform-web/src/main/jte` when run from the repository root.
   - If the code must support module-local execution too, check both:
     - `<base>/platform-web/src/main/jte`
     - `<base>/src/main/jte`
   - Pick one existing directory deterministically and log which one is used.

3. Removed the silent fallback helper:
   - Delete `renderUsingDevEngineWithPrecompiledFallback(...)`.
   - Remove the `gg.jte.CodeResolver` import if it becomes unused.
   - In development mode, if the source template directory is absent, fail loudly with a clear exception message instead
     of falling back to resources or precompiled classes.
   - The failure should explain the checked base directory and expected template path so misconfigured launches are easy
     to diagnose.

4. Kept `renderUsingPrecompiledRegistry()` as the only production/test renderer path:
   - It already fails with `ViewNotFound` when `JteClassRegistry` cannot resolve a template.
   - Do not reintroduce a `TemplateEngine` resource resolver for production.
   - Do not catch missing source templates and retry through the registry.

5. Updated tests:
   - Removed `JteInfraTest.dev renderer falls back to precompiled platform template when source is absent`.
   - Added coverage for repository-root source directory resolution.
   - Added coverage for module-root source directory resolution.
   - Added coverage for the loud missing-source-directory failure message.
   - Kept normal web rendering tests on the precompiled registry path via Maven's `jte.production=true` Surefire setting.

### Validation run

Compile after editing `JteInfra.kt`:

```powershell
$env:MAVEN_OPTS='-Dagent.owner=codex -Dagent.task=jte-renderer-compile'
mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Result: passed.

Focused renderer tests:

```powershell
$env:MAVEN_OPTS='-Dagent.owner=codex -Dagent.task=jte-renderer-test'
mvn -pl platform-web -am test -Dtest=JteInfraTest "-Dexec.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result: passed, 3 tests.

Representative web rendering test:

```powershell
$env:MAVEN_OPTS='-Dagent.owner=codex -Dagent.task=jte-web-render-test'
mvn -pl platform-web -am test -Dtest=PlatformPageRenderingTest "-Dexec.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Result: passed, 17 tests.

Before any commit, still run the normal repository gates:

```powershell
$env:MAVEN_OPTS='-Dagent.owner=codex -Dagent.task=full-nondesktop-test'
pwsh scripts/test.ps1

$env:AGENT_OWNER='codex'
$env:AGENT_TASK='desktop-container-test'
pwsh scripts/test-desktop.ps1
```

### Acceptance criteria met

- `createRenderer()` has no source-to-precompiled fallback in development mode.
- Production and test rendering use `JteClassRegistry`.
- Development mode renders from source templates only.
- A bad or missing development template source path fails clearly at startup/renderer creation, not later as a disguised
  successful precompiled render.
- The existing unrelated Maven plugin POM modification is preserved untouched.
- Focused compile and renderer tests pass before broader validation.

## Pending

### #453: Merge Flyway migration history into a single table

**Problem**: `PersistenceFactory.runMigrations()` runs two separate Flyway instances — `migrate()` for platform (V1–V13 → `flyway_schema_history`) and `migrateExtension()` for extensions (V100+ → `flyway_extension_history`). Switching between JVM and native-image builds on the same DB causes `42P07: relation already exists` because the two history tables disagree on what has been applied.

**Fix**: Merge both migration sets into a single Flyway instance with one history table. Add extension `location` as a second `locations` entry in the same `migrate()` call. Deprecate `ExtensionMigrations.historyTable`. Add one-time repair for existing dual-table deployments. Update `Migrator.kt` to handle extension migrations.

**Before fixing**: Audit the entire codebase for other instances of duplicated/redundant dual setups (dual config loading, dual service initialization, duplicated constants, etc.).

**Key files**:
- `platform-persistence-jdbi/.../infra/DatabaseInfra.kt` — `migrate()` + `migrateExtension()`
- `platform-persistence-jdbi/.../di/PersistenceFactory.kt` — `runMigrations()` orchestrator
- `platform-core/.../ExtensionMigrations.kt` — SPI data class
- `platform-persistence-jdbi/.../persistence/Migrator.kt` — standalone CLI (missing extension support)

### Duplication audit results (#453 prerequisite)

#### Medium severity — latent bugs

| # | Finding | Files |
|---|---------|-------|
| A | **Sync module wiring x3** — `DesktopComponents.kt`, `JavaFxApp.kt`, and `SyncClientComponents.kt` all wire the same HTTP clients, auth/sync/profile/admin/notification modules independently. `SyncClientComponents.kt` is the canonical factory but neither desktop module uses it. | `platform-desktop/.../di/DesktopComponents.kt:68-133`, `platform-desktop-javafx/.../JavaFxApp.kt:107-193`, `platform-sync-client/.../di/SyncClientComponents.kt:48-133` |
| B | **JavaFX persistence wiring duplicated manually** — `JavaFxApp.kt` creates its own datasource, runs Flyway, creates JDBI, wires repos instead of calling `createPersistenceComponents()`. Missing `InstantArgumentFactory`, `InstantColumnMapper`, `CachingUserRepository`. | `platform-desktop-javafx/.../JavaFxApp.kt:119-141`, `platform-persistence-jdbi/.../di/PersistenceFactory.kt:67-111` |
| C | **Table cleanup lists duplicated** — `WebTest` and `JdbiTest` have identical `tablesToDelete` lists that must stay in sync. New tables require updating both. | `platform-web/.../WebTest.kt:332-352`, `platform-persistence-jdbi/.../JdbiTest.kt:19-39` |
| D | **ExtensionTemplateRenderer override logic is dead code** — both branches call `delegate(viewModel)`, the override check does nothing. `extensionClassLoader` is always null from `WebFactory.kt:87`. Extension template overrides are non-functional. | `platform-web/.../ExtensionTemplateRenderer.kt:12-18` |
| E | **JDBI setup pattern x3, JavaFX missing mappers** — `PersistenceFactory.kt`, `TestDatabase.kt`, and `JavaFxApp.kt` all repeat the JDBI create+register pattern. JavaFX copy is missing `InstantColumnMapper` (will fail at runtime for Instant reads). | `platform-persistence-jdbi/.../PersistenceFactory.kt:79-91`, `platform-testkit/.../TestDatabase.kt:57-61`, `platform-desktop-javafx/.../JavaFxApp.kt:128-133` |

#### Low severity — maintenance burden

| # | Finding | Files |
|---|---------|-------|
| F | YAML config loading infrastructure duplicated between `AppConfig.kt` and `DesktopAppConfig.kt` | `platform-core/.../AppConfig.kt:100-116`, `platform-sync-client/.../DesktopAppConfig.kt:29-45` |
| G | CSP policy constant defined twice — `AppConfig.kt` and `Filters.kt` have identical `DEFAULT_CSP_POLICY` | `platform-core/.../AppConfig.kt:19-22`, `platform-web/.../Filters.kt:44-52` |
| H | `InstantArgumentFactory`/`InstantColumnMapper` duplicated in production and test | `platform-persistence-jdbi/.../JdbiSupport.kt:28-43`, `platform-testkit/.../TestDatabase.kt:20-35` |
| I | `DesktopAppConfig` re-declares fields from `AppConfig` with manual copying in `DesktopComponents.kt` | `platform-sync-client/.../DesktopAppConfig.kt`, `platform-desktop/.../DesktopComponents.kt:146-148` |
| J | `RuntimeConfig()` instantiated 15x in `buildRuntimeConfig()` for field defaults | `platform-core/.../AppConfig.kt:243-325` |
| K | Triple-layered user identity flow (RequestContext → SecurityRules.USER_KEY → route handler) — intentional but layered | `platform-web/.../Filters.kt:282-298`, `platform-web/.../Filters.kt:355-366`, `platform-security/.../SecurityRules.kt:14-22` |

## Recently completed

- #376: extension-host UI/root-route work
- #384: moved `outerstellar-i18n` into this repository
- #407: extension migration follow-ups
- #408: duplicate tracker for extension-host migration work

## Next workflow

When new work appears:

1. Start from latest `origin/develop`.
2. Create a fresh feature branch; do not reuse a merged branch.
3. Use Podman only, never the Docker CLI.
4. Run the relevant focused tests first.
5. Before commit, run:

```powershell
pwsh scripts/test.ps1
pwsh scripts/test-desktop.ps1
```
