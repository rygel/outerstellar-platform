# Remove WebContext Facade + SecurityService Delegation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the WebContext facade (routes/factories use RequestContext + ShellRenderer directly) and remove SecurityService session delegation (callers use SessionService directly).

**Architecture:** WebContext is currently a thin facade over RequestContext (request state) + ShellRenderer (HTML shell). We inject both into the request via separate lenses, update the `Request.webContext` extension to return RequestContext, and add a `Request.shellRenderer` extension. For SecurityService, we replace `securityService.createSession/deleteSession/lookupSession` with direct `sessionService` calls, then remove the delegation methods.

**Tech Stack:** Kotlin, http4k, JDBI

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Delete | `WebContext.kt` | Remove facade entirely |
| Delete | `RoutingDsl.kt` | Replace with two extensions |
| Modify | `RequestContext.kt` | Standalone request state (already done) |
| Modify | `ShellRenderer.kt` | Remove internal WebContext creation, accept RequestContext directly |
| Create | `RoutingDsl.kt` (rewritten) | `Request.requestContext` + `Request.shellRenderer` extensions |
| Modify | `Filters.kt` | Inject RequestContext + ShellRenderer separately |
| Modify | `SidebarFactory.kt` | Accept RequestContext instead of WebContext |
| Modify | `AuthPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `HomePageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `ContactsPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `SettingsPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `AdminPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `ErrorPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `SearchPageFactory.kt` | Accept RequestContext + ShellRenderer |
| Modify | `InfraPageFactory.kt` | Accept RequestContext + ShellRenderer (if exists) |
| Modify | `WebPageFactory.kt` | Update all factory delegation signatures |
| Modify | All route files (~15) | Use `request.requestContext` / `request.shellRenderer` |
| Modify | `App.kt` | Remove WebContext references, pass sessionService to route constructors |
| Modify | `SyncWebSocket.kt` | Use sessionService instead of securityService |
| Modify | `SecurityService.kt` | Remove createSession/lookupSession/deleteSession delegation |
| Modify | `AuthRoutes.kt` | Accept sessionService, use it for createSession |
| Modify | `AuthApi.kt` | Accept sessionService, use it for createSession/deleteSession |
| Modify | `OAuthRoutes.kt` | Accept sessionService, use it for createSession |
| Modify | `Filters.kt` | Accept sessionService, use it for createSession |
| Modify | `ViewModels.kt` | No change needed (ShellView stays the same) |
| Modify | `ShellConfig.kt` | No change needed |
| Modify | tests | Update to use new APIs |

---

### Task 1: Remove SecurityService session delegation methods

This is the simpler, more isolated change. SecurityService currently delegates `createSession`, `lookupSession`, `deleteSession` to SessionService. We switch all callers to use SessionService directly and remove the delegation.

**Files:**
- Modify: `platform-security/src/main/kotlin/.../security/SecurityService.kt:247-294`
- Modify: `platform-web/src/main/kotlin/.../web/Filters.kt:261`
- Modify: `platform-web/src/main/kotlin/.../web/AuthRoutes.kt:92`
- Modify: `platform-web/src/main/kotlin/.../web/AuthApi.kt:227,259,181`
- Modify: `platform-web/src/main/kotlin/.../web/OAuthRoutes.kt:129`
- Modify: `platform-web/src/main/kotlin/.../web/App.kt:384`
- Modify: `platform-web/src/main/kotlin/.../web/SyncWebSocket.kt:31`

- [ ] **Step 1: Remove delegation methods from SecurityService**

In `SecurityService.kt`, delete the three delegation methods (`createSession`, `lookupSession`, `deleteSession`) at lines 247-294. Also remove `sessionService` constructor parameter and `sessionRepository` constructor parameter since they're no longer needed.

The constructor becomes:
```kotlin
class SecurityService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditRepository: AuditRepository? = null,
    private val resetRepository: PasswordResetRepository? = null,
    private val apiKeyRepository: ApiKeyRepository? = null,
    private val emailService: io.github.rygel.outerstellar.platform.service.EmailService? = null,
    private val oauthRepository: OAuthRepository? = null,
    private val config: SecurityConfig = SecurityConfig(),
    private val activityUpdater: AsyncActivityUpdater? = null,
)
```

Also remove the `import` for `SessionRepository` if no longer needed.

- [ ] **Step 2: Update SecurityComponents to not pass sessionService/sessionRepository to SecurityService**

In `SecurityComponents.kt`, update the `SecurityService` construction (line 54-67) to remove `sessionRepository` and `sessionService` parameters:

```kotlin
val securityService =
    SecurityService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        auditRepository = auditRepository,
        resetRepository = resetRepository,
        apiKeyRepository = apiKeyRepository,
        emailService = emailService,
        oauthRepository = oauthRepository,
        config = securityConfig,
        activityUpdater = asyncActivityUpdater,
    )
```

- [ ] **Step 3: Update Filters.kt devLoginFilter to use sessionService**

The `devLoginFilter` function needs a `sessionService` parameter. Add it and use it:

Change signature from taking `securityService` to also taking `sessionService: SessionService`. At line 261, replace:
```kotlin
val token = securityService.createSession(admin.id)
```
with:
```kotlin
val token = sessionService.createSession(admin.id)
```

Pass `sessionService` through from the call site in `buildFilterChain` (App.kt).

- [ ] **Step 4: Update AuthRoutes to accept sessionService**

Add `sessionService: SessionService` parameter to `AuthRoutes` constructor. At line 92, replace:
```kotlin
val sessionToken = securityService.createSession(user.id)
```
with:
```kotlin
val sessionToken = sessionService.createSession(user.id)
```

Pass `sessionService` from `buildUiRoutes` in App.kt.

- [ ] **Step 5: Update AuthApi to accept sessionService**

Add `sessionService: SessionService` parameter to `AuthApi` constructor. At lines 227 and 259, replace:
```kotlin
val sessionToken = securityService.createSession(user.id)
```
with:
```kotlin
val sessionToken = sessionService.createSession(user.id)
```

At line 181, replace:
```kotlin
securityService.deleteSession(token)
```
with:
```kotlin
sessionService.deleteSession(token)
```

Pass `sessionService` from `buildApiRoutes` in App.kt.

- [ ] **Step 6: Update OAuthRoutes to accept sessionService**

Add `sessionService: SessionService` parameter to `OAuthRoutes` constructor. At line 129, replace:
```kotlin
val sessionToken = securityService.createSession(user.id)
```
with:
```kotlin
val sessionToken = sessionService.createSession(user.id)
```

Pass `sessionService` from `buildUiRoutes` in App.kt.

- [ ] **Step 7: Update App.kt logout route to use sessionService**

At line 384, replace:
```kotlin
securityService.deleteSession(rawToken)
```
with:
```kotlin
ctx.sessionService.deleteSession(rawToken)
```

`ctx.sessionService` is already available in AppContext.

- [ ] **Step 8: Update SyncWebSocket to use sessionService**

Change `SyncWebSocket` constructor to accept `SessionService` instead of `SecurityService`. At line 31, replace:
```kotlin
when (val lookup = securityService.lookupSession(rawToken)) {
```
with:
```kotlin
when (val lookup = sessionService.lookupSession(rawToken)) {
```

Update the `SyncWebSocket` creation in `WebComponents` / wherever it's created.

- [ ] **Step 9: Compile and verify**

```powershell
mvn clean compile -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 10: Run tests**

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 11: Commit**

```bash
git add -A && git commit -m "refactor: remove SecurityService session delegation, callers use SessionService directly"
```

---

### Task 2: Remove WebContext facade — RoutingDsl + Filters

Replace `WebContext.KEY` with `RequestContext.KEY` + `ShellRenderer.KEY`. Update `RoutingDsl.kt` to provide `Request.requestContext` and `Request.shellRenderer` extensions.

**Files:**
- Delete content of: `platform-web/src/main/kotlin/.../web/WebContext.kt`
- Rewrite: `platform-web/src/main/kotlin/.../web/RoutingDsl.kt`
- Modify: `platform-web/src/main/kotlin/.../web/Filters.kt`
- Modify: `platform-web/src/main/kotlin/.../web/ShellRenderer.kt:165` (remove internal WebContext creation)

- [ ] **Step 1: Add ShellRenderer.KEY lens**

In `ShellRenderer.kt`, add to companion object:
```kotlin
val KEY = org.http4k.lens.RequestKey.required<ShellRenderer>("shell.renderer")
```

- [ ] **Step 2: Rewrite RoutingDsl.kt**

Replace the file with:
```kotlin
package io.github.rygel.outerstellar.platform.web

import org.http4k.core.Request

val Request.requestContext: RequestContext
    get() = RequestContext.KEY(this)

val Request.shellRenderer: ShellRenderer
    get() = ShellRenderer.KEY(this)
```

- [ ] **Step 3: Delete WebContext.kt**

Delete `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebContext.kt`.

- [ ] **Step 4: Update ShellRenderer.shell() to not create WebContext**

In `ShellRenderer.kt`, the `shell()` method creates `val webContext = WebContext(ctx, this)` at line 165 to pass to `sidebarFactory`. SidebarFactory currently takes `WebContext`. We need to change SidebarFactory to accept `RequestContext + ShellRenderer` (or just `RequestContext` since it only needs lang/theme/layout/i18n).

See Task 3 for SidebarFactory changes. For now, inline what SidebarFactory needs — OR change SidebarFactory first (Task 3) then come back here.

**Dependency: Task 3 must be done before this step.**

- [ ] **Step 5: Update Filters.kt to inject RequestContext + ShellRenderer**

Replace `WebContext.create(...)` call with:
```kotlin
val requestContext = RequestContext(request, userRepository, jwtService, securityService)
val shellRenderer = ShellRenderer(requestContext, devDashboardEnabled, appVersion, shellConfig)
```

Replace `WebContext.KEY` lens injection with:
```kotlin
request.with(RequestContext.KEY of requestContext).with(ShellRenderer.KEY of shellRenderer)
```

Replace all `WebContext.XXX_COOKIE` references with `RequestContext.XXX_COOKIE` (they were already moved in the split).

Replace `request.webContext` references with `request.requestContext` / `request.shellRenderer`.

- [ ] **Step 6: Compile to verify**

```powershell
mvn clean compile -T4 -pl platform-web -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: Will fail because many files still reference `WebContext`. This is expected — we fix in Task 4.

---

### Task 3: Update SidebarFactory to accept RequestContext

SidebarFactory methods currently take `WebContext`. They only use `lang`, `theme`, `layout`, `i18n` — all available on RequestContext + ShellRenderer.

**Files:**
- Modify: `platform-web/src/main/kotlin/.../web/SidebarFactory.kt`

- [ ] **Step 1: Change SidebarFactory method signatures**

Change every method that takes `ctx: WebContext` to take `ctx: RequestContext, shellRenderer: ShellRenderer`.

Replace:
- `ctx.lang` → `ctx.lang` (same)
- `ctx.theme` → `ctx.theme` (same)
- `ctx.layout` → `ctx.layout` (same)
- `ctx.i18n` → `shellRenderer.i18n`

- [ ] **Step 2: Update ShellRenderer.shell() to call sidebarFactory with new signatures**

In `ShellRenderer.kt` `shell()` method, replace:
```kotlin
val webContext = WebContext(ctx, this)
...
themeSelector = sidebarFactory.buildThemeSelector(webContext),
languageSelector = sidebarFactory.buildLanguageSelector(webContext),
layoutSelector = sidebarFactory.buildLayoutSelector(webContext),
```
with:
```kotlin
themeSelector = sidebarFactory.buildThemeSelector(ctx, this),
languageSelector = sidebarFactory.buildLanguageSelector(ctx, this),
layoutSelector = sidebarFactory.buildLayoutSelector(ctx, this),
```

---

### Task 4: Update page factories to accept RequestContext + ShellRenderer

Each page factory currently takes `ctx: WebContext`. Change to `requestContext: RequestContext, shellRenderer: ShellRenderer`.

**Files (each factory is independent — can be done in parallel by subagents):**
- Modify: `AuthPageFactory.kt`
- Modify: `HomePageFactory.kt`
- Modify: `ContactsPageFactory.kt`
- Modify: `SettingsPageFactory.kt`
- Modify: `AdminPageFactory.kt`
- Modify: `ErrorPageFactory.kt`
- Modify: `SearchPageFactory.kt`
- Modify: `WebPageFactory.kt`
- Modify: `InfraPageFactory.kt` (if exists)

For each factory, the mechanical changes are:
1. Change parameter type from `ctx: WebContext` to `requestContext: RequestContext, shellRenderer: ShellRenderer`
2. Replace:
   - `ctx.user` → `requestContext.user`
   - `ctx.sessionExpired` → `requestContext.sessionExpired`
   - `ctx.csrfToken` → `requestContext.csrfToken`
   - `ctx.lang` → `requestContext.lang`
   - `ctx.theme` → `requestContext.theme`
   - `ctx.layout` → `requestContext.layout`
   - `ctx.shellStyle` → `requestContext.shellStyle`
   - `ctx.i18n` → `shellRenderer.i18n`
   - `ctx.url(...)` → `shellRenderer.url(...)`
   - `ctx.shell(...)` → `shellRenderer.shell(...)`

---

### Task 5: Update route files to use new extensions

Each route file currently uses `val ctx = request.webContext`. Change to `val ctx = request.requestContext` and `val shell = request.shellRenderer`.

**Files (each route is independent):**
- Modify: `AuthRoutes.kt`
- Modify: `HomeRoutes.kt`
- Modify: `ContactsRoutes.kt`
- Modify: `ComponentRoutes.kt`
- Modify: `TOTPRoutes.kt`
- Modify: `PollApi.kt`
- Modify: `VoteApi.kt`
- Modify: `SearchRoutes.kt`
- Modify: `NotificationRoutes.kt`
- Modify: `SettingsRoutes.kt`
- Modify: `UserAdminRoutes.kt`
- Modify: `ErrorRoutes.kt`
- Modify: `DevDashboardRoutes.kt`

For each route, the mechanical changes are:
1. Replace `val ctx = request.webContext` with `val ctx = request.requestContext` and `val shell = request.shellRenderer` (only add `shell` if the route uses i18n/url/shell)
2. Replace `ctx.user` → `ctx.user` (same)
3. Replace `ctx.i18n` → `shell.i18n`
4. Replace `ctx.url(...)` → `shell.url(...)`
5. Replace `ctx.shell(...)` → `shell.shell(...)`
6. Replace `request.webContext` (passed to factories) with appropriate `requestContext` + `shellRenderer` params

---

### Task 6: Update App.kt

**Files:**
- Modify: `platform-web/src/main/kotlin/.../App.kt`

- [ ] **Step 1: Remove WebContext import and usage**

Replace:
```kotlin
io.github.rygel.outerstellar.platform.web.WebContext.SESSION_COOKIE
```
with:
```kotlin
io.github.rygel.outerstellar.platform.web.RequestContext.SESSION_COOKIE
```

Replace the Extension Dashboard route's WebContext usage with RequestContext + ShellRenderer.

Replace `request.webContext.url(...)` with `request.shellRenderer.url(...)`.

- [ ] **Step 2: Update PlatformExtension.kt**

Replace `request.webContext` usages in PlatformExtension with `request.requestContext` / `request.shellRenderer`.

---

### Task 7: Update tests

**Files:**
- Modify: `platform-web/src/test/kotlin/.../WebTest.kt`
- Modify: any test that creates or uses WebContext

- [ ] **Step 1: Search for test WebContext references**

```powershell
Get-ChildItem -Recurse "platform-web/src/test" -Filter "*.kt" | Select-String "WebContext"
```

Update any test that references WebContext to use RequestContext + ShellRenderer.

- [ ] **Step 2: Compile tests**

```powershell
mvn clean test-compile -T4 -pl platform-web -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

---

### Task 8: Full reactor verify + commit

- [ ] **Step 1: Full reactor build**

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "refactor: remove WebContext facade, use RequestContext + ShellRenderer directly"
```
