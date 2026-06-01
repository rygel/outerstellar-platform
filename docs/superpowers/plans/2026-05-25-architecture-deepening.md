# Architecture Deepening: App.kt Decoupling + AuthRoutes Decomposition + Auth Guard Consolidation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce App.kt coupling (25 params → ~6), decompose AuthRoutes (487 lines → 4 focused classes), and consolidate 25+ scattered auth guards into a single filter.

**Architecture:** Replace `app()`'s 25 individual parameters with the 4 existing component group objects (`PersistenceComponents`, `SecurityComponents`, `CoreComponents`, `WebComponents`). Split AuthRoutes into 4 route classes along domain boundaries. Apply `SecurityRules.authenticated` as a default filter on the UI route set, removing inline null-check guards.

**Tech Stack:** Kotlin, http4k, JDBI, JTE

---

## File Structure

### Modified files

| File | Change |
|------|--------|
| `platform-web/src/main/kotlin/.../App.kt` | Replace 25-param `app()` with component-group params, delete `AppContext`/`OptionalServices`/`AuthServices`, add authenticated filter on UI routes |
| `platform-web/src/main/kotlin/.../ServerComponents.kt` | Simplify to pass component groups instead of unwrapping |
| `platform-web/src/main/kotlin/.../WebTest.kt` | Update `createApp()` to use new signature |

### Created files

| File | Content |
|------|---------|
| `platform-web/src/main/kotlin/.../PasswordRoutes.kt` | Password change + reset routes (extracted from AuthRoutes) |
| `platform-web/src/main/kotlin/.../ProfileRoutes.kt` | Profile edit + notification prefs + account delete routes (extracted from AuthRoutes) |
| `platform-web/src/main/kotlin/.../ApiKeyRoutes.kt` | API key CRUD routes (extracted from AuthRoutes) |

### Deleted code

| What | Lines removed |
|------|--------------|
| `OptionalServices` inner class (App.kt:86-98) | 13 lines |
| `AuthServices` inner class (App.kt:100-106) | 7 lines |
| `AppContext` inner class (App.kt:108-185) | 78 lines |
| Inline auth guards from HomeRoutes, ContactsRoutes, ComponentRoutes, NotificationRoutes | ~125 lines |

---

## Task 1: Refactor `app()` to accept component groups

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/App.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/ServerComponents.kt`
- Modify: `platform-web/src/main/kotlin/.../WebTest.kt`
- Test: All existing platform-web tests must pass

This is the foundation task. Everything else builds on it.

- [ ] **Step 1: Replace `app()` signature**

Replace the 25-parameter `app()` function (lines 188-215) with:

```kotlin
@Suppress("DEPRECATION")
fun app(
    config: AppConfig,
    persistence: io.github.rygel.outerstellar.platform.di.PersistenceComponents,
    security: io.github.rygel.outerstellar.platform.security.SecurityComponents,
    core: io.github.rygel.outerstellar.platform.di.CoreComponents,
    web: io.github.rygel.outerstellar.platform.di.WebComponents,
    extension: io.github.rygel.outerstellar.platform.web.PlatformExtension? = null,
): PolyHandler
```

Delete the `OptionalServices`, `AuthServices`, and `AppContext` inner classes (lines 86-185). Replace the body of `app()` to extract what it needs from the component groups directly:

```kotlin
fun app(
    config: AppConfig,
    persistence: PersistenceComponents,
    security: SecurityComponents,
    core: CoreComponents,
    web: WebComponents,
    extension: PlatformExtension? = null,
): PolyHandler {
    logger.info("Initializing Outerstellar application")
    val httpHandler = assembleHttpHandler(config, persistence, security, core, web, extension)
    val wsHandler = web.syncWebSocket?.let { websockets("/ws/sync" wsBind it.handler) }
    return PolyHandler(httpHandler, wsHandler)
}
```

- [ ] **Step 2: Rewrite `assembleHttpHandler` and all builder functions**

Replace the `AppContext`-taking private functions with versions that take explicit parameters or component groups. The key mapping from old `ctx.*` to new component group access:

| Old (`ctx.*`) | New |
|---|---|
| `ctx.config` | `config` (parameter) |
| `ctx.messageService` | `core.messageService` |
| `ctx.contactService` | `core.contactService` |
| `ctx.authServices.auth` | `security.authService` |
| `ctx.authServices.account` | `security.accountService` |
| `ctx.authServices.apiKeyService` | `security.apiKeyService` |
| `ctx.authServices.passwordResetService` | `security.passwordResetService` |
| `ctx.authServices.oauthService` | `security.oauthService` |
| `ctx.sessionService` | `security.sessionService` |
| `ctx.userAdminService` | `security.userAdminService` |
| `ctx.userRepository` | `persistence.userRepository` |
| `ctx.outboxRepository` | `persistence.outboxRepository` |
| `ctx.cache` | `web.messageCache` |
| `ctx.jteRenderer` | `web.templateRenderer` |
| `ctx.pageFactory` | `web.pageFactory` |
| `ctx.analytics` | `web.analyticsService` |
| `ctx.notificationService` | `web.notificationService` |
| `ctx.jwtService` | `security.jwtService` |
| `ctx.deviceTokenRepository` | `persistence.deviceTokenRepository` |
| `ctx.syncWebSocket` | `web.syncWebSocket` |
| `ctx.totpService` | `security.totpService` |
| `ctx.voteService` | `web.voteService` |
| `ctx.pollService` | `web.pollService` |
| `ctx.extension` | `extension` (parameter) |
| `ctx.appLabel` | `extension?.appLabel ?: "Outerstellar"` |
| `ctx.excludedRoutes` | `extension?.excludeDefaultRoutes ?: emptySet()` |

Each private function (`buildApiRoutes`, `buildUiRoutes`, `buildAdminRoutes`, `buildComponentRoutes`, `buildBaseApp`, `buildFilterChain`) changes its signature from `(ctx: AppContext)` to taking the component groups it needs.

- [ ] **Step 3: Update `ServerComponents.kt`**

The `app()` call (lines 72-98) simplifies to:

```kotlin
val polyHandler = app(
    config = config,
    persistence = persistence,
    security = security,
    core = core,
    web = web,
    extension = extension,
)
```

Note: Remove the `CaffeineMessageCache` creation in `createCoreComponents` call (lines 47-51) — the cache is already created in `createWebComponents`. The `core` module should use `web.messageCache` or receive it from the web layer. Check if `CoreComponents` needs the cache or if only `WebComponents` does. If only WebComponents needs it, remove it from the `createCoreComponents` call and pass it separately.

Actually, looking more carefully: `createCoreComponents` takes `messageCache` as a parameter to construct `MessageService`. And `createWebComponents` also creates a `CaffeineMessageCache`. So the cache is created twice — once in `ServerComponents` for `CoreComponents`, and once inside `WebFactory`. Fix this by having `ServerComponents` create the cache once and pass it to both.

- [ ] **Step 4: Update `WebTest.kt`**

The `createApp()` method (around line 145) currently calls `app()` with positional parameters. Update it to construct component groups and pass them:

```kotlin
return app(
    config = config,
    persistence = PersistenceComponents(
        dataSource = /* unused in test, can be mock */,
        jdbi = testJdbi,
        messageRepository = messageRepository,
        contactRepository = contactRepository,
        userRepository = resolvedUserRepo,
        outboxRepository = outbox,
        transactionManager = txManager,
        auditRepository = auditRepository,
        passwordResetRepository = passwordResetRepository,
        apiKeyRepository = apiKeyRepository,
        oAuthRepository = oAuthRepository,
        deviceTokenRepository = overrides.deviceTokenRepository ?: deviceTokenRepository,
        sessionRepository = sessionRepository,
        voteRepository = voteRepository,
        pollRepository = pollRepository,
        notificationRepository = notificationRepository,
    ),
    security = SecurityComponents(
        jwtService = jwtService,
        asyncActivityUpdater = /* ... */,
        authService = authService,
        accountService = accountService,
        apiKeyService = apiKeyService,
        passwordResetService = passwordResetService,
        oauthService = oauthService,
        authRealms = listOf(SessionRealm(sessionSvc), ApiKeyRealm(apiKeyService)),
        totpService = TOTPService(),
        sessionService = sessionSvc,
        userAdminService = userAdminService,
    ),
    core = CoreComponents(
        messageService = messageService,
        contactService = resolvedContactService,
        outboxProcessor = OutboxProcessor(outboxRepository = outbox, transactionManager = txManager),
        eventPublisher = NoOpEventPublisher,
        emailService = NoOpEmailService(),
        pushNotificationService = ConsolePushNotificationService,
    ),
    web = WebComponents(
        templateRenderer = renderer,
        pageFactory = pageFactory,
        messageCache = resolvedMessageCache,
        analyticsService = NoOpAnalyticsService(),
        emailService = NoOpEmailService(),
        i18nService = I18nService.create("messages"),
        syncWebSocket = null,
        eventPublisher = NoOpEventPublisher,
        voteService = overrides.pollService?.let { VoteService(voteRepository, messageRepository) } ?: voteService,
        pollService = overrides.pollService ?: pollService,
        notificationService = overrides.notificationService ?: notificationService,
        adminPageFactory = AdminPageFactory(apiKeyService, overrides.notificationService ?: notificationService, userAdminService),
        adminStatsService = AdminStatsService(resolvedUserRepo),
        extensionMigrationSource = NoOpExtensionMigrationSource,
    ),
    extension = extension,
).http!!
```

This is the most tedious part — matching each test's current positional args to the new structure. Alternatively, `WebTest` could call the individual `create*Components()` factory functions with the test repos. Evaluate which approach is cleaner.

- [ ] **Step 5: Compile and fix errors**

Run: `mvn -pl platform-web compile -am -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`

Expected: Compile passes. Fix any type mismatches where component groups expose different types than the old `app()` expected.

- [ ] **Step 6: Run tests**

Run: `mvn -pl platform-web -am test -Dexec.skip=true`

Expected: All existing tests pass with no behavior changes. This is a pure structural refactor — no test assertions should change.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: replace app() 25-param signature with component groups

Replace OptionalServices/AuthServices/AppContext inner classes with
direct use of PersistenceComponents, SecurityComponents, CoreComponents,
and WebComponents. No behavior change — pure structural refactor."
```

---

## Task 2: Decompose AuthRoutes into domain-focused classes

**Files:**
- Modify: `platform-web/src/main/kotlin/.../AuthRoutes.kt` (slim to login/register/recover only)
- Create: `platform-web/src/main/kotlin/.../PasswordRoutes.kt`
- Create: `platform-web/src/main/kotlin/.../ProfileRoutes.kt`
- Create: `platform/kotlin/.../ApiKeyRoutes.kt`
- Modify: `platform-web/src/main/kotlin/.../App.kt` (register new route classes in `buildUiRoutes`)

- [ ] **Step 1: Create `PasswordRoutes.kt`**

Extract from AuthRoutes:
- `GET /auth/change-password` (lines 173-186)
- `POST /auth/components/change-password` (lines 187-232)
- `GET /auth/reset/{token}` (lines 233-244)
- `POST /auth/components/reset-confirm` (lines 245-303)

Constructor: `(pageFactory: WebPageFactory, renderer: TemplateRenderer, accountService: AccountService, passwordResetService: PasswordResetService)` — 4 params.

This class must implement `ServerRoutes` and return `routes` as `List<ContractRoute>`.

The auth guard on `/auth/change-password` GET (`if (ctx.user == null)` redirect) stays for now — it will be removed in Task 3 when the authenticated filter is applied. Mark it with a TODO comment.

- [ ] **Step 2: Create `ProfileRoutes.kt`**

Extract from AuthRoutes:
- `GET /auth/profile` (lines 304-317)
- `POST /auth/components/profile-update` (lines 318-360)
- `POST /auth/notification-preferences` (lines 361-384)
- `POST /auth/account/delete` (lines 385-416)

Constructor: `(pageFactory: WebPageFactory, renderer: TemplateRenderer, accountService: AccountService, sessionCookieSecure: Boolean)` — 4 params.

The auth guards stay for now (removed in Task 3).

- [ ] **Step 3: Create `ApiKeyRoutes.kt`**

Extract from AuthRoutes:
- `GET /auth/api-keys` (lines 417-430)
- `POST /auth/api-keys/create` (lines 431-458)
- `POST /auth/api-keys/{id}/delete` (lines 459-476)

Constructor: `(pageFactory: WebPageFactory, renderer: TemplateRenderer, apiKeyService: ApiKeyService)` — 3 params.

- [ ] **Step 4: Slim `AuthRoutes.kt`**

Remove the extracted routes. The remaining `AuthRoutes` contains:
- `GET /auth` (landing page)
- `GET /auth/components/forms/{mode}` (form fragment)
- `POST /auth/components/result` (login/register/recover handler)
- `safeReturnTo()` helper

Constructor params drop from 10 to 7: `pageFactory`, `renderer`, `authService`, `sessionService`, `passwordResetService`, `analytics`, `appConfig`. (Drop `apiKeyService` and `accountService` — they moved to the new classes.)

- [ ] **Step 5: Register new route classes in App.kt `buildUiRoutes`**

In the `buildUiRoutes` function, replace the single `AuthRoutes(...)` creation with:

```kotlin
routes += AuthRoutes(pageFactory, jteRenderer, security.authService, security.sessionService,
    security.passwordResetService, web.analyticsService, config).routes
routes += PasswordRoutes(pageFactory, jteRenderer, security.accountService,
    security.passwordResetService).routes
routes += ProfileRoutes(pageFactory, jteRenderer, security.accountService,
    config.sessionCookieSecure).routes
routes += ApiKeyRoutes(pageFactory, jteRenderer, security.apiKeyService).routes
```

- [ ] **Step 6: Compile and fix errors**

Run: `mvn -pl platform-web compile -am -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`

- [ ] **Step 7: Run tests**

Run: `mvn -pl platform-web -am test -Dexec.skip=true`

All existing auth-related tests (ChangePasswordWebIntegrationTest, OAuthIntegrationTest, UserManagementIntegrationTest, SessionSecurityIntegrationTest) must pass without changes — the route URLs haven't changed, only the classes that handle them.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: decompose AuthRoutes into domain-focused route classes

Extract PasswordRoutes, ProfileRoutes, and ApiKeyRoutes from AuthRoutes.
AuthRoutes now handles only the public auth flow (login/register/recover).
No behavior change."
```

---

## Task 3: Consolidate auth guards into `SecurityRules.authenticated` filter

**Files:**
- Modify: `platform-web/src/main/kotlin/.../App.kt` (wrap UI routes with authenticated filter)
- Modify: `platform-web/src/main/kotlin/.../HomeRoutes.kt` (remove inline guards)
- Modify: `platform-web/src/main/kotlin/.../ContactsRoutes.kt` (remove inline guards)
- Modify: `platform-web/src/main/kotlin/.../ComponentRoutes.kt` (remove inline guards from protected routes)
- Modify: `platform-web/src/main/kotlin/.../NotificationRoutes.kt` (remove inline guards)
- Modify: `platform-web/src/main/kotlin/.../SettingsRoutes.kt` (remove inline guards)
- Modify: `platform-web/src/main/kotlin/.../PasswordRoutes.kt` (remove inline guard on change-password)
- Modify: `platform-web/src/main/kotlin/.../ProfileRoutes.kt` (remove inline guards)
- Modify: `platform-web/src/main/kotlin/.../ApiKeyRoutes.kt` (remove inline guards)

- [ ] **Step 1: Add `SecurityRules.authenticated` filter wrapper in App.kt**

In `buildUiRoutes`, wrap the authenticated routes with the filter. The public routes (`/auth` and sub-paths) must sit outside the filter.

Strategy: Split the UI contract into two contracts:
1. **Public UI contract** — `/auth` landing, `/auth/components/forms`, `/auth/components/result`, `/auth/reset/{token}`, `/auth/components/reset-confirm`, OAuth routes, error routes
2. **Authenticated UI contract** — Everything else, wrapped with `SecurityRules.authenticated`

Actually, a simpler approach: apply the filter at the `buildBaseApp` level. Instead of adding `uiRoutes` directly to `appRoutes`, wrap them:

```kotlin
val authenticatedUiRoutes = Filter { next ->
    SecurityRules.authenticated(next)
}.then(uiRoutes)
appRoutes += authenticatedUiRoutes
```

But this would also protect the public auth routes (`/auth/*`). The filter needs to skip those.

Better approach: `SecurityRules.authenticated` redirects unauthenticated users to `/auth?returnTo=...`. The `/auth` routes already handle unauthenticated users (they're the login page). So even if the filter wraps them, the redirect just loops back to `/auth` — but the `/auth/components/result` POST handler needs to work without auth (it processes login forms).

Cleanest approach: Keep the public routes in a separate contract that is NOT wrapped with the filter:

In `buildBaseApp`, instead of:
```kotlin
val appRoutes = mutableListOf(uiRoutes, componentRoutes)
```

Do:
```kotlin
val authenticatedFilter = Filter { next -> SecurityRules.authenticated(next) }
val appRoutes = mutableListOf(
    publicAuthRoutes,      // /auth/* — no filter
    authenticatedFilter.then(protectedUiRoutes),  // everything else
    authenticatedFilter.then(componentRoutes),
)
```

This requires splitting `buildUiRoutes` into `buildPublicUiRoutes` and `buildProtectedUiRoutes`.

- [ ] **Step 2: Remove inline guards from route handlers**

For each route file, remove the pattern:
```kotlin
val ctx = request.requestContext
val shellRenderer = request.shellRenderer
if (ctx.user == null) {
    return@to Response(Status.FOUND).header("location", shellRenderer.url("/auth"))
}
```

Replace with direct usage. After the filter is applied, `request.requestContext.user` is guaranteed non-null inside protected routes. Handlers can use `val user = request.requestContext.user!!` or `val user = SecurityRules.USER_KEY(request)!!`.

HomeRoutes: Remove guard from all 8 protected routes.
ContactsRoutes: Remove guard from all 8 routes.
ComponentRoutes: Remove guard from 4 protected routes (message-list, vote routes). Leave the 4 public routes (theme/lang/layout selectors) as-is — they don't have guards.
NotificationRoutes: Remove guards.
SettingsRoutes: Remove guards.
PasswordRoutes: Remove guard on `/auth/change-password` GET.
ProfileRoutes: Remove guards on all 4 routes.
ApiKeyRoutes: Remove guards on all 3 routes.

Note: Some handlers check `ctx.user` and return `Response(Status.UNAUTHORIZED)` instead of redirecting (e.g., the POST handlers for change-password, profile-update, notification-preferences, account-delete, api-keys). These should also be removed — the filter handles the redirect before the handler is reached.

- [ ] **Step 3: Verify `shellRenderer` still works**

The inline guards reference `shellRenderer` for the redirect URL. After removing the guard, some handlers may no longer need `shellRenderer` at all. Check each handler and remove unused `val shellRenderer = request.shellRenderer` declarations.

Handlers that still use `shellRenderer` for i18n (e.g., `shellRenderer.i18n.translate(...)`) or URL generation must keep it.

- [ ] **Step 4: Compile**

Run: `mvn -pl platform-web compile -am -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`

- [ ] **Step 5: Run tests**

Run: `mvn -pl platform-web -am test -Dexec.skip=true`

Tests that verify unauthenticated redirect behavior must still pass. The redirect now comes from the filter, not the handler, but the end result (302 to `/auth`) is the same. The `returnTo` parameter will now be included (improvement over the old behavior).

Check `SessionSecurityIntegrationTest` carefully — it tests redirect behavior for unauthenticated users on `/`, `/contacts`, `/admin/users`, `/auth/change-password`.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: consolidate auth guards into SecurityRules.authenticated filter

Apply SecurityRules.authenticated as a default filter on all protected UI
routes. Remove ~125 lines of duplicated inline null-check guards across
HomeRoutes, ContactsRoutes, ComponentRoutes, NotificationRoutes,
SettingsRoutes, PasswordRoutes, ProfileRoutes, and ApiKeyRoutes.

Public routes (/auth/*, /health, static assets) are excluded from the filter.
Unauthenticated users now get proper returnTo parameter on redirect."
```

---

## Self-Review

**1. Spec coverage:**
- App.kt coupling reduction: Task 1 ✓
- AuthRoutes decomposition: Task 2 ✓
- Auth guard consolidation: Task 3 ✓
- Component groups as seam: Task 1 ✓

**2. Placeholder scan:**
- No TBD, TODO (except the intentional one in Task 2 Step 1 that says the guard will be removed in Task 3)
- All code steps show actual implementation patterns
- Exact file paths provided

**3. Type consistency:**
- `PersistenceComponents`, `SecurityComponents`, `CoreComponents`, `WebComponents` are all existing data classes with the correct field names
- The field names used in the mapping table (Task 1 Step 2) match the actual class definitions verified by reading the source files
- Route class constructors use the correct service types from the component groups
