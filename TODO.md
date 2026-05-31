# TODO - Issue #407 Plugin Migration Follow-ups

Issue: https://github.com/rygel/outerstellar-platform/issues/407

Current follow-up PR: https://github.com/rygel/outerstellar-platform/pull/410

## Status

- PR #409 merged the plugin-hosted UI/root-route work and fixed the native-image Flyway reflection failure.
- PR #410 is open with `createServerComponents(config = ..., plugin = ...)`, a production-wiring integration test, and docs for config-driven hosted-app tests.
- Do not close #407 yet. It is still an umbrella customer migration-pain ticket unless the remaining items below are split into separate issues or explicitly accepted as complete.

## Before continuing

- Start from latest `origin/develop`, not from an already-merged feature branch.
- Preserve unrelated local edits unless the owner explicitly asks to change them.
- Use Podman only; do not use the Docker CLI.
- Run `pwsh scripts/test.ps1` and `pwsh scripts/test-desktop.ps1` before committing.

## Remaining work for #407

### 1. Decide whether PR #410 is enough for test infrastructure

- After PR #410 merges, verify whether customers can write hosted-app integration tests without copying `WebTest` internals.
- If not enough, design a real public test helper, likely one of:
  - `TestServerComponentsBuilder`
  - `createServerComponents(config, plugin, testOverrides)`
  - a published test-fixtures/test-support module
- Make sure the helper supports at least:
  - explicit `AppConfig`
  - hosted app/plugin instance
  - test database config
  - optional service/repository overrides for mocks
  - deterministic cleanup/close behavior
- Add customer-facing example tests.

### 2. Factory method compatibility/deprecation path

- Review old v1.6.x factory signatures called out in #407:
  - `createPersistenceComponents(config, pluginMigrationSource)`
  - `createSecurityComponents(...)` without required `emailService`
  - `createWebComponents(...)` with old `sessionService` shape
  - `createCoreComponents(...)` older parameter ordering/shape
- Decide which compatibility overloads are technically safe to restore.
- Add deprecated wrapper overloads only where behavior is unambiguous.
- Each deprecated overload should include `ReplaceWith(...)` guidance or clear KDoc pointing to `createServerComponents(...)`.
- Add compile tests or focused integration tests proving old-style calls still route to the current wiring where feasible.

### 3. Jackson/Flyway dependency story

- Native-image runtime reflection was fixed, but #407 also reports a downstream Jackson 2.x/3.x classpath conflict.
- Reproduce the customer scenario if possible with a minimal downstream app using http4k + Flyway 12.
- Decide on one path:
  - manage the required Jackson versions in the platform dependency management/BOM,
  - document exact downstream overrides,
  - or investigate shading/relocation if dependency management is insufficient.
- Add documentation to `MIGRATION.md` with the chosen workaround or supported dependency set.
- Add a dependency convergence or sample-app check if practical.

### 4. Documentation polish for migration guide

- Review `MIGRATION.md` against every bullet in #407 and ensure each has a concrete answer.
- Add a short "1.6.x to 3.6.x checklist" at the top.
- Include import paths for the primary SPI:
  - `io.github.rygel.outerstellar.platform.plugin.HostedApp`
  - `io.github.rygel.outerstellar.platform.PluginMigrations`
  - `io.github.rygel.outerstellar.platform.composition.PlatformMode`
- Add a before/after route registration example using `contribute(context)` and `context.routes`.
- Add a before/after migration example from deprecated `migrationLocation` to `migrations = PluginMigrations(...)`.

### 5. Confirm root-route ownership is fully documented

- Verify the docs explain that `PlatformMode.PluginHostedApp` grants `/` as the default UI ownership prefix.
- Confirm examples include a hosted app registering `/`.
- Add a regression test if a gap remains around `/`, `/dashboard`, and plugin-prefixed fallback routes.

### 6. Close or split issues after PR #410

- After PR #410 merges, reassess #407 item by item.
- Close #376 if it is still open and PR #409 really completed it.
- Close #408 if it is only tracking #376/#409 work and nothing remains.
- For #407, either:
  - keep it open until all items above are complete, or
  - split remaining work into smaller issues and close #407 with links to those issues.

## Validation already run for PR #410

- `mvn -pl platform-web -am test -Dtest=ServerComponentsIntegrationTest -Dexec.skip=true -Dsurefire.failIfNoSpecifiedTests=false`
- `pwsh scripts/test.ps1`
- `pwsh scripts/test-desktop.ps1`

## Notes

- PR #410 branch: `fix/issue-407-plugin-migration-followups`
- Commit: `ef82f227 feat: support explicit config server components`
