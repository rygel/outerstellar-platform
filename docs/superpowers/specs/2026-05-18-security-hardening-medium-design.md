# Security Hardening: Session TTL, Registration Control, Password Policy

**Date:** 2026-05-18  
**Status:** Approved  
**PR target:** `develop`

## Overview

Address three MEDIUM-severity findings from the security audit:
1. Sessions can slide forever (no absolute maximum lifetime)
2. Registration is completely open with no disable toggle
3. Password validation only checks length (8-128 chars), no complexity requirements

## 1. Session Absolute Maximum Lifetime

### Problem

Sessions use a 30-minute sliding window with renewal on every request. A user who remains active (hits the server every 29 minutes) never expires. This violates security best practices requiring an absolute session lifetime cap.

### Design

**Config:** Add `sessionAbsoluteTimeoutMinutes` to `AppConfig` (default: `1440` = 24 hours). Env var: `SESSION_ABSOLUTE_TIMEOUT_MINUTES`. YAML key: `sessionAbsoluteTimeoutMinutes`.

**Schema change:** Flyway migration adds `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` to `plt_sessions`. The `created_at` is set once at session creation and never modified.

**Logic change in `SecurityService.lookupSession()`:**
- After fetching the session and confirming it exists, check: `now - session.createdAt > absoluteTimeout`
- If exceeded: delete the session, return `Expired`
- Otherwise: proceed with existing sliding window logic (extend `expiresAt` by `sessionTimeoutSeconds`)
- The absolute timeout is a hard ceiling; the sliding timeout operates within it

**Session creation (`SecurityService.createSession()`):**
- Store `createdAt = now` alongside existing fields
- `expiresAt` is still set to `now + sessionTimeoutSeconds` (sliding window initial value)

**jOOQ regen:** After the migration, regenerate jOOQ sources (`generate-jooq.ps1`). The `SessionsRecord` will pick up the new column.

**SessionRepository:** Update `save()` to accept `createdAt`. Update `Session` data class to include `createdAt: Instant`.

**Cleanup:** `deleteExpiredSessions()` already runs periodically. No changes needed there — expired sessions are already cleaned by `expires_at` index. The absolute timeout causes sessions to be marked expired during lookup, then cleaned up normally.

**Config reference update (AGENTS.md):**

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `sessionAbsoluteTimeoutMinutes` | `SESSION_ABSOLUTE_TIMEOUT_MINUTES` | 1440 | Absolute max session lifetime (cannot be extended by sliding) |

### Files changed

- `platform-core/.../AppConfig.kt` — add field
- `platform-security/.../Models.kt` — `Session` data class add `createdAt`
- `platform-security/.../SecurityService.kt` — `createSession()`, `lookupSession()`
- `platform-security/.../SecurityModule.kt` — wire new config
- `platform-persistence-jooq/.../V{n}__sessions_created_at.sql` — migration
- `platform-persistence-jooq/.../JooqSessionRepository.kt` — update save/find queries
- `platform-persistence-jdbi/.../JdbiSessionRepository.kt` — update save/find queries (if exists)
- jOOQ generated sources (regenerated)

## 2. Registration Toggle

### Problem

Registration is always open. There is no config toggle to disable it. Any HTTP client can POST to registration endpoints and create accounts.

### Design

**Config:** Add `registrationEnabled` to `AppConfig` (default: `true` for backward compatibility). Env var: `REGISTRATION_ENABLED`. YAML key: `registrationEnabled`.

**API behavior when disabled:**
- `POST /api/v1/auth/register` → 403 Forbidden, JSON body: `{"error": "Registration is disabled"}`
- No session is created, no user is created

**Web behavior when disabled:**
- `POST /auth/components/result` (mode=register) → render error message on auth page: "Registration is currently disabled"
- The auth page should conditionally hide the registration form/tab when disabled (via `AuthPageFactory` or JTE template context)

**Admin user creation is unaffected:**
- Admin endpoints for user management (`POST /api/v1/admin/users`) continue to work
- This is controlled by the `registrationEnabled` flag, not by admin-only routes

**Implementation approach:**
- Check the flag early in both `AuthApi.register()` and `AuthRoutes` register handler
- Pass `registrationEnabled` to the auth page template context so the UI can hide the registration form

### Files changed

- `platform-core/.../AppConfig.kt` — add field
- `platform-web/.../AuthApi.kt` — guard registration route
- `platform-web/.../AuthRoutes.kt` — guard registration handler
- `platform-web/.../AuthPageFactory.kt` (or auth JTE template) — pass flag to UI

## 3. Password Complexity Policy

### Problem

`validatePassword()` in `PasswordValidation.kt` only checks length (8-128 chars). No complexity requirements exist. Passwords like `aaaaaaaa` or `password` pass validation.

### Design

Enhance `validatePassword()` to enforce:

| Rule | Requirement | Error message |
|---|---|---|
| Minimum length | 8 characters (unchanged) | "Password must be at least 8 characters" |
| Maximum length | 128 characters (unchanged) | "Password must be at most 128 characters" |
| Uppercase | At least 1 | "Password must contain at least one uppercase letter" |
| Lowercase | At least 1 | "Password must contain at least one lowercase letter" |
| Digit | At least 1 | "Password must contain at least one digit" |
| Special character | At least 1 | "Password must contain at least one special character" |

Special characters defined as any character that is not a letter or digit (i.e., `!char.isLetterOrDigit()`).

**Validation returns the first failing rule's message.** All existing callers already handle the error message via `WeakPasswordException`. No changes to callers needed.

**UI hint:** The registration form and password change/reset forms should display the password requirements as helper text. This is a template change in the relevant JTE templates.

### Files changed

- `platform-security/.../PasswordValidation.kt` — enhance validation rules
- JTE templates for registration/password forms — add requirement hints
- Existing tests for `PasswordValidation` — update to cover new rules

## Non-goals

- **Invite code system** — deferred; config toggle is sufficient for now
- **CAPTCHA** — out of scope for this change
- **Email verification** — out of scope; would require email infrastructure changes
- **Common password blocklist** — deferred; complexity rules provide adequate protection
- **Username similarity check** — deferred; low priority given complexity rules
- **JWT TTL changes** — JWT is disabled by default and has its own expiry config

## Testing

- Unit tests for `validatePassword()` covering each new rule and valid passwords
- Unit tests for registration guard (enabled/disabled) in `AuthApi` and `AuthRoutes`
- Integration test verifying session absolute timeout enforcement
- Integration test verifying registration returns 403 when disabled
- Flyway migration tested via existing migration test infrastructure
