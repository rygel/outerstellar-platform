# Decompose SecurityService Facade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete SecurityService and wire its 5 sub-services directly, eliminating the dual-instance bug and removing a shallow pass-through module.

**Architecture:** SecurityService is a facade that creates internal copies of AuthService, AccountService, ApiKeyService, PasswordResetService, and OAuthService, then delegates every method. SecurityComponents creates separate copies of AuthService and AccountService. After this change, SecurityComponents creates one instance of each sub-service, and consumers take the specific service they need. The only added logic in SecurityService (audit on API key create/delete) moves into ApiKeyService.

**Tech Stack:** Kotlin, Maven multi-module, http4k, JDBI, JUnit 5

---

## File Structure

### New files
- `platform-security/.../security/AuditExtensions.kt` — extension function replacing 6 duplicated `audit()` helpers and `sanitize()`

### Modified files (platform-security)
- `platform-security/.../security/ApiKeyService.kt` — add `auditRepository` param, add audit logging to `createApiKey` and `deleteApiKey`
- `platform-security/.../security/SecurityComponents.kt` — create sub-services directly, remove SecurityService from the data class, add new sub-services to the data class, update ApiKeyRealm to take ApiKeyService
- `platform-security/.../security/AuthRealm.kt` — `ApiKeyRealm` takes `ApiKeyService` instead of `SecurityService`
- Delete `platform-security/.../security/SecurityService.kt`

### Modified files (platform-web — consumers)
- `platform-web/.../App.kt` — replace `SecurityService` param with `ApiKeyService`, `PasswordResetService`, `OAuthService`; update AuthApi, AuthRoutes, OAuthRoutes construction; update `ApiKeyRealm` usage
- `platform-web/.../di/WebFactory.kt` — replace `SecurityService` with sub-services in `WebComponents` and `createWebComponents`
- `platform-web/.../ServerComponents.kt` — pass sub-services instead of `security.securityService`
- `platform-web/.../web/WebPageFactory.kt` — replace `SecurityService` with `OAuthService` + `ApiKeyService` + `PasswordResetService`
- `platform-web/.../web/AdminPageFactory.kt` — replace `SecurityService` with `ApiKeyService` + `PasswordResetService`
- `platform-web/.../web/AuthApi.kt` — replace `SecurityService` with `ApiKeyService` + `PasswordResetService`
- `platform-web/.../web/AuthRoutes.kt` — replace `SecurityService` with `ApiKeyService` + `PasswordResetService`
- `platform-web/.../web/OAuthRoutes.kt` — replace `SecurityService` with `OAuthService`
- `platform-web/.../web/PlatformExtension.kt` — replace `SecurityService` in `ExtensionContext` with sub-services

### Modified files (tests)
- `platform-web/.../web/WebTest.kt` — replace `createSecurityService()` with direct sub-service creation
- All test files that construct `SecurityService` directly (~15 files) — replace with sub-service construction

---

### Task 1: Create AuditExtensions.kt

**Files:**
- Create: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuditExtensions.kt`

This consolidates the duplicated `audit()` method (currently copy-pasted across 6 files) and `sanitize()` (copy-pasted across 5 files) into one extension and one top-level function.

- [ ] **Step 1: Create AuditExtensions.kt**

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.AuditRepository

fun AuditRepository.logAction(
    action: String,
    actor: User? = null,
    target: User? = null,
    detail: String? = null,
    targetUsername: String? = null,
) {
    log(
        AuditEntry(
            actorId = actor?.id?.toString(),
            actorUsername = actor?.username,
            targetId = target?.id?.toString(),
            targetUsername = targetUsername ?: target?.username,
            action = action,
            detail = detail,
        )
    )
}

fun sanitize(value: String, maxLength: Int = MAX_LOG_ID_LENGTH): String =
    value.take(maxLength).replace('\n', ' ').replace('\r', ' ')

private const val MAX_LOG_ID_LENGTH = 80
```

- [ ] **Step 2: Compile**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuditExtensions.kt
git commit -m "feat(security): add AuditExtensions with shared logAction and sanitize"
```

---

### Task 2: Migrate existing services to use AuditExtensions

**Files:**
- Modify: `platform-security/.../security/AuthService.kt`
- Modify: `platform-security/.../security/AccountService.kt`
- Modify: `platform-security/.../security/PasswordResetService.kt`
- Modify: `platform-security/.../security/OAuthService.kt`
- Modify: `platform-security/.../security/UserAdminService.kt`

Each service has a private `audit()` method (identical to every other) and a private `sanitize()` method. Replace both with imports from AuditExtensions. The `private fun audit(...)` and `private fun sanitize(...)` and their companion `MAX_LOG_ID_LENGTH` constants are deleted.

- [ ] **Step 1: In AuthService.kt** — delete the private `audit()` method (lines 147-164) and private `sanitize()` (line 145) and the `MAX_LOG_ID_LENGTH` constant (line 167). Add `import io.github.rygel.outerstellar.platform.security.logAction` and `import io.github.rygel.outerstellar.platform.security.sanitize`. Replace all `audit(` calls with `auditRepository?.logAction(`. Replace `sanitize(` calls with `sanitize(` (same name, now top-level import).

- [ ] **Step 2: In AccountService.kt** — same pattern. Delete private `audit()` (lines 101-118), `sanitize()` (line 99), `MAX_LOG_ID_LENGTH` (line 122). Add imports. Replace `audit(` with `auditRepository?.logAction(`. Replace `sanitize(` with the imported `sanitize(`.

- [ ] **Step 3: In PasswordResetService.kt** — same pattern. Delete private `audit()` (lines 80-91), `sanitize()` (line 27), `MAX_LOG_ID_LENGTH` (line 94). Add imports. Replace calls.

- [ ] **Step 4: In OAuthService.kt** — same pattern. Delete private `audit()` (lines 68-79), `sanitize()` (line 21), `MAX_LOG_ID_LENGTH` (line 11). Add imports. Replace calls.

- [ ] **Step 5: In UserAdminService.kt** — check for the same `audit()` and `sanitize()` patterns. Apply the same migration.

- [ ] **Step 6: Compile**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 7: Run security tests**

Run: `mvn -pl platform-security test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor(security): migrate 5 services to shared AuditExtensions"
```

---

### Task 3: Add audit logging to ApiKeyService

**Files:**
- Modify: `platform-security/.../security/ApiKeyService.kt`

ApiKeyService currently has no audit logging. SecurityService wraps it to add audit on `createApiKey` and `deleteApiKey`. After decomposition, ApiKeyService owns its own audit trail.

- [ ] **Step 1: Add auditRepository parameter to ApiKeyService constructor**

Change the constructor from:

```kotlin
class ApiKeyService(
    private val userRepository: UserRepository,
    private val apiKeyRepository: ApiKeyRepository? = null,
)
```

To:

```kotlin
class ApiKeyService(
    private val userRepository: UserRepository,
    private val apiKeyRepository: ApiKeyRepository? = null,
    private val auditRepository: AuditRepository? = null,
)
```

Add imports for `AuditRepository`, `logAction`, and `AuditEntry` (via the extension).

- [ ] **Step 2: Add audit logging to createApiKey**

After the `logger.info` line in `createApiKey`, add:

```kotlin
val user = userRepository.findById(userId)
auditRepository?.logAction("API_KEY_CREATED", actor = user, detail = "name=$name")
```

Note: this adds a `findById` call that SecurityService was already doing. The net cost is the same — one extra DB read, same as before.

- [ ] **Step 3: Add audit logging to deleteApiKey**

After the `apiKeyRepository?.delete` line in `deleteApiKey`, add:

```kotlin
val user = userRepository.findById(userId)
auditRepository?.logAction("API_KEY_DELETED", actor = user, detail = "keyId=$keyId")
```

- [ ] **Step 4: Compile**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/ApiKeyService.kt
git commit -m "feat(security): add audit logging to ApiKeyService create/delete"
```

---

### Task 4: Update SecurityComponents to wire sub-services directly

**Files:**
- Modify: `platform-security/.../security/SecurityComponents.kt`

Remove SecurityService from the data class. Add `apiKeyService`, `passwordResetService`, `oauthService`. Update the factory function to create each sub-service once. Update `ApiKeyRealm` to take `ApiKeyService`.

- [ ] **Step 1: Update SecurityComponents data class**

Replace:

```kotlin
class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val securityService: SecurityService,
    val authService: AuthService,
    val accountService: AccountService,
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)
```

With:

```kotlin
class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val authService: AuthService,
    val accountService: AccountService,
    val apiKeyService: ApiKeyService,
    val passwordResetService: PasswordResetService,
    val oauthService: OAuthService,
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)
```

- [ ] **Step 2: Update createSecurityComponents factory**

Replace the entire function body. The key changes:
1. Create `AuthService` once (shared)
2. Create `AccountService` once (shared)
3. Create `ApiKeyService` once with `auditRepository`
4. Create `PasswordResetService` once
5. Create `OAuthService` once
6. `ApiKeyRealm` takes `apiKeyService` instead of `securityService`
7. Delete the `SecurityService(...)` construction
8. Return updated `SecurityComponents`

```kotlin
fun createSecurityComponents(
    config: AppConfig,
    userRepository: UserRepository,
    auditRepository: AuditRepository? = null,
    resetRepository: PasswordResetRepository? = null,
    apiKeyRepository: ApiKeyRepository? = null,
    emailService: EmailService? = null,
    oauthRepository: OAuthRepository? = null,
    sessionRepository: SessionRepository? = null,
): SecurityComponents {
    val passwordEncoder = BCryptPasswordEncoder()
    val jwtService = JwtService(config.jwt)
    val asyncActivityUpdater = AsyncActivityUpdater(userRepository)
    val securityConfig =
        SecurityConfig(
            appBaseUrl = config.appBaseUrl,
            sessionTimeoutSeconds = config.sessionTimeoutMinutes.toLong() * 60,
            maxFailedLoginAttempts = config.maxFailedLoginAttempts,
            lockoutDurationSeconds = config.lockoutDurationSeconds,
            sessionAbsoluteTimeoutSeconds = config.sessionAbsoluteTimeoutMinutes.toLong() * 60,
            registrationEnabled = config.registrationEnabled,
        )
    val totpService = TOTPService()
    val sessionService =
        SessionService(
            sessionRepository = sessionRepository ?: error("SessionRepository required"),
            userRepository = userRepository,
            config = securityConfig,
            activityUpdater = asyncActivityUpdater,
        )
    val userAdminService = UserAdminService(userRepository = userRepository, auditRepository = auditRepository)
    val authService =
        AuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            auditRepository = auditRepository,
            config = securityConfig,
        )
    val accountService =
        AccountService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            sessionRepository = sessionRepository,
            auditRepository = auditRepository,
        )
    val apiKeyService =
        ApiKeyService(
            userRepository = userRepository,
            apiKeyRepository = apiKeyRepository,
            auditRepository = auditRepository,
        )
    val passwordResetService =
        PasswordResetService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            resetRepository = resetRepository,
            auditRepository = auditRepository,
            sessionRepository = sessionRepository,
            emailService = emailService,
            appBaseUrl = config.appBaseUrl,
        )
    val oauthService =
        OAuthService(
            userRepository = userRepository,
            passwordEncoder = passwordEncoder,
            oauthRepository = oauthRepository,
            auditRepository = auditRepository,
        )
    val permissionResolver = RoleBasedPermissionResolver()
    val authRealms = listOf(SessionRealm(sessionService), ApiKeyRealm(apiKeyService))
    return SecurityComponents(
        passwordEncoder = passwordEncoder,
        jwtService = jwtService,
        asyncActivityUpdater = asyncActivityUpdater,
        authService = authService,
        accountService = accountService,
        apiKeyService = apiKeyService,
        passwordResetService = passwordResetService,
        oauthService = oauthService,
        permissionResolver = permissionResolver,
        authRealms = authRealms,
        totpService = totpService,
        sessionService = sessionService,
        userAdminService = userAdminService,
    )
}
```

Remove the `SecurityService` import.

- [ ] **Step 3: Update AuthRealm.kt — ApiKeyRealm**

In `AuthRealm.kt`, change `ApiKeyRealm` to take `ApiKeyService`:

```kotlin
class ApiKeyRealm(private val apiKeyService: ApiKeyService) : AuthRealm {
    override val name = "api-key"

    override fun authenticate(token: String): AuthResult {
        val user = apiKeyService.authenticateApiKey(token)
        return if (user != null) AuthResult.Authenticated(user) else AuthResult.Skipped
    }
}
```

- [ ] **Step 4: Compile**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 5: Run security tests**

Run: `mvn -pl platform-security test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityComponents.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/AuthRealm.kt
git commit -m "refactor(security): wire sub-services directly in SecurityComponents, update ApiKeyRealm"
```

---

### Task 5: Delete SecurityService.kt

**Files:**
- Delete: `platform-security/.../security/SecurityService.kt`

The file is no longer referenced from platform-security. This task only deletes it after confirming no platform-security references remain.

- [ ] **Step 1: Verify no platform-security references**

Run: `rg "SecurityService" platform-security/`
Expected: No matches (all references removed in Task 4)

- [ ] **Step 2: Delete the file**

Delete `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt`

- [ ] **Step 3: Compile platform-security**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git rm platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt
git commit -m "refactor(security): delete SecurityService facade"
```

---

### Task 6: Update platform-web consumers — production code

**Files:**
- Modify: `platform-web/.../App.kt`
- Modify: `platform-web/.../di/WebFactory.kt`
- Modify: `platform-web/.../ServerComponents.kt`
- Modify: `platform-web/.../web/WebPageFactory.kt`
- Modify: `platform-web/.../web/AdminPageFactory.kt`
- Modify: `platform-web/.../web/AuthApi.kt`
- Modify: `platform-web/.../web/AuthRoutes.kt`
- Modify: `platform-web/.../web/OAuthRoutes.kt`
- Modify: `platform-web/.../web/PlatformExtension.kt`

This is the largest task. Every production file that imports or uses `SecurityService` must be updated. The pattern is consistent: replace `SecurityService` with the specific sub-service(s) the consumer actually calls.

**Consumer mapping (what each uses from SecurityService):**

| Consumer | SecurityService methods used | Replace with |
|---|---|---|
| `AuthApi` | `createApiKey`, `listApiKeys`, `deleteApiKey`, `requestPasswordReset`, `resetPassword` | `ApiKeyService` + `PasswordResetService` |
| `AuthRoutes` | `requestPasswordReset`, `resetPassword`, `createApiKey`, `deleteApiKey` | `ApiKeyService` + `PasswordResetService` |
| `OAuthRoutes` | `findOrCreateOAuthUser` | `OAuthService` |
| `WebPageFactory` | passes to `AdminPageFactory` | `OAuthService` + `ApiKeyService` + `PasswordResetService` (or just pass down AdminPageFactory directly) |
| `AdminPageFactory` | `listApiKeys` (via securityService) | `ApiKeyService` + `PasswordResetService` |
| `PlatformExtension.ExtensionContext` | exposes `securityService` | `OAuthService` + `ApiKeyService` |
| `App.kt` `ApiKeyRealm` construction | passes `securityService` | passes `apiKeyService` |
| `App.kt` `app()` param | passes to routes | pass sub-services |
| `ServerComponents` | `security.securityService` | `security.apiKeyService`, `security.passwordResetService`, `security.oauthService` |
| `WebFactory` | passes through | pass sub-services |

- [ ] **Step 1: Update App.kt**

1. Replace `import ...SecurityService` with imports for `ApiKeyService`, `PasswordResetService`, `OAuthService`.
2. In the `app()` function signature, replace `securityService: SecurityService` with three params: `apiKeyService: ApiKeyService`, `passwordResetService: PasswordResetService`, `oauthService: OAuthService`.
3. Update `AuthServices` class — remove `security: SecurityService`, add the three sub-services (or just pass them separately to the routes that need them). Since `AuthServices` is only used as a bag, simplify: remove it and just use the individual services directly.
4. Update `AppContext` — replace `val securityService` with `val apiKeyService`, `val passwordResetService`, `val oauthService`.
5. Update `ApiKeyRealm` construction: `ApiKeyRealm(ctx.apiKeyService)` instead of `ApiKeyRealm(ctx.securityService)`.
6. Update `AuthApi` construction: replace `securityService` with `apiKeyService` and `passwordResetService`.
7. Update `AuthRoutes` construction: replace `securityService` with `apiKeyService` and `passwordResetService`.
8. Update `OAuthRoutes` construction: replace `securityService` with `oauthService`.
9. Update `buildAdminRoutes` — replace `ctx.securityService` with `ctx.apiKeyService` if used.
10. Update `ExtensionContext` construction — pass sub-services instead of `securityService`.

- [ ] **Step 2: Update ServerComponents.kt**

Replace `security.securityService` with `security.apiKeyService`, `security.passwordResetService`, `security.oauthService` in the `app()` call and `createWebComponents()` call.

- [ ] **Step 3: Update WebFactory.kt**

1. Replace `securityService: SecurityService` param with `apiKeyService: ApiKeyService`, `passwordResetService: PasswordResetService`, `oauthService: OAuthService`.
2. In `WebPageFactory` construction, pass the sub-services instead of `securityService`.
3. In `AdminPageFactory` construction, pass `apiKeyService` and `passwordResetService` instead of `securityService`.
4. Update `WebComponents` data class if it exposes `SecurityService`.

- [ ] **Step 4: Update WebPageFactory.kt**

Replace `securityService: SecurityService?` constructor param with `apiKeyService: ApiKeyService?`, `passwordResetService: PasswordResetService?`, `oauthService: OAuthService?`. Update all sub-factory construction and method implementations that use it.

- [ ] **Step 5: Update AdminPageFactory.kt**

Replace `securityService: SecurityService?` with `apiKeyService: ApiKeyService?`, `passwordResetService: PasswordResetService?`. Update all methods that call `securityService.listApiKeys(...)` etc.

- [ ] **Step 6: Update AuthApi.kt**

Replace `private val securityService: SecurityService` with `private val apiKeyService: ApiKeyService` and `private val passwordResetService: PasswordResetService`. Update all method calls:
- `securityService.createApiKey(...)` → `apiKeyService.createApiKey(...)`
- `securityService.listApiKeys(...)` → `apiKeyService.listApiKeys(...)`
- `securityService.deleteApiKey(...)` → `apiKeyService.deleteApiKey(...)`
- `securityService.requestPasswordReset(...)` → `passwordResetService.requestPasswordReset(...)`
- `securityService.resetPassword(...)` → `passwordResetService.resetPassword(...)`

- [ ] **Step 7: Update AuthRoutes.kt**

Same pattern as AuthApi. Replace `private val securityService: SecurityService` with `apiKeyService` and `passwordResetService`. Update all method calls.

- [ ] **Step 8: Update OAuthRoutes.kt**

Replace `private val securityService: SecurityService` with `private val oauthService: OAuthService`. Update `securityService.findOrCreateOAuthUser(...)` → `oauthService.findOrCreateOAuthUser(...)`.

- [ ] **Step 9: Update PlatformExtension.kt**

Replace `val securityService: SecurityService` in `ExtensionContext` with the sub-services extensions actually need. Check what `ExtensionContext` exposes: `securityService` is used in `forTesting()` and passed to extensions. Determine which sub-services extensions need (likely `OAuthService` for `findOrCreateOAuthUser`, `ApiKeyService` for API key management). If extensions only need `findOrCreateOAuthUser`, replace with `oauthService: OAuthService`. Update `forTesting()` accordingly.

- [ ] **Step 10: Compile platform-web**

Run: `mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true" -Dexec.skip=true`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "refactor(web): replace SecurityService with direct sub-service wiring"
```

---

### Task 7: Update platform-web test code

**Files:**
- Modify: `platform-web/.../web/WebTest.kt`
- Modify: all test files that construct `SecurityService` directly (~15 files)

The test surface is the seam. `WebTest.createSecurityService()` is the factory used by most tests. It must be replaced with direct sub-service construction.

- [ ] **Step 1: Update WebTest.kt**

1. Delete `createSecurityService()` method.
2. Add lazy fields for the sub-services:

```kotlin
val apiKeyService by lazy { ApiKeyService(userRepository, apiKeyRepository, auditRepository) }
val passwordResetService by lazy {
    PasswordResetService(userRepository, encoder, passwordResetRepository, auditRepository, sessionRepository)
}
val oauthService by lazy { OAuthService(userRepository, encoder, oauthRepository, auditRepository) }
```

3. Update `buildApp()` signature — replace `securityService: SecurityService = createSecurityService()` with `apiKeyService: ApiKeyService = this.apiKeyService`, `passwordResetService: PasswordResetService = this.passwordResetService`, `oauthService: OAuthService = this.oauthService`.
4. Update the `app()` call inside `buildApp()` to pass the sub-services instead of `securityService`.

- [ ] **Step 2: Update all test files that construct SecurityService directly**

Search for `SecurityService(` in `platform-web/src/test/`. Each test file constructs SecurityService with varying parameters. Replace each with the sub-services it actually needs:

- Files that only call `createSecurityService()` — already handled by WebTest update
- Files that construct `SecurityService(userRepository, encoder, ...)` directly — replace with sub-service construction
- Files that use `mockk<SecurityService>(relaxed = true)` — replace with mocks of the specific sub-service(s) needed

The exact list of files and their required changes:

| Test file | Pattern | Fix |
|---|---|---|
| `OAuthIntegrationTest.kt` | Creates `SecurityService` directly | Create `OAuthService` directly |
| `SecurityIntegrationTest.kt` | Creates `SecurityService` directly | Create `AuthService` directly |
| `ChangePasswordWebIntegrationTest.kt` | Uses `createSecurityService()` | Handled by WebTest update |
| `SyncIntegrationTest.kt` | `SecurityService(userRepository, encoder, ...)` | Remove if not used for sync |
| `DeviceRegistrationApiIntegrationTest.kt` | `SecurityService(userRepository, encoder, ...)` | Remove if not used |
| `ApiKeyLifecycleIntegrationTest.kt` | Creates `SecurityService` directly | Create `ApiKeyService` directly |
| `AuditLogIntegrationTest.kt` | Creates `SecurityService` directly | Create `ApiKeyService` directly |
| `ApiKeyAuthIntegrationTest.kt` | Creates `SecurityService` directly | Create `ApiKeyService` directly |
| `PlatformAppTest.kt` | `mockk<SecurityService>(relaxed = true)` | Mock the specific sub-services needed |
| All `WebTest` subclasses | Use `buildApp()` | Handled by WebTest update |

- [ ] **Step 3: Compile**

Run: `mvn -pl platform-web -am compile -Dexec.skip=true "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: PASS

- [ ] **Step 4: Run full web test suite**

Run: `mvn -pl platform-web test -Dexec.skip=true`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(web): update all tests to use sub-services directly"
```

---

### Task 8: Full verification and cleanup

**Files:**
- Potentially all modules

- [ ] **Step 1: Full reactor build (excluding desktop)**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-testkit,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`
Expected: PASS (all tests, all quality checks)

- [ ] **Step 2: Search for any remaining SecurityService references**

Run: `rg "SecurityService" --include="*.kt" --include="*.java" .`
Expected: No matches anywhere outside git history

- [ ] **Step 3: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: cleanup after SecurityService decomposition"
```
