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
