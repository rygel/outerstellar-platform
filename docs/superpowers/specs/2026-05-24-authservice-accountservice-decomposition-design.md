# SecurityService Decomposition: AuthService + AccountService

## Goal

Extract `AuthService` (authentication + TOTP flow) and `AccountService` (self-service account management) from `SecurityService`. SecurityService retains API key, OAuth, and password reset delegation.

## Architecture

Three services replace the current monolithic `SecurityService`:

**AuthService** — all login/registration flow including TOTP:
- `authenticate(username, password): AuthResult?` — core auth with lockout, TOTP partial auth
- `register(username, password): User` — user registration
- `verifyTotp(partialToken, code, sessionService): TotpVerifyResponse` — TOTP second factor
- `enableTotp(userId, secret, backupCodes)` — enable TOTP
- `disableTotp(userId)` — disable TOTP
- Owns `partialAuthStore` (Caffeine cache), `totpService` (TOTPService), `secureRandom`
- Dependencies: `UserRepository`, `PasswordEncoder`, `SecurityConfig`, `AuditRepository?`
- Also owns `sanitize()`, `PartialAuth` data class, `MAX_LOG_ID_LENGTH`

**AccountService** — self-service account management:
- `changePassword(userId, currentPassword, newPassword)` — password change with session invalidation
- `updateProfile(userId, email, username?, avatarUrl?)` — profile update with validation
- `updateNotificationPreferences(userId, emailEnabled, pushEnabled)` — notification toggle
- `deleteAccount(userId, currentPassword)` — account deletion with admin guard
- Dependencies: `UserRepository`, `PasswordEncoder`, `SessionRepository?`, `AuditRepository?`
- Owns `EMAIL_REGEX`, `MAX_USERNAME_LENGTH`, `sanitize()`

**SecurityService** — shrinks to delegation layer:
- `createApiKey`, `authenticateApiKey`, `listApiKeys`, `deleteApiKey` — delegates to `ApiKeyService`
- `findOrCreateOAuthUser` — delegates to `OAuthService`
- `requestPasswordReset`, `resetPassword` — delegates to `PasswordResetService`
- Internal lazy services: `PasswordResetService`, `ApiKeyService`, `OAuthService`
- Dependencies: `UserRepository`, `PasswordEncoder`, `AuditRepository?`, `PasswordResetRepository?`, `ApiKeyRepository?`, `OAuthRepository?`, `EmailService?`, `SecurityConfig`, `SessionRepository?`

## Wiring

`SecurityComponents` creates all three and exposes them:
```kotlin
class SecurityComponents(
    val passwordEncoder: PasswordEncoder,
    val jwtService: JwtService,
    val asyncActivityUpdater: AsyncActivityUpdater,
    val securityService: SecurityService,    // API keys, OAuth, password reset
    val authService: AuthService,             // NEW
    val accountService: AccountService,       // NEW
    val permissionResolver: PermissionResolver,
    val authRealms: List<AuthRealm>,
    val totpService: TOTPService,
    val sessionService: SessionService,
    val userAdminService: UserAdminService,
)
```

`authRealms` changes from `listOf(SessionRealm(sessionService), ApiKeyRealm(securityService))` to `listOf(SessionRealm(sessionService), ApiKeyRealm(securityService))` — no change, since ApiKeyRealm still uses SecurityService for `authenticateApiKey`.

## Caller Migration

### platform-web route files

| Route file | Current calls | New target |
|-----------|--------------|------------|
| `AuthRoutes` | `securityService.authenticate`, `securityService.register` | `authService.authenticate`, `authService.register` |
| `AuthApi` | `securityService.authenticate`, `securityService.register`, `securityService.changePassword`, `securityService.requestPasswordReset`, `securityService.resetPassword`, `securityService.deleteSession`, `securityService.deleteApiKey` | `authService` for auth, `accountService` for password, `securityService` for password reset + API keys |
| `OAuthRoutes` | `securityService.findOrCreateOAuthUser` | `securityService.findOrCreateOAuthUser` (no change) |
| `TOTPRoutes` | `securityService.verifyTotp`, `securityService.enableTotp`, `securityService.disableTotp` | `authService.verifyTotp`, `authService.enableTotp`, `authService.disableTotp` |
| `TOTPApiRoutes` | `securityService.verifyTotp`, `securityService.enableTotp`, `securityService.disableTotp` | `authService.verifyTotp`, `authService.enableTotp`, `authService.disableTotp` |
| `AuthRoutes` | `securityService.changePassword` | `accountService.changePassword` |
| `AuthRoutes` | `securityService.updateProfile` | `accountService.updateProfile` |
| `AuthRoutes` | `securityService.updateNotificationPreferences` | `accountService.updateNotificationPreferences` |
| `AuthRoutes` | `securityService.deleteAccount` | `accountService.deleteAccount` |
| `AuthRoutes` | `securityService.createApiKey`, `securityService.deleteApiKey` | `securityService` (no change) |

### Filters.kt
- `devAutoLogin` — already uses `sessionService.createSession`, no `securityService` calls remaining after previous refactor

### App.kt
- Logout route — already uses `ctx.sessionService.deleteSession`, no change
- `AppContext` — add `authService` and `accountService` fields
- Route constructors — pass appropriate services to each route

## File locations

| File | Location |
|------|----------|
| `AuthService.kt` | `platform-security/src/main/kotlin/.../security/AuthService.kt` |
| `AccountService.kt` | `platform-security/src/main/kotlin/.../security/AccountService.kt` |
| `SecurityService.kt` | Same location, shrunk to ~100 lines |
| `SecurityComponents.kt` | Same location, wires new services |

## Testing

- Existing `SecurityServiceTest` tests migrate to `AuthServiceTest` or `AccountServiceTest` based on which method they test
- `SecurityServiceTotpTest` migrates to `AuthServiceTotpTest`
- No new tests needed — existing tests cover all behavior
