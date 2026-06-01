# Move User and UserRepository to platform-core

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move User, UserRepository, LockoutRepository, Session, SessionRepository, SessionLookup, DeviceToken, DeviceTokenRepository, PasswordResetRepository, and ApiKeyRepository from platform-security's Models.kt to platform-core, so that domain types live in the domain module and persistence modules no longer depend on the security module.

**Architecture:** Pure move refactor — no logic changes. Types get new packages in platform-core. All import statements across the codebase get updated. Models.kt in platform-security retains only SecurityConfig. Every file's compilation is verified after each task.

**Tech Stack:** Kotlin, Maven multi-module, Koin DI

---

## What moves and where

| Type | From | To |
|------|------|----|
| `User` | `io.github.rygel.outerstellar.platform.security.User` | `io.github.rygel.outerstellar.platform.model.User` |
| `LockoutRepository` | `io.github.rygel.outerstellar.platform.security.LockoutRepository` | `io.github.rygel.outerstellar.platform.persistence.LockoutRepository` |
| `UserRepository` | `io.github.rygel.outerstellar.platform.security.UserRepository` | `io.github.rygel.outerstellar.platform.persistence.UserRepository` |
| `Session` | `io.github.rygel.outerstellar.platform.security.Session` | `io.github.rygel.outerstellar.platform.model.Session` |
| `SessionRepository` | `io.github.rygel.outerstellar.platform.security.SessionRepository` | `io.github.rygel.outerstellar.platform.persistence.SessionRepository` |
| `SessionLookup` | `io.github.rygel.outerstellar.platform.security.SessionLookup` | `io.github.rygel.outerstellar.platform.model.SessionLookup` |
| `DeviceToken` | `io.github.rygel.outerstellar.platform.security.DeviceToken` | `io.github.rygel.outerstellar.platform.model.DeviceToken` |
| `DeviceTokenRepository` | `io.github.rygel.outerstellar.platform.security.DeviceTokenRepository` | `io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository` |
| `PasswordResetRepository` | `io.github.rygel.outerstellar.platform.security.PasswordResetRepository` | `io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository` |
| `ApiKeyRepository` | `io.github.rygel.outerstellar.platform.security.ApiKeyRepository` | `io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository` |

**Stays in platform-security:** `SecurityConfig` (only used within security module).

---

## Task 1: Create User.kt in platform-core model package

**Files:**
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/User.kt`

- [ ] **Step 1: Create User.kt**

```kotlin
package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val enabled: Boolean = true,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Instant? = null,
    val lastActivityAt: Instant? = null,
    val avatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val language: String? = null,
    val theme: String? = null,
    val layout: String? = null,
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val totpBackupCodes: String? = null,
)
```

- [ ] **Step 2: Create Session.kt, SessionLookup.kt, DeviceToken.kt**

`platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/Session.kt`:
```kotlin
package io.github.rygel.outerstellar.platform.model

import java.time.Instant
import java.util.UUID

data class Session(
    val id: Long = 0,
    val tokenHash: String,
    val userId: UUID,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant,
)
```

`platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/SessionLookup.kt`:
```kotlin
package io.github.rygel.outerstellar.platform.model

sealed class SessionLookup {
    data class Active(val user: User) : SessionLookup()

    data object Expired : SessionLookup()

    data object NotFound : SessionLookup()
}
```

`platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/DeviceToken.kt`:
```kotlin
package io.github.rygel.outerstellar.platform.model

import java.util.UUID

data class DeviceToken(val id: Long, val userId: UUID, val platform: String, val token: String, val appBundle: String?)
```

- [ ] **Step 3: Compile platform-core**

Run: `mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 2: Create repository interfaces in platform-core persistence package

**Files:**
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/LockoutRepository.kt`
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/UserRepository.kt`
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/SessionRepository.kt`
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/DeviceTokenRepository.kt`
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/PasswordResetRepository.kt`
- Create: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/ApiKeyRepository.kt`

- [ ] **Step 1: Create LockoutRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.util.UUID

interface LockoutRepository {
    fun incrementFailedLoginAttempts(userId: UUID): Int

    fun resetFailedLoginAttempts(userId: UUID)

    fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)
}
```

- [ ] **Step 2: Create UserRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.time.LocalDateTime
import java.util.UUID

interface UserRepository : LockoutRepository {
    fun findById(id: UUID): User?

    fun findByUsername(username: String): User?

    fun findByEmail(email: String): User?

    fun save(user: User)

    fun seedAdminUser(passwordHash: String)

    fun findAll(): List<User>

    fun findPage(limit: Int, offset: Int): List<User>

    fun countAll(): Long

    fun countByRole(role: UserRole): Long

    fun updateRole(userId: UUID, role: UserRole)

    fun updateEnabled(userId: UUID, enabled: Boolean)

    fun updateLastActivity(userId: UUID)

    fun deleteById(userId: UUID)

    fun updateUsername(userId: UUID, newUsername: String)

    fun updateAvatarUrl(userId: UUID, avatarUrl: String?)

    fun updateNotificationPreferences(userId: UUID, emailEnabled: Boolean, pushEnabled: Boolean)

    fun updatePreferences(userId: UUID, language: String?, theme: String?, layout: String?)

    fun countUsersSince(cutoff: LocalDateTime): Long

    fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>?

    fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?)

    fun enableTotp(userId: UUID)

    fun disableTotp(userId: UUID)
}
```

- [ ] **Step 3: Create SessionRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.Session
import java.time.Instant
import java.util.UUID

interface SessionRepository {
    fun save(session: Session)

    fun findByTokenHash(tokenHash: String): Session?

    fun findByTokenHashIncludingExpired(tokenHash: String): Session?

    fun updateExpiresAt(tokenHash: String, expiresAt: Instant)

    fun deleteByTokenHash(tokenHash: String)

    fun deleteByUserId(userId: UUID)

    fun deleteExpired()
}
```

- [ ] **Step 4: Create DeviceTokenRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.DeviceToken
import java.util.UUID

interface DeviceTokenRepository {
    fun upsert(deviceToken: DeviceToken)

    fun delete(token: String)

    fun deleteByTokenAndUserId(token: String, userId: UUID): Boolean

    fun findByUserId(userId: UUID): List<DeviceToken>

    fun deleteAllForUser(userId: UUID)
}
```

- [ ] **Step 5: Create PasswordResetRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.PasswordResetToken

interface PasswordResetRepository {
    fun save(token: PasswordResetToken)

    fun findByToken(token: String): PasswordResetToken?

    fun markUsed(token: String)
}
```

Note: `PasswordResetToken` is already in platform-core `AuthModels.kt`. No FQN needed now.

- [ ] **Step 6: Create ApiKeyRepository.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ApiKey
import java.util.UUID

interface ApiKeyRepository {
    fun save(apiKey: ApiKey)

    fun findByKeyHash(keyHash: String): ApiKey?

    fun findByUserId(userId: UUID): List<ApiKey>

    fun delete(id: Long, userId: UUID)

    fun updateLastUsed(id: Long)
}
```

Note: `ApiKey` is already in platform-core `AuthModels.kt`. No FQN needed now.

- [ ] **Step 7: Compile platform-core**

Run: `mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 3: Add type aliases in platform-security for backward compatibility during migration

**Files:**
- Modify: `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt`

This task replaces Models.kt with type aliases so that platform-security's own code and any lingering old imports still compile. This is a temporary bridge — once all consumers are updated (Tasks 4-7), the aliases can be removed.

- [ ] **Step 1: Replace Models.kt with type aliases**

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.DeviceToken
import io.github.rygel.outerstellar.platform.model.Session
import io.github.rygel.outerstellar.platform.model.SessionLookup
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.LockoutRepository
import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository

typealias User = User
typealias LockoutRepository = LockoutRepository
typealias UserRepository = UserRepository
typealias Session = Session
typealias SessionRepository = SessionRepository
typealias SessionLookup = SessionLookup
typealias DeviceToken = DeviceToken
typealias DeviceTokenRepository = DeviceTokenRepository
typealias PasswordResetRepository = PasswordResetRepository
typealias ApiKeyRepository = ApiKeyRepository

data class SecurityConfig(
    val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    val sessionTimeoutSeconds: Long = 1800L,
    val maxFailedLoginAttempts: Int = 10,
    val lockoutDurationSeconds: Long = 900,
    val sessionAbsoluteTimeoutSeconds: Long = 86400L,
    val registrationEnabled: Boolean = true,
)
```

**IMPORTANT:** Kotlin type aliases with the same name as the target type don't actually work — you can't have `typealias User = User` where both are the same simple name. Instead, keep the original types as deprecated re-exports. Actually, the simplest approach is to just delete Models.kt entirely and update all imports in one go. Skip the alias approach and go straight to updating imports.

- [ ] **Step 1 (revised): Delete all types except SecurityConfig from Models.kt**

Replace Models.kt with only:

```kotlin
package io.github.rygel.outerstellar.platform.security

data class SecurityConfig(
    val appBaseUrl: String = io.github.rygel.outerstellar.platform.AppConfig.DEFAULT_APP_BASE_URL,
    val sessionTimeoutSeconds: Long = 1800L,
    val maxFailedLoginAttempts: Int = 10,
    val lockoutDurationSeconds: Long = 900,
    val sessionAbsoluteTimeoutSeconds: Long = 86400L,
    val registrationEnabled: Boolean = true,
)
```

- [ ] **Step 2: Compile platform-security — EXPECT FAILURE**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: FAILURE — all files in platform-security that imported User, UserRepository, etc. from Models.kt will have unresolved references. This is expected. We fix them in the next task.

---

## Task 4: Update all imports inside platform-security

**Files:**
- Modify: ALL .kt files in `platform-security/src/main/kotlin/` that reference User, UserRepository, Session, SessionRepository, SessionLookup, DeviceToken, DeviceTokenRepository, PasswordResetRepository, ApiKeyRepository, LockoutRepository

- [ ] **Step 1: Find all files needing updates**

Run: `rg "import io\.github\.rygel\.outerstellar\.platform\.security\.(User|UserRepository|LockoutRepository|Session[^R]|SessionRepository|SessionLookup|DeviceToken|PasswordResetRepository|ApiKeyRepository)" --type kotlin -l platform-security/src/main/kotlin/`

This returns the list of files to edit. For each file:

- Replace `import io.github.rygel.outerstellar.platform.security.User` with `import io.github.rygel.outerstellar.platform.model.User`
- Replace `import io.github.rygel.outerstellar.platform.security.UserRepository` with `import io.github.rygel.outerstellar.platform.persistence.UserRepository`
- Replace `import io.github.rygel.outerstellar.platform.security.LockoutRepository` with `import io.github.rygel.outerstellar.platform.persistence.LockoutRepository`
- Replace `import io.github.rygel.outerstellar.platform.security.Session` (but NOT SessionRepository) with `import io.github.rygel.outerstellar.platform.model.Session`
- Replace `import io.github.rygel.outerstellar.platform.security.SessionRepository` with `import io.github.rygel.outerstellar.platform.persistence.SessionRepository`
- Replace `import io.github.rygel.outerstellar.platform.security.SessionLookup` with `import io.github.rygel.outerstellar.platform.model.SessionLookup`
- Replace `import io.github.rygel.outerstellar.platform.security.DeviceToken` (but NOT DeviceTokenRepository) with `import io.github.rygel.outerstellar.platform.model.DeviceToken`
- Replace `import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository` with `import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository`
- Replace `import io.github.rygel.outerstellar.platform.security.PasswordResetRepository` with `import io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository`
- Replace `import io.github.rygel.outerstellar.platform.security.ApiKeyRepository` with `import io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository`

- [ ] **Step 2: Compile platform-security**

Run: `mvn -pl platform-security compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 5: Update all imports in platform-persistence-jooq and platform-persistence-jdbi

**Files:**
- Modify: All .kt files in `platform-persistence-jooq/src/main/kotlin/` and `platform-persistence-jdbi/src/main/kotlin/` that import User, UserRepository, Session, SessionRepository, etc. from the security package

- [ ] **Step 1: Update imports in both persistence modules**

Same replacement patterns as Task 4, applied to:
- `JooqUserRepository.kt`, `JooqSessionRepository.kt`, `JooqPasswordResetRepository.kt`, `JooqDeviceTokenRepository.kt`, `JooqApiKeyRepository.kt`, `PersistenceModule.kt` in platform-persistence-jooq
- `JdbiUserRepository.kt`, `JdbiSessionRepository.kt`, `JdbiPasswordResetRepository.kt`, `JdbiDeviceTokenRepository.kt`, `JdbiApiKeyRepository.kt`, `PersistenceModule.kt` in platform-persistence-jdbi

Also check for `OAuthRepository` — it's defined in `OAuthProvider.kt` in platform-security and should stay there (it's a security-specific SPI).

- [ ] **Step 2: Compile both persistence modules**

Run: `mvn -pl platform-persistence-jooq,platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 6: Update all imports in platform-web and platform-sync-client

**Files:**
- Modify: All .kt files in `platform-web/src/main/kotlin/` and `platform-sync-client/src/main/kotlin/` that import from the security package

- [ ] **Step 1: Update imports in platform-web production code**

Files to check (from dependency analysis):
- `App.kt` — imports SessionRealm, UserRepository, SessionLookup
- `WebContext.kt` — imports User, SessionLookup, UserRepository
- `Filters.kt` — imports User, UserRepository
- `PlatformExtension.kt` — imports User, UserRepository
- `Main.kt` — imports UserRepository
- `di/WebModule.kt` — imports UserRepository

Also search for any other files: `rg "import io\.github\.rygel\.outerstellar\.platform\.security\.(User|Session|DeviceToken)" --type kotlin -l platform-web/src/main/kotlin/`

- [ ] **Step 2: Update imports in platform-sync-client production code**

Search: `rg "import io\.github\.rygel\.outerstellar\.platform\.security\.(User|Session)" --type kotlin -l platform-sync-client/src/main/kotlin/`

The sync client likely imports User for DTOs. Update to `io.github.rygel.outerstellar.platform.model.User`.

- [ ] **Step 3: Compile web and sync-client**

Run: `mvn -pl platform-web,platform-sync-client compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 7: Update all imports in platform-seeder and platform-desktop

**Files:**
- Modify: `platform-seeder/src/main/kotlin/.../seed/SeedData.kt`
- Modify: All .kt files in `platform-desktop/src/main/kotlin/` that import from security package

- [ ] **Step 1: Update SeedData.kt**

- Replace `import io.github.rygel.outerstellar.platform.security.User` with `import io.github.rygel.outerstellar.platform.model.User`
- Replace `import io.github.rygel.outerstellar.platform.security.UserRepository` with `import io.github.rygel.outerstellar.platform.persistence.UserRepository`

- [ ] **Step 2: Update platform-desktop imports**

Search: `rg "import io\.github\.rygel\.outerstellar\.platform\.security\.(User|UserRepository)" --type kotlin -l platform-desktop/src/main/kotlin/`

Update any found imports.

- [ ] **Step 3: Compile all non-desktop modules**

Run: `mvn -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 8: Update all test imports

**Files:**
- Modify: ~60 test files across all modules

- [ ] **Step 1: Find and update all test files**

Run for each module's test directory:
```
rg "import io\.github\.rygel\.outerstellar\.platform\.security\.(User|UserRepository|LockoutRepository|Session|SessionRepository|SessionLookup|DeviceToken|DeviceTokenRepository|PasswordResetRepository|ApiKeyRepository)" --type kotlin -l platform-*/src/test/
```

Apply the same replacement patterns as Task 4. The bulk of test files are in:
- `platform-web/src/test/` (~43 files)
- `platform-persistence-jooq/src/test/` (~6 files)
- `platform-persistence-jdbi/src/test/` (~6 files)
- `platform-security/src/test/` (~2 files)

- [ ] **Step 2: Compile all test code**

Run: `mvn -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder test-compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: SUCCESS

---

## Task 9: Full build verification

- [ ] **Step 1: Run full reactor build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`
Expected: BUILD SUCCESS — all tests pass, SpotBugs/Detekt/Spotless clean.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: move User, UserRepository, Session, and related types to platform-core

Domain types (User, Session, SessionLookup, DeviceToken) moved from
platform-security to platform-core model package. Repository interfaces
(UserRepository, SessionRepository, etc.) moved to platform-core
persistence package. This fixes the dependency inversion where every
module depended on security just to use the User type.

SecurityConfig remains in platform-security (only used there)."
```

---

## Task 10: Remove platform-persistence-* dependency on platform-security

**Files:**
- Modify: `platform-persistence-jooq/pom.xml`
- Modify: `platform-persistence-jdbi/pom.xml`

- [ ] **Step 1: Check if persistence modules still need the security dependency**

After moving all types out, search for any remaining imports from platform-security in the persistence modules:

```
rg "import io\.github\.rygel\.outerstellar\.platform\.security" --type kotlin platform-persistence-jooq/src/
rg "import io\.github\.rygel\.outerstellar\.platform\.security" --type kotlin platform-persistence-jdbi/src/
```

If no imports remain (OAuthRepository is in security but used by JooqOAuthRepository — check), remove the `<dependency>` on `outerstellar-platform-security` from both persistence pom.xml files.

If `OAuthRepository` is still needed, the dependency stays for now (it moves when security is decomposed later).

- [ ] **Step 2: Verify build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit (if dependency was removed)**

```bash
git commit -m "refactor: remove platform-security dependency from persistence modules"
```

---

## Self-Review

### Spec coverage
- All 10 types identified in the "what moves" table have a creation task (Tasks 1-2)
- All production code imports updated (Tasks 4-7)
- All test imports updated (Task 8)
- Full build verification (Task 9)
- Dependency cleanup (Task 10)

### Placeholder scan
- No TBD/TODO/placeholders
- All code blocks contain complete implementations
- All commands have exact Maven invocations

### Type consistency
- User.kt uses UserRole from `io.github.rygel.outerstellar.platform.model` — matches existing AuthModels.kt location
- SessionLookup references User from same `model` package — correct
- All repository interfaces use model types from `io.github.rygel.outerstellar.platform.model` — consistent
- PasswordResetRepository uses PasswordResetToken (already in AuthModels.kt) — no FQN needed, matches existing pattern
- ApiKeyRepository uses ApiKey (already in AuthModels.kt) — no FQN needed, matches existing pattern
