# TOTP Two-Factor Authentication Design

Date: 2026-05-15
Status: Approved

## Scope

Add optional TOTP two-factor authentication to the web application. Desktop clients are out of scope for the initial implementation.

## Architecture

### Design Decision: Two-Step Login with Partial-Auth Token

After password verification, if TOTP is enabled, issue a short-lived partial-auth token instead of a full session. The client exchanges this token (+ TOTP code) for a full session via a dedicated endpoint.

### Library: `dev.samstevens.totp:totp` v1.7.1

Provides secret generation, QR code generation (via ZXing), code verification, and recovery codes. Maven dependency added to `platform-security`.

## Database

### Flyway Migration V9

```sql
ALTER TABLE plt_users ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE plt_users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE plt_users ADD COLUMN totp_backup_codes TEXT;
```

Regenerate jOOQ sources after migration.

### User Model Changes

Add to `User` data class in `platform-security/.../security/Models.kt`:
- `totpSecret: String? = null`
- `totpEnabled: Boolean = false`
- `totpBackupCodes: String? = null`

### UserRepository Changes

Add to `UserRepository` interface:
- `fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>?` — returns (secret, enabled, backupCodes)
- `fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?)`
- `fun enableTotp(userId: UUID)`

Implement in `JooqUserRepository`, `JdbiUserRepository`, and `CachingUserRepository`.

## TOTPService

New class in `platform-security/.../security/TOTPService.kt`:

```kotlin
class TOTPService {
    fun generateSecret(): String
    fun generateQrDataUri(secret: String, email: String): String
    fun verifyCode(secret: String, code: String): Boolean
    fun generateBackupCodes(): Pair<List<String>, String>
    fun verifyBackupCode(code: String, hashedCodes: String): String?
}
```

- `generateSecret()` — `DefaultSecretGenerator(32)`
- `generateQrDataUri()` — `QrData.Builder(label=email, secret, issuer="Outerstellar")` + `ZxingPngQrGenerator` + `Utils.getDataUriForImage`
- `verifyCode()` — `DefaultCodeVerifier(SystemTimeProvider)` with 30s period, 1 discrepancy
- `generateBackupCodes()` — `RecoveryCodeGenerator.generateCodes(16)` + SHA-256 hash each, return (raw, JSON-of-hashes)
- `verifyBackupCode()` — hash input, compare against stored hashes, return updated JSON or null

Registered in `SecurityModule` as `single { TOTPService() }`.

## Partial-Auth Token System

### PartialAuth Store

In-memory `ConcurrentHashMap<UUID, PartialAuth>` in `SecurityService`:

```kotlin
data class PartialAuth(
    val userId: UUID,
    val createdAt: Instant,
    val attemptCount: Int = 0
)
```

- Token format: `pt_` + 32 random hex chars
- TTL: 5 minutes, checked on verification
- Max attempts: 5, then token invalidated
- Cleanup: checked on verification + periodic sweep

### SecurityService Changes

Modify `authenticate(username, password)`:
```kotlin
val user = userRepository.findByUsername(username)
// ... existing password check, lockout check ...
if (user.totpEnabled) {
    val partialToken = generatePartialAuthToken(user.id)
    return AuthResult.TotpRequired(token = partialToken)
}
// ... existing session creation ...
```

New method `verifyTotp(partialToken: String, code: String): Result<AuthResult>`:
1. Look up partial token
2. Load user's TOTP secret
3. Verify code
4. On success: create session, return `AuthResult.Authenticated(user, session)`
5. On failure: try backup codes, increment attempt counter

### AuthResult Changes

Add to `sealed class AuthResult`:
```kotlin
data class TotpRequired(val token: String) : AuthResult()
```

## API Endpoints

### Web UI Routes (HTMX)

| Method | Route | Purpose |
|--------|-------|---------|
| POST | `/auth/components/result` | Existing login. If TOTP required, return HTMX fragment with totp form |
| POST | `/auth/components/totp-verify` | Submit TOTP code + partial token from hidden field |
| POST | `/auth/components/totp-setup` | Enable TOTP — generate secret, return QR fragment |
| POST | `/auth/components/totp-verify-setup` | Verify code during setup, persist secret |
| POST | `/auth/components/totp-disable` | Disable TOTP with password confirmation |

### API Routes (JSON)

| Method | Route | Purpose |
|--------|-------|---------|
| POST | `/api/v1/auth/totp/verify` | Verify TOTP code with partial token |
| POST | `/api/v1/auth/totp/setup` | Generate secret for setup |
| POST | `/api/v1/auth/totp/confirm` | Confirm setup with verification code |
| POST | `/api/v1/auth/totp/disable` | Disable TOTP |

### LoginResponse Model

Extend login response for TotpRequired case:
```kotlin
data class LoginResponse(
    val status: String,  // "success", "totp_required", "error"
    val token: String? = null,
    val username: String? = null,
    val role: String? = null,
    val partialToken: String? = null,
    val error: String? = null,
)
```

## Settings: Security Tab

Add `"security"` tab to `SettingsPageFactory.kt` tab list between `"password"` and `"api-keys"`.

### Tab Content

**When TOTP is disabled:**
- "Enable Two-Factor Authentication" button
- Brief explanation

**When TOTP is enabled:**
- Status badge: "Two-factor authentication is enabled"
- "Disable" button (with password confirmation dialog)
- "Regenerate backup codes" button
- Backup codes count remaining

### Setup Dialog (HTMX fragment):

1. User clicks "Enable" → server generates secret + QR → returns fragment with:
   - QR code image (data URI)
   - Manual secret text for manual entry
   - "Enter the 6-digit code from your authenticator app" input field
2. User enters code → server verifies → if valid:
   - Persists secret
   - Returns backup codes display
   - Sets `totpEnabled = true`

### SettingsPage ViewModel

Add fields:
- `totpEnabled: Boolean`
- `totpQrDataUri: String?` (for setup)
- `totpSecret: String?` (for manual entry)
- `totpBackupCodes: List<String>?` (shown once after setup)
- `totpRemainingBackupCodes: Int`

## Password Reset Behavior

- Password reset preserves TOTP secret
- User keeps their TOTP enrollment after password reset
- Backup codes are NOT preserved (regenerated if user wants them)
- Rationale: password reset doesn't mean device compromise

## OAuth Interaction

- OAuth logins do NOT require TOTP
- Rationale: OAuth provider already performed authentication
- Configurable via `AppConfig.totpRequireOAuth` (default: false)

## Error Handling

- Rate limit: 5 TOTP attempts per partial token, then invalidate
- Separate rate limit: 10 failed TOTP attempts per user per 15 minutes (reuse existing RateLimiter)
- Invalid code: return 401 with "Invalid verification code"
- Expired partial token: return 401 with "Verification session expired, please log in again"
- Setup verification: 3 attempts, then restart setup

## Testing

| Test | Coverage |
|------|----------|
| `TOTPServiceTest` | Secret generation, QR data URI, code verification, backup code generation/verification |
| `SecurityServiceTotpTest` | Login with TOTP enabled/disabled, partial auth token flow, session creation after TOTP, invalid code, expired token, rate limiting |
| `AuthRoutesTotpTest` | HTMX form flow, TOTP setup, TOTP disable |
| `AuthApiTotpTest` | API endpoint verification |
| `SettingsTotpTest` | Security tab rendering, setup flow |

## Out of Scope (Initial)

- Desktop client TOTP support (Swing, JavaFX)
- TOTP via email/SMS (TOTP means authenticator apps)
- WebAuthn/FIDO2
- Remember-this-device cookies

## Migration Path

### Phase 1: Core Infrastructure
- Flyway migration V9
- User model + repository changes
- TOTPService
- Partial-auth token system

### Phase 2: Login Flow
- Modify SecurityService.authenticate()
- TOTP verification endpoint
- HTMX login form changes

### Phase 3: Settings
- Security tab in SettingsPageFactory
- Setup/disable flows
- Backup code display

### Phase 4: API + Testing
- JSON API endpoints
- All tests
- Rate limiting

### Phase 5: Password Reset + Polish
- Verify password reset preserves TOTP
- Error handling edge cases
- OAuth TOTP bypass
