# Account Lockout Design

## Overview

Add per-account brute-force lockout protection with automatic time-based unlock and manual admin override.

## Design

### Approach

**B — Database columns on User model** (chosen over in-memory cache or separate table). Persistent across restarts, scales horizontally, admin-visible.

### Database Migration

New Flyway migration `V13__add_account_lockout.sql`:

```sql
ALTER TABLE plt_users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plt_users ADD COLUMN locked_until TIMESTAMPTZ;
```

### User Model

Two new fields on existing `User` data class (`platform-security/.../security/Models.kt`):

```kotlin
val failedLoginAttempts: Int = 0,
val lockedUntil: Instant? = null,
```

### Repository

Three new methods on `UserRepository` interface, implemented in both `JooqUserRepository` and `JdbiUserRepository`:

- `incrementFailedLoginAttempts(userId: UUID): Int` — atomic increment, returns new count
- `resetFailedLoginAttempts(userId: UUID)` — reset to 0
- `updateLockedUntil(userId: UUID, lockedUntil: Instant?)` — set or clear lock

### Authentication Flow

In `SecurityService.authenticate()`:

1. Existing checks (user exists, user enabled) remain unchanged
2. New check: if `user.lockedUntil` is in the future, reject with `"Account is temporarily locked"`
3. On successful auth: reset `failedLoginAttempts` to 0, clear `lockedUntil`
4. On failed auth: increment `failedLoginAttempts`. If count >= threshold, set `lockedUntil = now + duration`

### Admin Unlock

New method `SecurityService.unlockAccount(adminId, targetId)`:
- Verifies admin role
- Clears `lockedUntil` and resets `failedLoginAttempts` to 0
- Logs audit event `USER_UNLOCKED`

New route `POST /admin/users/{id}/unlock` with unlock button in admin user list UI.

### Configuration

Two new fields in `AppConfig`:

| Field | Env Var | Default | Description |
|-------|---------|---------|-------------|
| `lockoutThreshold` | `LOCKOUT_THRESHOLD` | `10` | Failed attempts before lockout |
| `lockoutDurationSeconds` | `LOCKOUTDURATIONSECONDS` | `900` | Lockout duration in seconds (15 min) |

### Testing

- Unit tests for `SecurityService.authenticate()` lockout logic
- Unit test for `unlockAccount()` permission check
- Integration test: login fail N times -> locked -> login returns null -> wait (mock time) -> login succeeds
- Integration test: admin unlock restores access

### Files Changed

- `platform-core/.../AppConfig.kt` — add lockout config fields
- `platform-security/.../Models.kt` — add fields to User
- `platform-security/.../Models.kt` — add methods to UserRepository
- `platform-security/.../SecurityService.kt` — add lockout logic + unlockAccount
- `platform-persistence-jooq/.../migrations/V13__add_account_lockout.sql` — new migration
- `platform-persistence-jooq/.../JooqUserRepository.kt` — implement new methods
- `platform-persistence-jdbi/.../JdbiUserRepository.kt` — implement new methods
- `platform-web/.../web/UserAdminRoutes.kt` — add unlock route + admin UI button
- `platform-web/.../web/ViewModels.kt` — add locked status to user list view model
- Test files
