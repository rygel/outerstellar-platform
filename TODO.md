# TODO - Issue #407 Plugin Migration Follow-ups

Issue: https://github.com/rygel/outerstellar-platform/issues/407

Merged PRs:

- https://github.com/rygel/outerstellar-platform/pull/409
- https://github.com/rygel/outerstellar-platform/pull/410

Current recovery branch: `fix/issue-407-missing-followups`

## Status

- PR #409 merged the plugin-hosted UI/root-route work and fixed the native-image Flyway reflection failure.
- PR #410 merged `createServerComponents(config = ..., plugin = ...)`, a production-wiring integration test, and docs for config-driven hosted-app tests.
- The follow-up commit `3b7d327f` was left only on the deleted PR branch. This recovery branch reapplies that missing work from latest `origin/develop`.
- Items 1-5 below are now complete on this recovery branch. Only item 6 remains after this branch is merged.

## Before continuing

- Start from latest `origin/develop`, not from an already-merged feature branch.
- Preserve unrelated local edits unless the owner explicitly asks to change them.
- Use Podman only; do not use the Docker CLI.
- Run `pwsh scripts/test.ps1` and `pwsh scripts/test-desktop.ps1` before committing.
- Include `platform-plugin-api` in reactor `-pl` lists because `platform-web` depends on it.

## Completed work

### 1. Test infrastructure decision - DONE

- `createServerComponents(config, plugin)` is sufficient for downstream hosted-app integration tests.
- Customers can pass their own `AppConfig` with a test database and get back a `ServerComponents` with `.app.http` and `.persistence.close()`.
- `WebTest` is an internal test harness. Downstream apps should use their own test database setup plus `createServerComponents(config, plugin)`.
- Documented in `MIGRATION.md` under "Full-stack web tests".
- No separate test-fixtures/test-support module is needed at this time.

### 2. Factory method compatibility/deprecation - DONE

Added two safe deprecated overloads:

- `createPersistenceComponents(config, pluginMigrationSource: String?)` in `PersistenceFactory.kt`
  - Wraps the string location into `PluginMigrations(location = source)` and delegates to the current overload.
  - `@ReplaceWith` points to `createPersistenceComponents(config, PluginMigrations?)`.

- `createSecurityComponents(config, userRepository, ..., oauthRepository?, sessionRepository?)` in `SecurityComponents.kt`
  - Calls without `emailService` default to `NoOpEmailService()`.
  - `@ReplaceWith` points to the overload with explicit `emailService`.

Not restored because the old API shape is ambiguous:

- `createWebComponents(...)` with old `sessionService` shape.
- `createCoreComponents(...)` with old parameter ordering.

Downstream customers should migrate those calls to the current parameter list or use `createServerComponents(plugin = ...)`.

### 3. Jackson/Flyway dependency story - DONE

- Platform manages Jackson 2.21.4 via `jackson-bom` in root POM dependency management.
- Flyway 11.20.3 brings Jackson 2.x transitively, so there is no conflict within the platform.
- Dependency convergence and `banDuplicateClasses` are enforced by `maven-enforcer-plugin`.
- Conflicts only appear when downstream consumers introduce Jackson 3.x alongside Flyway/Jackson 2.x.
- Documented in `MIGRATION.md` under "Jackson and Flyway dependency compatibility".

### 4. Migration guide documentation polish - DONE

`MIGRATION.md` now includes:

- Quick checklist for "1.6.x to 3.6.x".
- Primary import paths.
- Before/after examples for default route selection, plugin migrations, and route registration.
- Deprecated factory overloads section.
- Jackson/Flyway dependency compatibility section.

### 5. Root-route ownership - DONE

- `MIGRATION.md` explains that `HostedAppContribution.from(...)` grants `/` as a UI ownership prefix in `PluginHostedApp` mode.
- API, admin, and asset ownership remain under explicit prefixes.
- Regression coverage in `ServerComponentsIntegrationTest` verifies:
  - `PluginHostedApp` can own `/`, `/dashboard`, and `/about`.
  - `FullPlatformApp` still rejects root route ownership.

## Remaining work for #407

### 6. Close or split issues after this recovery branch merges

- Reassess #407 item by item.
- Close #376 if it is still open and PR #409 really completed it.
- Close #408 if it only tracked #376/#409 work and nothing remains.
- For #407, either:
  - close it if all reported migration pain points are accepted as addressed, or
  - split any remaining work into smaller issues and close #407 with links.

## Validation

Focused validation on this recovery branch:

```powershell
mvn -pl platform-web -am test -Dtest=ServerComponentsIntegrationTest "-Dexec.skip=true" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected before merge:

```powershell
pwsh scripts/test.ps1
pwsh scripts/test-desktop.ps1
```
