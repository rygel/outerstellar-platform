# SecurityService Decomposition Design

**Date:** 2026-05-24
**Status:** Approved

## Problem

`SecurityService` is a 452-line god class with 31 public methods spanning 10 distinct responsibilities. It mixes session management, authentication, user administration, TOTP, password management, and thin delegations to already-extracted services. Every caller depends on the concrete class — no interfaces exist.

## Scope

This PR extracts 3 services from SecurityService. A future PR will extract TOTP/2FA and remove the thin delegation methods.

## Extracted Services

### 1. `SessionService`

**Package:** `io.github.rygel.outerstellar.platform.security`

```kotlin
class SessionService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val config: SecurityConfig,
)
```

Methods extracted from SecurityService:
- `createSession(userId: UUID): String` — token generation, hashing, persistence
- `lookupSession(rawToken: String): SessionLookup` — lookup with sliding/absolute timeout, user validation
- `deleteSession(rawToken: String): Unit` — delete by token hash

Removed (dead code):
- `deleteExpiredSessions()` — never called by any production code

Callers to update:
- `WebContext` (lookupSession)
- `SyncWebSocket` (lookupSession)
- `SessionRealm` (lookupSession)
- `Filters.kt` (createSession for dev auto-login)
- `App.kt` (deleteSession for logout)
- `AuthRoutes` (createSession)
- `AuthApi` (createSession, deleteSession)

### 2. `AuthService`

**Package:** `io.github.rygel.outerstellar.platform.security`

```kotlin
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val config: SecurityConfig,
)
```

Methods extracted from SecurityService:
- `authenticate(username: String, password: String): AuthResult?` — password verification, lockout, TOTP detection
- `register(username: String, password: String): User` — registration with validation

Callers to update:
- `AuthRoutes` (authenticate, register)
- `AuthApi` (authenticate, register)
- `TOTPRoutes` (authenticate)
- `TOTPApiRoutes` (authenticate)

### 3. `UserAdminService`

**Package:** `io.github.rygel.outerstellar.platform.security`

```kotlin
class UserAdminService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
)
```

Methods extracted from SecurityService:
- `listUsers(): List<UserSummary>`
- `listUsers(limit: Int, offset: Int): List<UserSummary>`
- `countUsers(): Long`
- `findUserSummary(id: UUID): UserSummary?`
- `setUserEnabled(adminId: UUID, targetId: UUID, enabled: Boolean): Unit`
- `unlockAccount(adminId: UUID, targetId: UUID): Unit`
- `setUserRole(adminId: UUID, targetId: UUID, role: UserRole): Unit`
- `countAuditEntries(): Long`
- `getAuditLog(limit: Int = 50): List<AuditEntry>`
- `getAuditLog(limit: Int, offset: Int): List<AuditEntry>`

Callers to update:
- `UserAdminRoutes` (listUsers, findUserSummary, setUserEnabled, getAuditLog, setUserRole, unlockAccount)
- `UserAdminApi` (listUsers, setUserEnabled, setUserRole)
- `AdminPageFactory` (countUsers, listUsers, countAuditEntries, getAuditLog, listApiKeys — listApiKeys stays on SecurityService)

## What Stays in SecurityService

After extraction, SecurityService retains:
- `changePassword(userId, currentPassword, newPassword)` — password management
- `updateProfile(userId, newEmail, newUsername, newAvatarUrl)` — profile updates
- `updateNotificationPreferences(userId, emailEnabled, pushEnabled)` — notification prefs
- `deleteAccount(userId, currentPassword)` — account deletion
- `verifyTotp(partialToken, code)`, `enableTotp(userId, secret, backupCodes)`, `disableTotp(userId)` — TOTP/2FA (future extraction)
- Delegation methods: `createApiKey`, `authenticateApiKey`, `listApiKeys`, `deleteApiKey`, `requestPasswordReset`, `resetPassword`, `findOrCreateOAuthUser` (future removal)
- `audit()`, `sanitize()` helpers (shared utility, future extraction)

SecurityService gains a `SessionService` dependency for TOTP flow (which calls `createSession` on successful verification).

## Dead Code Removed

- `SecurityService.deleteExpiredSessions()` — never called
- `SecurityService.updatePreferences()` — never called (callers use `userRepository.updatePreferences()` directly)

## Wiring

`SecurityComponents` constructs all three new services and passes them to consumers:
- `SecurityService` gets `SessionService` as a new dependency
- `SessionRealm` takes `SessionService` instead of `SecurityService`
- `ApiKeyRealm` stays unchanged (uses `SecurityService.authenticateApiKey`)
- Routes take `AuthService`, `UserAdminService`, `SessionService` as additional constructor parameters
- `WebTest.createSecurityService()` updated to match new constructor

## Plugin SPI

`PlatformPlugin` holds `securityService: SecurityService` — unchanged. New services are internal to the platform, not exposed to plugins.

## Internal Helpers

The `toSummary()` extension on `User` moves to `UserAdminService` (only user admin uses it). The `audit()` and `sanitize()` helpers stay in SecurityService for now — they're shared with the remaining methods and will be extracted to a utility in a future PR.

## Duplicate SecureRandom

`SecurityService` has both `secureRandom` and `random` fields doing the same thing. Consolidate to a single `secureRandom` field. `SessionService` gets its own `SecureRandom` for token generation.

## Testing

- Existing tests continue to work — they create `SecurityService` via `WebTest.createSecurityService()` which will be updated
- No new tests needed — the extracted methods keep their existing behavior, and existing integration tests cover them
- `WebTest` gains `SessionService`, `AuthService`, `UserAdminService` helpers for tests that need them directly

## Out of Scope (Future PRs)

- Extract `TotpService` from SecurityService
- Remove delegation methods (callers use `ApiKeyService`, `PasswordResetService`, `OAuthService` directly)
- Extract `audit()` and `sanitize()` to shared utilities
- Extract interfaces for testability
- Move `OAuthRepository` to persistence module
