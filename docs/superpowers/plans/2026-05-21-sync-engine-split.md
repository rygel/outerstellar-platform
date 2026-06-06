# SyncEngine Interface Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the 27-method SyncEngine interface and 290-line concrete SyncService into focused modules with narrow seams, split state, and per-module listeners.

**Architecture:** Decompose DesktopSyncEngine into 5 sub-modules (AuthModule, SyncDataModule, ProfileModule, AdminModule, NotificationModule), each with its own interface, state class, and listener. Split SyncService into 5 HTTP client interfaces (AuthClient, SyncClient, ProfileClient, AdminClient, NotificationClient) with a shared ApiSession for the auth token. The existing 7 test files migrate to test the new modules directly.

**Tech Stack:** Kotlin/JVM, http4k, MockK, JUnit 5, Koin DI, kotlinx.serialization

---

## File Structure

### New files in `platform-sync-client`

```
src/main/kotlin/io/github/rygel/outerstellar/platform/
├── sync/
│   ├── SyncService.kt                    ← MODIFY (gut and replace with client impls)
│   ├── client/
│   │   ├── ApiSession.kt                 ← CREATE
│   │   ├── AuthClient.kt                 ← CREATE (interface)
│   │   ├── SyncClient.kt                 ← CREATE (interface)
│   │   ├── ProfileClient.kt              ← CREATE (interface)
│   │   ├── AdminClient.kt                ← CREATE (interface)
│   │   ├── NotificationClient.kt         ← CREATE (interface)
│   │   ├── HttpAuthClient.kt             ← CREATE (implementation)
│   │   ├── HttpSyncClient.kt             ← CREATE (implementation)
│   │   ├── HttpProfileClient.kt          ← CREATE (implementation)
│   │   ├── HttpAdminClient.kt            ← CREATE (implementation)
│   │   └── HttpNotificationClient.kt     ← CREATE (implementation)
│   └── SyncModels.kt                     ← NO CHANGE (stays in platform-core)
├── sync/engine/
│   ├── SyncEngine.kt                     ← DELETE (replaced by module interfaces)
│   ├── DesktopSyncEngine.kt             ← DELETE (replaced by module impls)
│   ├── EngineState.kt                    ← DELETE (replaced by split states)
│   ├── EngineListener.kt                 ← DELETE (replaced by per-module listeners)
│   ├── EngineNotifier.kt                 ← DELETE (replaced by per-module listeners)
│   ├── module/
│   │   ├── AuthModule.kt                 ← CREATE (interface + state + listener)
│   │   ├── SyncDataModule.kt             ← CREATE (interface + state + listener)
│   │   ├── ProfileModule.kt              ← CREATE (interface + state + listener)
│   │   ├── AdminModule.kt                ← CREATE (interface + state + listener)
│   │   ├── NotificationModule.kt         ← CREATE (interface + state + listener)
│   │   ├── AuthModuleImpl.kt             ← CREATE
│   │   ├── SyncDataModuleImpl.kt         ← CREATE
│   │   ├── ProfileModuleImpl.kt          ← CREATE
│   │   ├── AdminModuleImpl.kt            ← CREATE
│   │   └── NotificationModuleImpl.kt     ← CREATE
│   ├── ConnectivityChecker.kt            ← NO CHANGE
│   └── DesktopAppConfig.kt               ← NO CHANGE
├── di/
│   └── ApiClientModule.kt               ← MODIFY (wire new client interfaces)
└── service/
    └── SyncProvider.kt                   ← DELETE (absorbed into SyncDataModule)
```

### Modified files in `platform-core`

```
src/main/kotlin/.../service/SyncProvider.kt    ← DELETE
```

### Modified files in `platform-desktop`

```
src/main/kotlin/.../di/DesktopModule.kt              ← MODIFY
src/main/kotlin/.../swing/viewmodel/SyncViewModel.kt ← MODIFY
src/main/kotlin/.../swing/SwingSyncApp.kt            ← MODIFY
```

### Modified test files in `platform-sync-client`

```
src/test/.../engine/DesktopSyncEngineTestBase.kt       ← DELETE → REPLACE with per-module test bases
src/test/.../engine/DesktopSyncEngineAuthTest.kt       ← DELETE → REPLACE with AuthModuleTest.kt
src/test/.../engine/DesktopSyncEngineSyncTest.kt       ← DELETE → REPLACE with SyncDataModuleSyncTest.kt
src/test/.../engine/DesktopSyncEngineDataTest.kt       ← DELETE → REPLACE with SyncDataModuleDataTest.kt
src/test/.../engine/DesktopSyncEngineProfileTest.kt    ← DELETE → REPLACE with ProfileModuleTest.kt
src/test/.../engine/DesktopSyncEngineAdminTest.kt      ← DELETE → REPLACE with AdminModuleTest.kt
src/test/.../engine/DesktopSyncEngineNotificationTest.kt ← DELETE → REPLACE with NotificationModuleTest.kt
src/test/.../engine/DesktopSyncEngineListenerTest.kt   ← DELETE → SPLIT across module tests
src/test/.../sync/SyncServiceTest.kt                   ← DELETE → REPLACE with per-client tests
src/test/.../di/KoinModuleTest.kt                      ← MODIFY
```

### Modified test files in `platform-desktop`

```
src/test/.../di/KoinModuleTest.kt                      ← MODIFY
src/test/.../swing/SyncViewModelAuthTest.kt             ← MODIFY
src/test/.../swing/SyncViewModelProfileTest.kt          ← MODIFY
src/test/.../swing/SyncViewModelAdminOperationsTest.kt  ← MODIFY
src/test/.../swing/SyncViewModelSessionExpiryTest.kt    ← MODIFY
src/test/.../swing/SyncViewModelPasswordResetTest.kt    ← MODIFY
src/test/.../swing/SyncViewModelSearchTest.kt           ← MODIFY
```

---

## Task 1: Create ApiSession and HTTP client interfaces

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/ApiSession.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/AuthClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/SyncClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/ProfileClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/AdminClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/NotificationClient.kt`

- [ ] **Step 1: Create `ApiSession.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

class ApiSession {
    @Volatile
    var apiToken: String? = null
        internal set

    @Volatile
    var userRole: String? = null
        internal set

    fun clear() {
        apiToken = null
        userRole = null
    }
}
```

- [ ] **Step 2: Create `AuthClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse

interface AuthClient {
    fun login(username: String, password: String): AuthTokenResponse

    fun register(username: String, password: String): AuthTokenResponse

    fun logout()

    fun changePassword(currentPassword: String, newPassword: String)

    fun requestPasswordReset(email: String)

    fun resetPassword(token: String, newPassword: String)
}
```

- [ ] **Step 3: Create `SyncClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.sync.SyncMessage
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse

interface SyncClient {
    fun pull(since: Long): SyncPullResponse

    fun push(request: SyncPushRequest): SyncPushResponse
}
```

- [ ] **Step 4: Create `ProfileClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserProfileResponse

interface ProfileClient {
    fun fetchProfile(): UserProfileResponse

    fun updateProfile(email: String, username: String?, avatarUrl: String?)

    fun deleteAccount(currentPassword: String)

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean)
}
```

- [ ] **Step 5: Create `AdminClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserSummary

interface AdminClient {
    fun listUsers(): List<UserSummary>

    fun setUserEnabled(userId: String, enabled: Boolean)

    fun setUserRole(userId: String, role: String)
}
```

- [ ] **Step 6: Create `NotificationClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary

interface NotificationClient {
    fun listNotifications(): List<NotificationSummary>

    fun markNotificationRead(notificationId: String)

    fun markAllNotificationsRead()
}
```

- [ ] **Step 7: Run compile check**

Run: `mvn -pl platform-sync-client compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS (interfaces only, no consumers yet)

- [ ] **Step 8: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/
git commit -m "feat(sync): add ApiSession and HTTP client interfaces for SyncEngine split"
```

---

## Task 2: Create HTTP client implementations

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpAuthClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpSyncClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpProfileClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpAdminClient.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpNotificationClient.kt`
- Reference: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/SyncService.kt` (the current implementation being decomposed)

Each client implementation extracts its methods from the current `SyncService.kt`. The shared helpers (`authenticatedRequest`, `checkSessionExpired`) become an internal utility.

- [ ] **Step 1: Create `HttpAuthClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.ChangePasswordRequest
import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.PasswordResetConfirm
import io.github.rygel.outerstellar.platform.model.PasswordResetRequest
import io.github.rygel.outerstellar.platform.model.RegisterRequest
import io.github.rygel.outerstellar.platform.model.SyncException
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.HttpHandler

class HttpAuthClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : AuthClient {
    private val loginRequestLens = Body.auto<LoginRequest>().toLens()
    private val registerRequestLens = Body.auto<RegisterRequest>().toLens()
    private val authTokenLens = Body.auto<AuthTokenResponse>().toLens()
    private val changePasswordLens = Body.auto<ChangePasswordRequest>().toLens()
    private val resetRequestLens = Body.auto<PasswordResetRequest>().toLens()
    private val resetConfirmLens = Body.auto<PasswordResetConfirm>().toLens()

    override fun login(username: String, password: String): AuthTokenResponse {
        val request =
            Request(Method.POST, "$baseUrl/api/v1/auth/login")
                .with(loginRequestLens of LoginRequest(username, password))
        val response = client(request)
        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            session.apiToken = auth.token
            session.userRole = auth.role
            return auth
        } else {
            throw SyncException("Login failed: ${response.status}")
        }
    }

    override fun register(username: String, password: String): AuthTokenResponse {
        val request =
            Request(Method.POST, "$baseUrl/api/v1/auth/register")
                .with(registerRequestLens of RegisterRequest(username, password))
        val response = client(request)
        if (response.status == Status.OK) {
            val auth = authTokenLens(response)
            session.apiToken = auth.token
            session.userRole = auth.role
            return auth
        } else {
            throw SyncException("Registration failed: ${response.status}")
        }
    }

    override fun logout() {
        session.apiToken?.let { token ->
            val request =
                Request(Method.POST, "$baseUrl/api/v1/auth/logout")
                    .header("Authorization", "Bearer $token")
            try {
                val response = client(request)
                if (response.status != Status.OK) {
                    logger.warn("Logout request returned {}", response.status)
                }
            } catch (e: Exception) {
                logger.warn("Logout request failed; clearing local session only: {}", e.message, e)
            }
        }
        session.clear()
    }

    override fun changePassword(currentPassword: String, newPassword: String) {
        val request =
            Request(Method.PUT, "$baseUrl/api/v1/auth/password")
                .with(changePasswordLens of ChangePasswordRequest(currentPassword, newPassword))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Password change failed: ${response.bodyString()}")
        }
    }

    override fun requestPasswordReset(email: String) {
        val request =
            Request(Method.POST, "$baseUrl/api/v1/auth/reset-request")
                .with(resetRequestLens of PasswordResetRequest(email))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset request failed: ${response.status}")
        }
    }

    override fun resetPassword(token: String, newPassword: String) {
        val request =
            Request(Method.POST, "$baseUrl/api/v1/auth/reset-confirm")
                .with(resetConfirmLens of PasswordResetConfirm(token, newPassword))
        val response = client(request)
        if (response.status != Status.OK) {
            throw SyncException("Password reset failed: ${response.bodyString()}")
        }
    }

    private fun authenticated(request: Request) =
        session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request

    private fun checkSessionExpired(response: org.http4k.core.Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw io.github.rygel.outerstellar.platform.model.SessionExpiredException()
        }
    }
}
```

- [ ] **Step 2: Create `HttpSyncClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.HttpHandler

class HttpSyncClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : SyncClient {
    private val pullResponseLens = Body.auto<SyncPullResponse>().toLens()
    private val pushRequestLens = Body.auto<SyncPushRequest>().toLens()
    private val pushResponseLens = Body.auto<SyncPushResponse>().toLens()

    override fun pull(since: Long): SyncPullResponse {
        val request = Request(Method.GET, "$baseUrl/api/v1/sync?since=$since")
        val authed = authenticated(request)
        val response = client(authed)
        if (response.status != Status.OK) {
            throw SyncException("Pull failed: ${response.status}")
        }
        return pullResponseLens(response)
    }

    override fun push(request: SyncPushRequest): SyncPushResponse {
        val httpRequest =
            Request(Method.POST, "$baseUrl/api/v1/sync")
                .with(pushRequestLens of request)
        val authed = authenticated(httpRequest)
        val response = client(authed)
        if (response.status != Status.OK) {
            throw SyncException("Push failed: ${response.status}")
        }
        return pushResponseLens(response)
    }

    private fun authenticated(request: Request) =
        session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request
}
```

- [ ] **Step 3: Create `HttpProfileClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.DeleteAccountRequest
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UpdateNotificationPrefsRequest
import io.github.rygel.outerstellar.platform.model.UpdateProfileRequest
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.HttpHandler

class HttpProfileClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : ProfileClient {
    private val updateProfileLens = Body.auto<UpdateProfileRequest>().toLens()
    private val userProfileResponseLens = Body.auto<UserProfileResponse>().toLens()
    private val updateNotifPrefsLens = Body.auto<UpdateNotificationPrefsRequest>().toLens()
    private val deleteAccountLens = Body.auto<DeleteAccountRequest>().toLens()

    override fun fetchProfile(): UserProfileResponse {
        val response = authenticated(Request(Method.GET, "$baseUrl/api/v1/auth/profile"))
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to fetch profile: ${response.status}")
        }
        return userProfileResponseLens(response)
    }

    override fun updateProfile(email: String, username: String?, avatarUrl: String?) {
        val request =
            Request(Method.PUT, "$baseUrl/api/v1/auth/profile")
                .with(updateProfileLens of UpdateProfileRequest(email, username, avatarUrl))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException(response.bodyString().ifBlank { "Profile update failed" })
        }
    }

    override fun deleteAccount(currentPassword: String) {
        val request =
            Request(Method.DELETE, "$baseUrl/api/v1/auth/account")
                .with(deleteAccountLens of DeleteAccountRequest(currentPassword))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException(response.bodyString().ifBlank { "Account deletion failed" })
        }
    }

    override fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean) {
        val request =
            Request(Method.PUT, "$baseUrl/api/v1/auth/notification-preferences")
                .with(updateNotifPrefsLens of UpdateNotificationPrefsRequest(emailEnabled, pushEnabled))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update notification preferences: ${response.status}")
        }
    }

    private fun authenticated(request: Request) =
        session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request

    private fun checkSessionExpired(response: org.http4k.core.Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw io.github.rygel.outerstellar.platform.model.SessionExpiredException()
        }
    }
}
```

- [ ] **Step 4: Create `HttpAdminClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SetUserEnabledRequest
import io.github.rygel.outerstellar.platform.model.SetUserRoleRequest
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserSummary
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.HttpHandler

class HttpAdminClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : AdminClient {
    private val userSummaryListLens = Body.auto<List<UserSummary>>().toLens()
    private val setUserEnabledLens = Body.auto<SetUserEnabledRequest>().toLens()
    private val setUserRoleLens = Body.auto<SetUserRoleRequest>().toLens()

    override fun listUsers(): List<UserSummary> {
        val response = authenticated(Request(Method.GET, "$baseUrl/api/v1/admin/users"))
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to list users: ${response.status}")
        }
        return userSummaryListLens(response)
    }

    override fun setUserEnabled(userId: String, enabled: Boolean) {
        val request =
            Request(Method.PUT, "$baseUrl/api/v1/admin/users/$userId/enabled")
                .with(setUserEnabledLens of SetUserEnabledRequest(enabled))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user enabled: ${response.bodyString()}")
        }
    }

    override fun setUserRole(userId: String, role: String) {
        val request =
            Request(Method.PUT, "$baseUrl/api/v1/admin/users/$userId/role")
                .with(setUserRoleLens of SetUserRoleRequest(role))
        val response = authenticated(request)
        checkSessionExpired(response)
        if (response.status != Status.OK) {
            throw SyncException("Failed to update user role: ${response.bodyString()}")
        }
    }

    private fun authenticated(request: Request) =
        session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request

    private fun checkSessionExpired(response: org.http4k.core.Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw io.github.rygel.outerstellar.platform.model.SessionExpiredException()
        }
    }
}
```

- [ ] **Step 5: Create `HttpNotificationClient.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.core.HttpHandler

class HttpNotificationClient(
    private val baseUrl: String,
    private val session: ApiSession,
    private val client: HttpHandler,
) : NotificationClient {
    private val notificationListLens = Body.auto<List<NotificationSummary>>().toLens()

    override fun listNotifications(): List<NotificationSummary> {
        val response = authenticated(Request(Method.GET, "$baseUrl/api/v1/notifications"))
        checkSessionExpired(response)
        if (response.status != Status.OK) return emptyList()
        return notificationListLens(response)
    }

    override fun markNotificationRead(notificationId: String) {
        val response =
            authenticated(
                Request(Method.PUT, "$baseUrl/api/v1/notifications/$notificationId/read")
            )
        checkSessionExpired(response)
    }

    override fun markAllNotificationsRead() {
        val response =
            authenticated(Request(Method.PUT, "$baseUrl/api/v1/notifications/read-all"))
        checkSessionExpired(response)
    }

    private fun authenticated(request: Request) =
        session.apiToken?.let { request.header("Authorization", "Bearer $it") } ?: request

    private fun checkSessionExpired(response: org.http4k.core.Response) {
        if (response.status == Status.UNAUTHORIZED && response.header("X-Session-Expired") == "true") {
            throw io.github.rygel.outerstellar.platform.model.SessionExpiredException()
        }
    }
}
```

- [ ] **Step 6: Write failing tests for HttpAuthClient**

Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpAuthClientTest.kt`

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SyncException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpAuthClientTest {
    private val session = ApiSession()

    private fun makeClient(handler: (org.http4k.core.Request) -> Response): AuthClient =
        HttpAuthClient("http://localhost:8080", session, handler)

    @Test
    fun `login stores token in session`() {
        val resp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok123", "alice", "USER"))
        val client = makeClient { resp }
        val result = client.login("alice", "pass")
        assertEquals("tok123", result.token)
        assertEquals("tok123", session.apiToken)
        assertEquals("USER", session.userRole)
    }

    @Test
    fun `login throws SyncException on failure`() {
        val client = makeClient { Response(Status.FORBIDDEN) }
        assertThrows<SyncException> { client.login("alice", "bad") }
    }

    @Test
    fun `register stores token in session`() {
        val resp =
            Response(Status.OK)
                .with(Body.auto<AuthTokenResponse>().toLens() of AuthTokenResponse("tok456", "bob", "USER"))
        val client = makeClient { resp }
        client.register("bob", "pass")
        assertEquals("tok456", session.apiToken)
    }

    @Test
    fun `logout clears session`() {
        session.apiToken = "tok"
        session.userRole = "ADMIN"
        val client = makeClient { Response(Status.OK) }
        client.logout()
        assertNull(session.apiToken)
        assertNull(session.userRole)
    }

    @Test
    fun `changePassword sends authenticated request`() {
        session.apiToken = "tok"
        var authHeader: String? = null
        val client = makeClient { req ->
            authHeader = req.header("Authorization")
            Response(Status.OK)
        }
        client.changePassword("old", "new")
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `requestPasswordReset does not require auth`() {
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.requestPasswordReset("a@b.c")
        assertTrue(called)
    }

    @Test
    fun `resetPassword does not require auth`() {
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.resetPassword("token123", "newPassword123")
        assertTrue(called)
    }
}
```

- [ ] **Step 7: Run compile check**

Run: `mvn -pl platform-sync-client test -Dtest=HttpAuthClientTest -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS (7 tests)

- [ ] **Step 8: Write failing tests for remaining HTTP clients**

Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpSyncClientTest.kt`

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.sync.SyncMessage
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HttpSyncClientTest {
    private val session = ApiSession()

    private fun makeClient(handler: (org.http4k.core.Request) -> Response): SyncClient =
        HttpSyncClient("http://localhost:8080", session, handler)

    @Test
    fun `pull returns SyncPullResponse on success`() {
        session.apiToken = "tok"
        val pullResp =
            Response(Status.OK)
                .with(
                    Body.auto<SyncPullResponse>().toLens() of
                        SyncPullResponse(messages = emptyList(), serverTimestamp = 100L, hasMore = false)
                )
        var authHeader: String? = null
        val client = makeClient { req ->
            authHeader = req.header("Authorization")
            pullResp
        }
        val result = client.pull(0L)
        assertEquals(0, result.messages.size)
        assertEquals(100L, result.serverTimestamp)
        assertEquals("Bearer tok", authHeader)
    }

    @Test
    fun `pull throws SyncException on failure`() {
        val client = makeClient { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.pull(0L) }
    }

    @Test
    fun `push returns SyncPushResponse on success`() {
        session.apiToken = "tok"
        val pushResp =
            Response(Status.OK)
                .with(
                    Body.auto<SyncPushResponse>().toLens() of
                        SyncPushResponse(appliedCount = 3, conflicts = emptyList())
                )
        val client = makeClient { pushResp }
        val result = client.push(SyncPushRequest(emptyList()))
        assertEquals(3, result.appliedCount)
    }

    @Test
    fun `push throws SyncException on failure`() {
        val client = makeClient { Response(Status.INTERNAL_SERVER_ERROR) }
        assertThrows<SyncException> { client.push(SyncPushRequest(emptyList())) }
    }
}
```

Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpProfileClientTest.kt`

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test

class HttpProfileClientTest {
    private val session = ApiSession()

    private fun makeClient(handler: (org.http4k.core.Request) -> Response): ProfileClient =
        HttpProfileClient("http://localhost:8080", session, handler)

    @Test
    fun `fetchProfile returns UserProfileResponse`() {
        session.apiToken = "tok"
        val profile = UserProfileResponse("alice", "a@b.c", null, true, true)
        val client = makeClient { req ->
            if (req.uri.toString().contains("auth/profile") && req.uri.toString().contains("notification").not()) {
                Response(Status.OK)
                    .with(Body.auto<UserProfileResponse>().toLens() of profile)
            } else {
                Response(Status.OK)
            }
        }
        val result = client.fetchProfile()
        assertEquals("alice", result.username)
    }

    @Test
    fun `updateProfile sends PUT with email`() {
        session.apiToken = "tok"
        var body: String? = null
        val client = makeClient { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.updateProfile("alice@example.com", "aliceNew", null)
        assertTrue(body?.contains("\"email\":\"alice@example.com\"") == true)
    }

    @Test
    fun `updateNotificationPreferences sends correct JSON`() {
        session.apiToken = "tok"
        var body: String? = null
        val client = makeClient { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.updateNotificationPreferences(false, true)
        assertEquals("{\"emailEnabled\":false,\"pushEnabled\":true}", body)
    }

    @Test
    fun `deleteAccount sends DELETE with auth`() {
        session.apiToken = "tok"
        var method: org.http4k.core.Method? = null
        var body: String? = null
        val client = makeClient { req ->
            method = req.method
            body = req.bodyString()
            Response(Status.OK)
        }
        client.deleteAccount("pass")
        assertEquals(org.http4k.core.Method.DELETE, method)
        assertEquals("{\"currentPassword\":\"pass\"}", body)
    }
}
```

Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpAdminClientTest.kt`

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.model.UserRole
import kotlin.test.assertEquals
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test

class HttpAdminClientTest {
    private val session = ApiSession()

    private fun makeClient(handler: (org.http4k.core.Request) -> Response): AdminClient =
        HttpAdminClient("http://localhost:8080", session, handler)

    @Test
    fun `listUsers returns user list`() {
        session.apiToken = "tok"
        val users = listOf(UserSummary("1", "alice", "a@b.c", UserRole.USER, true))
        val client = makeClient { req ->
            if (req.uri.toString().contains("admin/users") && req.method == org.http4k.core.Method.GET) {
                Response(Status.OK)
                    .with(Body.auto<List<UserSummary>>().toLens() of users)
            } else {
                Response(Status.OK)
            }
        }
        val result = client.listUsers()
        assertEquals(1, result.size)
        assertEquals("alice", result[0].username)
    }

    @Test
    fun `setUserEnabled sends PUT with correct body`() {
        session.apiToken = "tok"
        var body: String? = null
        val client = makeClient { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.setUserEnabled("uuid", true)
        assertEquals("{\"enabled\":true}", body)
    }

    @Test
    fun `setUserRole sends PUT with role`() {
        session.apiToken = "tok"
        var body: String? = null
        val client = makeClient { req ->
            body = req.bodyString()
            Response(Status.OK)
        }
        client.setUserRole("uuid", "ADMIN")
        assertEquals("{\"role\":\"ADMIN\"}", body)
    }
}
```

Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/HttpNotificationClientTest.kt`

```kotlin
package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.Body
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.junit.jupiter.api.Test

class HttpNotificationClientTest {
    private val session = ApiSession()

    private fun makeClient(handler: (org.http4k.core.Request) -> Response): NotificationClient =
        HttpNotificationClient("http://localhost:8080", session, handler)

    @Test
    fun `listNotifications returns list`() {
        session.apiToken = "tok"
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2025-01-01"))
        val client = makeClient { req ->
            if (req.uri.toString().contains("notifications") && req.method == org.http4k.core.Method.GET) {
                Response(Status.OK)
                    .with(Body.auto<List<NotificationSummary>>().toLens() of notifs)
            } else {
                Response(Status.OK)
            }
        }
        val result = client.listNotifications()
        assertEquals(1, result.size)
        assertEquals("n1", result[0].id)
    }

    @Test
    fun `markNotificationRead requires auth`() {
        session.apiToken = "tok"
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.markNotificationRead("n1")
        assertTrue(called)
    }

    @Test
    fun `markAllNotificationsRead requires auth`() {
        session.apiToken = "tok"
        var called = false
        val client = makeClient {
            called = true
            Response(Status.OK)
        }
        client.markAllNotificationsRead()
        assertTrue(called)
    }
}
```

- [ ] **Step 9: Run all new client tests**

Run: `mvn -pl platform-sync-client test -Dtest="HttpAuthClientTest,HttpSyncClientTest,HttpProfileClientTest,HttpAdminClientTest,HttpNotificationClientTest" -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS

- [ ] **Step 10: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/client/Http*.kt
git add platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/client/
git commit -m "feat(sync): add HTTP client implementations with tests for SyncEngine split"
```

---

## Task 3: Create module interfaces, state classes, and listeners

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/AuthModule.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/SyncDataModule.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/ProfileModule.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/AdminModule.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/NotificationModule.kt`

- [ ] **Step 1: Create `AuthModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine.module

data class AuthState(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userRole: String? = null,
)

interface AuthListener {
    fun onAuthStateChanged(state: AuthState) {}

    fun onSessionExpired() {}
}

interface AuthModule {
    val authState: AuthState

    fun addListener(listener: AuthListener)

    fun removeListener(listener: AuthListener)

    fun login(username: String, password: String): Result<Unit>

    fun register(username: String, password: String): Result<Unit>

    fun logout()

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    fun requestPasswordReset(email: String): Result<Unit>

    fun resetPassword(token: String, newPassword: String): Result<Unit>
}
```

- [ ] **Step 2: Create `SyncDataModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary

data class SyncDataState(
    val isSyncing: Boolean = false,
    val isOnline: Boolean = true,
    val messages: List<MessageSummary> = emptyList(),
    val contacts: List<ContactSummary> = emptyList(),
    val searchQuery: String = "",
    val syncStatus: String = "",
)

interface SyncDataListener {
    fun onSyncDataStateChanged(state: SyncDataState) {}

    fun onSyncError(operation: String, message: String) {}
}

interface SyncDataModule {
    val syncDataState: SyncDataState

    fun addListener(listener: SyncDataListener)

    fun removeListener(listener: SyncDataListener)

    fun sync(isAuto: Boolean = false): Result<Unit>

    fun startAutoSync()

    fun stopAutoSync()

    fun loadMessages()

    fun loadContacts()

    fun loadData()

    fun setSearchQuery(query: String)

    fun createLocalMessage(author: String, content: String): Result<Unit>

    fun resolveConflict(syncId: String, strategy: ConflictStrategy)

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit>

    fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit>
}
```

- [ ] **Step 3: Create `ProfileModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine.module

data class ProfileState(
    val userEmail: String = "",
    val userAvatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
)

interface ProfileListener {
    fun onProfileStateChanged(state: ProfileState) {}

    fun onSessionExpired() {}
}

interface ProfileModule {
    val profileState: ProfileState

    fun addListener(listener: ProfileListener)

    fun removeListener(listener: ProfileListener)

    fun loadProfile()

    fun updateProfile(email: String, username: String? = null, avatarUrl: String? = null): Result<Unit>

    fun deleteAccount(currentPassword: String): Result<Unit>

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit>
}
```

- [ ] **Step 4: Create `AdminModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.UserSummary

data class AdminState(
    val adminUsers: List<UserSummary> = emptyList(),
)

interface AdminListener {
    fun onAdminStateChanged(state: AdminState) {}

    fun onSessionExpired() {}
}

interface AdminModule {
    val adminState: AdminState

    fun addListener(listener: AdminListener)

    fun removeListener(listener: AdminListener)

    fun loadUsers()

    fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit>

    fun setUserRole(userId: String, role: String): Result<Unit>
}
```

- [ ] **Step 5: Create `NotificationModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.NotificationSummary

data class NotificationState(
    val notifications: List<NotificationSummary> = emptyList(),
) {
    val unreadCount: Int
        get() = notifications.count { !it.read }
}

interface NotificationListener {
    fun onNotificationStateChanged(state: NotificationState) {}

    fun onSessionExpired() {}
}

interface NotificationModule {
    val notificationState: NotificationState

    fun addListener(listener: NotificationListener)

    fun removeListener(listener: NotificationListener)

    fun loadNotifications()

    fun markNotificationRead(notificationId: String)

    fun markAllNotificationsRead()
}
```

- [ ] **Step 6: Run compile check**

Run: `mvn -pl platform-sync-client compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/
git commit -m "feat(sync): add module interfaces, state classes, and listeners for SyncEngine split"
```

---

## Task 4: Create module implementations

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/AuthModuleImpl.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/SyncDataModuleImpl.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/ProfileModuleImpl.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/AdminModuleImpl.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/NotificationModuleImpl.kt`

Each implementation is extracted from the corresponding methods in `DesktopSyncEngine.kt` (lines referenced below). The `runGuarded` / `runGuardedResult` / `handleSessionExpired` / `fireError` helpers move into each module that needs them.

- [ ] **Step 1: Create `AuthModuleImpl.kt`**

Extracted from `DesktopSyncEngine.kt:56-104` (login, register, logout) and `156-189` (password operations).

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class AuthModuleImpl(
    private val authClient: AuthClient,
    private val analytics: AnalyticsService,
    private val onLoadData: () -> Unit,
    private val onStartAutoSync: () -> Unit,
    private val onStopAutoSync: () -> Unit,
    private val notifier: ModuleNotifier? = null,
) : AuthModule {
    private val logger = LoggerFactory.getLogger(AuthModuleImpl::class.java)

    private val _state = AtomicReference(AuthState())
    override val authState: AuthState
        get() = _state.get()

    private val listeners = CopyOnWriteArrayList<AuthListener>()

    override fun addListener(listener: AuthListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AuthListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (AuthState) -> AuthState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onAuthStateChanged(newState) }
    }

    override fun login(username: String, password: String): Result<Unit> =
        runGuardedResult(
            "login",
            onError = { e ->
                updateState { it.copy(isLoggedIn = false) }
                notifier?.notifyFailure("Login failed: ${e.message}")
            },
        ) {
            val auth = authClient.login(username, password)
            updateState {
                it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role)
            }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_login")
            onStartAutoSync()
            onLoadData()
            notifier?.notifySuccess("Logged in as ${auth.username}")
            Result.success(Unit)
        }

    override fun register(username: String, password: String): Result<Unit> =
        runGuardedResult(
            "register",
            onError = { e ->
                updateState { it.copy(isLoggedIn = false) }
                notifier?.notifyFailure("Registration failed: ${e.message}")
            },
        ) {
            val auth = authClient.register(username, password)
            updateState {
                it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role)
            }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_register")
            onStartAutoSync()
            onLoadData()
            notifier?.notifySuccess("Registered as ${auth.username}")
            Result.success(Unit)
        }

    override fun logout() {
        onStopAutoSync()
        authClient.logout()
        updateState { AuthState() }
        val username = authState.userName
        if (username.isNotBlank()) {
            analytics.track(username, "user_logout")
        }
    }

    override fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runGuardedResult(
            "changePassword",
            onError = { e -> notifier?.notifyFailure("Password change failed: ${e.message}") },
        ) {
            authClient.changePassword(currentPassword, newPassword)
            analytics.track(authState.userName, "password_changed")
            notifier?.notifySuccess("Password changed")
            Result.success(Unit)
        }

    override fun requestPasswordReset(email: String): Result<Unit> =
        try {
            authClient.requestPasswordReset(email)
            notifier?.notifySuccess("Password reset email sent")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Password reset request failed", e)
            notifier?.notifyFailure("Password reset request failed: ${e.message}")
            Result.failure(e)
        }

    override fun resetPassword(token: String, newPassword: String): Result<Unit> =
        try {
            authClient.resetPassword(token, newPassword)
            notifier?.notifySuccess("Password has been reset")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Password reset failed", e)
            notifier?.notifyFailure("Password reset failed: ${e.message}")
            Result.failure(e)
        }

    private fun handleSessionExpired(e: Exception) {
        logger.warn("Session expired: ${e.message}", e)
        onStopAutoSync()
        authClient.logout()
        updateState { AuthState() }
        listeners.forEach { it.onSessionExpired() }
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> =
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
}

interface ModuleNotifier {
    fun notifySuccess(message: String)
    fun notifyFailure(message: String)
}
```

- [ ] **Step 2: Create `SyncDataModuleImpl.kt`**

Extracted from `DesktopSyncEngine.kt:106-154` (sync), `361-387` (data loading), `280-354` (local writes), `389-408` (auto-sync).

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncStats
import io.github.rygel.outerstellar.platform.sync.client.ApiSession
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class SyncDataModuleImpl(
    private val syncClient: SyncClient,
    private val messageService: MessageService,
    private val contactService: ContactService?,
    private val analytics: AnalyticsService,
    private val repository: MessageRepository,
    private val transactionManager: TransactionManager,
    private val session: ApiSession,
    private val authStateProvider: () -> AuthState,
    private val notifier: ModuleNotifier? = null,
    private val connectivityChecker: io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker? = null,
) : SyncDataModule {
    private val logger = LoggerFactory.getLogger(SyncDataModuleImpl::class.java)

    private val _state = AtomicReference(SyncDataState())
    override val syncDataState: SyncDataState
        get() = _state.get()

    private val listeners = CopyOnWriteArrayList<SyncDataListener>()
    private val syncInProgress = AtomicBoolean(false)
    private var autoSyncExecutor: ScheduledExecutorService? = null
    private val autoSyncIntervalMinutes: Long = 5L

    init {
        connectivityChecker?.addObserver { online ->
            updateState { it.copy(isOnline = online) }
        }
    }

    override fun addListener(listener: SyncDataListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SyncDataListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (SyncDataState) -> SyncDataState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onSyncDataStateChanged(newState) }
    }

    override fun sync(isAuto: Boolean): Result<Unit> {
        val auth = authStateProvider()
        if (!auth.isLoggedIn) {
            return Result.failure(IllegalStateException("Not logged in"))
        }
        if (syncDataState.isOnline == false) {
            if (!isAuto) {
                notifier?.notifyFailure("Cannot sync while offline")
            }
            return Result.failure(IllegalStateException("Offline"))
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            return Result.success(Unit)
        }
        updateState { it.copy(isSyncing = true, syncStatus = if (isAuto) "Auto-syncing..." else "Syncing...") }
        return try {
            val stats = doSync()
            updateState {
                it.copy(
                    isSyncing = false,
                    syncStatus = buildSyncStatusMessage(stats.pushedCount, stats.pulledCount, stats.conflictCount),
                )
            }
            if (isAuto) {
                analytics.track(auth.userName, "auto_sync")
            } else {
                analytics.track(auth.userName, "manual_sync")
            }
            loadData()
            if (!isAuto) {
                notifier?.notifySuccess(syncDataState.syncStatus)
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false) }
            Result.failure(e)
        } catch (e: Exception) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false, syncStatus = "Sync failed: ${e.message}") }
            if (!isAuto) {
                notifier?.notifyFailure("Sync failed: ${e.message}")
                fireError("sync", e.message ?: "Unknown error")
            }
            Result.failure(e)
        } finally {
            syncInProgress.set(false)
        }
    }

    private fun doSync(): SyncStats {
        val lastSync = repository.getLastSyncEpochMs()

        val allPulled = mutableListOf<io.github.rygel.outerstellar.platform.sync.SyncMessage>()
        var pullHasMore = true
        var pullSince = lastSync
        var latestTimestamp = 0L

        while (pullHasMore) {
            val pullBody = syncClient.pull(pullSince)
            allPulled.addAll(pullBody.messages)
            pullHasMore = pullBody.hasMore
            latestTimestamp = pullBody.serverTimestamp
            if (pullBody.messages.isNotEmpty()) {
                pullSince = pullBody.messages.maxOf { it.updatedAtEpochMs }
            }
        }

        val dirtyMessages = repository.listDirtyMessages()
        val pushRequestData =
            io.github.rygel.outerstellar.platform.sync.SyncPushRequest(
                dirtyMessages.map { it.toSyncMessage() }
            )

        val pushBody = syncClient.push(pushRequestData)

        transactionManager.inTransaction {
            allPulled.forEach { repository.upsertSyncedMessage(it, false) }
            pushBody.conflicts.forEach { conflict ->
                conflict.serverMessage?.let { repository.upsertSyncedMessage(it, false) }
            }
            repository.setLastSyncEpochMs(latestTimestamp)
        }

        return SyncStats(
            pushedCount = pushBody.appliedCount,
            pulledCount = allPulled.size,
            conflictCount = pushBody.conflicts.size,
        )
    }

    override fun startAutoSync() {
        stopAutoSync()
        autoSyncExecutor =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "auto-sync").also { it.isDaemon = true }
                }
                .also { executor ->
                    executor.scheduleAtFixedRate(
                        { sync(isAuto = true) },
                        autoSyncIntervalMinutes,
                        autoSyncIntervalMinutes,
                        TimeUnit.MINUTES,
                    )
                }
    }

    override fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    override fun loadMessages() {
        try {
            val query = syncDataState.searchQuery.ifBlank { null }
            val result = messageService.listMessages(query = query)
            updateState { it.copy(messages = result.items) }
        } catch (e: Exception) {
            logger.warn("Failed to load messages", e)
            fireError("loadMessages", e.message ?: "Unknown error")
        }
    }

    override fun loadContacts() {
        val svc = contactService ?: return
        try {
            val query = syncDataState.searchQuery.ifBlank { null }
            val contacts = svc.listContacts(query = query)
            updateState { it.copy(contacts = contacts) }
        } catch (e: Exception) {
            logger.warn("Failed to load contacts", e)
            fireError("loadContacts", e.message ?: "Unknown error")
        }
    }

    override fun loadData() {
        loadMessages()
        loadContacts()
    }

    override fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadData()
    }

    override fun createLocalMessage(author: String, content: String): Result<Unit> =
        runGuardedResult("createLocalMessage") {
            messageService.createLocalMessage(author, content)
            loadMessages()
            Result.success(Unit)
        }

    override fun resolveConflict(syncId: String, strategy: ConflictStrategy) =
        runGuarded("resolveConflict") {
            messageService.resolveConflict(syncId, strategy)
            loadMessages()
        }

    override fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit> {
        val svc = contactService
            ?: return Result.failure(IllegalStateException("Contact service not available"))
        return try {
            svc.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
            loadContacts()
            analytics.track(authStateProvider().userName, "contact_created", mapOf("name" to name))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn("Failed to create contact", e)
            fireError("createContact", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    override fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit> {
        val svc = contactService
            ?: return Result.failure(IllegalStateException("Contact service not available"))
        return try {
            val stored =
                svc.getContactBySyncId(syncId)
                    ?: return Result.failure(IllegalStateException("Contact not found: $syncId"))
            val updated =
                stored.copy(
                    name = name,
                    emails = emails,
                    phones = phones,
                    socialMedia = socialMedia,
                    company = company,
                    companyAddress = companyAddress,
                )
            svc.updateContact(updated)
            loadContacts()
            analytics.track(authStateProvider().userName, "contact_updated", mapOf("name" to name))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn("Failed to update contact", e)
            fireError("updateContact", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private fun fireError(operation: String, message: String) {
        listeners.forEach { it.onSyncError(operation, message) }
    }

    private fun buildSyncStatusMessage(pushed: Int, pulled: Int, conflicts: Int): String {
        val parts = mutableListOf<String>()
        if (pushed > 0) parts.add("pushed $pushed")
        if (pulled > 0) parts.add("pulled $pulled")
        if (conflicts > 0) parts.add("$conflicts conflict(s)")
        return if (parts.isEmpty()) "Everything up to date" else "Synced: ${parts.joinToString(", ")}"
    }

    private inline fun runGuarded(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            logger.warn("Session expired during $operation", e)
            onError(e)
            fireError(operation, e.message ?: "Session expired")
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            fireError(operation, e.message ?: "Unknown error")
        }
    }

    private inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> =
        try {
            block()
        } catch (e: SessionExpiredException) {
            logger.warn("Session expired during $operation", e)
            onError(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
}
```

- [ ] **Step 3: Create `ProfileModuleImpl.kt`**

Extracted from `DesktopSyncEngine.kt:231-278`.

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class ProfileModuleImpl(
    private val profileClient: ProfileClient,
    private val analytics: AnalyticsService,
    private val authStateProvider: () -> AuthState,
    private val onLoadData: () -> Unit,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
    private val notifier: ModuleNotifier? = null,
) : ProfileModule {
    private val logger = LoggerFactory.getLogger(ProfileModuleImpl::class.java)

    private val _state = AtomicReference(ProfileState())
    override val profileState: ProfileState
        get() = _state.get()

    private val listeners = CopyOnWriteArrayList<ProfileListener>()

    override fun addListener(listener: ProfileListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ProfileListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (ProfileState) -> ProfileState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onProfileStateChanged(newState) }
    }

    override fun loadProfile() =
        runGuarded("loadProfile") {
            val profile = profileClient.fetchProfile()
            updateState {
                it.copy(
                    userEmail = profile.email,
                    userAvatarUrl = profile.avatarUrl,
                    emailNotificationsEnabled = profile.emailNotificationsEnabled,
                    pushNotificationsEnabled = profile.pushNotificationsEnabled,
                )
            }
        }

    override fun updateProfile(email: String, username: String?, avatarUrl: String?): Result<Unit> =
        runGuardedResult(
            "updateProfile",
            onError = { e -> notifier?.notifyFailure("Profile update failed: ${e.message}") },
        ) {
            profileClient.updateProfile(email, username, avatarUrl)
            onLoadData()
            loadProfile()
            analytics.track(authStateProvider().userName, "profile_updated")
            notifier?.notifySuccess("Profile updated")
            Result.success(Unit)
        }

    override fun deleteAccount(currentPassword: String): Result<Unit> =
        runGuardedResult(
            "deleteAccount",
            onError = { e -> notifier?.notifyFailure("Account deletion failed: ${e.message}") },
        ) {
            val username = authStateProvider().userName
            profileClient.deleteAccount(currentPassword)
            onStopAutoSync()
            onLogout()
            analytics.track(username, "account_deleted")
            updateState { ProfileState() }
            notifier?.notifySuccess("Account deleted")
            Result.success(Unit)
        }

    override fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit> =
        runGuardedResult("updateNotificationPreferences") {
            profileClient.updateNotificationPreferences(emailEnabled, pushEnabled)
            updateState { it.copy(emailNotificationsEnabled = emailEnabled, pushNotificationsEnabled = pushEnabled) }
            Result.success(Unit)
        }

    private fun handleSessionExpired(e: Exception) {
        logger.warn("Session expired: ${e.message}", e)
        onStopAutoSync()
        onLogout()
        updateState { ProfileState() }
        listeners.forEach { it.onSessionExpired() }
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private inline fun runGuarded(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
        }
    }

    private inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> =
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
}
```

- [ ] **Step 4: Create `AdminModuleImpl.kt`**

Extracted from `DesktopSyncEngine.kt:191-211`.

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class AdminModuleImpl(
    private val adminClient: AdminClient,
    private val analytics: AnalyticsService,
    private val authStateProvider: () -> AuthState,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
) : AdminModule {
    private val logger = LoggerFactory.getLogger(AdminModuleImpl::class.java)

    private val _state = AtomicReference(AdminState())
    override val adminState: AdminState
        get() = _state.get()

    private val listeners = CopyOnWriteArrayList<AdminListener>()

    override fun addListener(listener: AdminListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AdminListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (AdminState) -> AdminState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onAdminStateChanged(newState) }
    }

    override fun loadUsers() =
        runGuarded("loadUsers") {
            val users = adminClient.listUsers()
            updateState { it.copy(adminUsers = users) }
        }

    override fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit> =
        runGuardedResult("setUserEnabled") {
            adminClient.setUserEnabled(userId, enabled)
            loadUsers()
            analytics.track(authStateProvider().userName, "user_enabled_changed", mapOf("userId" to userId, "enabled" to enabled))
            Result.success(Unit)
        }

    override fun setUserRole(userId: String, role: String): Result<Unit> =
        runGuardedResult("setUserRole") {
            adminClient.setUserRole(userId, role)
            loadUsers()
            analytics.track(authStateProvider().userName, "user_role_changed", mapOf("userId" to userId, "role" to role))
            Result.success(Unit)
        }

    private fun handleSessionExpired(e: Exception) {
        logger.warn("Session expired: ${e.message}", e)
        onStopAutoSync()
        onLogout()
        updateState { AdminState() }
        listeners.forEach { it.onSessionExpired() }
    }

    private inline fun runGuarded(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
        }
    }

    private inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> =
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            Result.failure(e)
        }
}
```

- [ ] **Step 5: Create `NotificationModuleImpl.kt`**

Extracted from `DesktopSyncEngine.kt:213-229`.

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class NotificationModuleImpl(
    private val notificationClient: NotificationClient,
    private val onStopAutoSync: () -> Unit,
    private val onLogout: () -> Unit,
) : NotificationModule {
    private val logger = LoggerFactory.getLogger(NotificationModuleImpl::class.java)

    private val _state = AtomicReference(NotificationState())
    override val notificationState: NotificationState
        get() = _state.get()

    private val listeners = CopyOnWriteArrayList<NotificationListener>()

    override fun addListener(listener: NotificationListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: NotificationListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (NotificationState) -> NotificationState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onNotificationStateChanged(newState) }
    }

    override fun loadNotifications() =
        runGuarded("loadNotifications") {
            val notifications = notificationClient.listNotifications()
            updateState { it.copy(notifications = notifications) }
        }

    override fun markNotificationRead(notificationId: String) =
        runGuarded("markNotificationRead") {
            notificationClient.markNotificationRead(notificationId)
            loadNotifications()
        }

    override fun markAllNotificationsRead() =
        runGuarded("markAllNotificationsRead") {
            notificationClient.markAllNotificationsRead()
            loadNotifications()
        }

    private fun handleSessionExpired(e: Exception) {
        logger.warn("Session expired: ${e.message}", e)
        onStopAutoSync()
        onLogout()
        updateState { NotificationState() }
        listeners.forEach { it.onSessionExpired() }
    }

    private inline fun runGuarded(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
        }
    }
}
```

- [ ] **Step 6: Run compile check**

Run: `mvn -pl platform-sync-client compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/module/*Impl.kt
git commit -m "feat(sync): add module implementations extracted from DesktopSyncEngine"
```

---

## Task 5: Migrate tests from DesktopSyncEngine*Test to module tests

**Files:**
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineTestBase.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineAuthTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineSyncTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineDataTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineProfileTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineAdminTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineNotificationTest.kt`
- Delete: `platform-sync-client/src/test/.../engine/DesktopSyncEngineListenerTest.kt`
- Delete: `platform-sync-client/src/test/.../sync/SyncServiceTest.kt`
- Create: `platform-sync-client/src/test/.../engine/module/AuthModuleTest.kt`
- Create: `platform-sync-client/src/test/.../engine/module/SyncDataModuleTest.kt`
- Create: `platform-sync-client/src/test/.../engine/module/ProfileModuleTest.kt`
- Create: `platform-sync-client/src/test/.../engine/module/AdminModuleTest.kt`
- Create: `platform-sync-client/src/test/.../engine/module/NotificationModuleTest.kt`

Each new test file recreates the same test coverage as the old DesktopSyncEngine tests, but tests the module implementations directly. The HTTP client tests (from Task 2) already cover SyncServiceTest's coverage.

- [ ] **Step 1: Create `AuthModuleTest.kt`**

Migrates coverage from `DesktopSyncEngineAuthTest` (7 tests) + password reset tests from `DesktopSyncEngineListenerTest` (4 tests).

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthModuleTest {
    private lateinit var authClient: AuthClient
    private lateinit var analytics: AnalyticsService
    private lateinit var notifier: ModuleNotifier
    private var loadDataCalled = false
    private var startAutoSyncCalled = false
    private var stopAutoSyncCalled = false
    private lateinit var module: AuthModule

    @BeforeEach
    fun setUp() {
        authClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        loadDataCalled = false
        startAutoSyncCalled = false
        stopAutoSyncCalled = false
        module = AuthModuleImpl(
            authClient = authClient,
            analytics = analytics,
            onLoadData = { loadDataCalled = true },
            onStartAutoSync = { startAutoSyncCalled = true },
            onStopAutoSync = { stopAutoSyncCalled = true },
            notifier = notifier,
        )
    }

    @Test
    fun `login success updates state and starts auto-sync`() {
        every { authClient.login("alice", "pw") } returns AuthTokenResponse("tok", "alice", "ADMIN")

        val result = module.login("alice", "pw")

        assertTrue(result.isSuccess)
        assertTrue(module.authState.isLoggedIn)
        assertEquals("alice", module.authState.userName)
        assertEquals("ADMIN", module.authState.userRole)
        verify { analytics.identify("alice", mapOf("role" to "ADMIN")) }
        verify { analytics.track("alice", "user_login") }
        verify { notifier.notifySuccess("Logged in as alice") }
        assertTrue(loadDataCalled)
        assertTrue(startAutoSyncCalled)
    }

    @Test
    fun `login failure returns Result failure`() {
        every { authClient.login("alice", "bad") } throws RuntimeException("Bad credentials")

        val result = module.login("alice", "bad")

        assertTrue(result.isFailure)
        assertFalse(module.authState.isLoggedIn)
        verify { notifier.notifyFailure("Login failed: Bad credentials") }
    }

    @Test
    fun `login session expired fires session expired`() {
        every { authClient.login("alice", "pw") } throws SessionExpiredException()
        val listener = mockk<AuthListener>(relaxed = true)
        module.addListener(listener)

        val result = module.login("alice", "pw")

        assertTrue(result.isFailure)
        verify { listener.onSessionExpired() }
    }

    @Test
    fun `register success updates state`() {
        every { authClient.register("bob", "pw") } returns AuthTokenResponse("tok2", "bob", "USER")

        val result = module.register("bob", "pw")

        assertTrue(result.isSuccess)
        assertTrue(module.authState.isLoggedIn)
        assertEquals("bob", module.authState.userName)
        verify { analytics.track("bob", "user_register") }
        verify { notifier.notifySuccess("Registered as bob") }
    }

    @Test
    fun `register failure returns Result failure`() {
        every { authClient.register("bob", "pw") } throws RuntimeException("Taken")

        val result = module.register("bob", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Registration failed: Taken") }
    }

    @Test
    fun `logout clears state`() {
        every { authClient.login("user", "pass") } returns AuthTokenResponse("tok", "user", "USER")
        module.login("user", "pass")

        module.logout()

        assertFalse(module.authState.isLoggedIn)
        assertEquals("", module.authState.userName)
        assertNull(module.authState.userRole)
        assertTrue(stopAutoSyncCalled)
    }

    @Test
    fun `changePassword success`() {
        val result = module.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { authClient.changePassword("old", "new") }
        verify { analytics.track(any(), "password_changed") }
        verify { notifier.notifySuccess("Password changed") }
    }

    @Test
    fun `changePassword failure`() {
        every { authClient.changePassword(any(), any()) } throws RuntimeException("Weak")

        val result = module.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password change failed: Weak") }
    }

    @Test
    fun `changePassword session expired`() {
        every { authClient.changePassword(any(), any()) } throws SessionExpiredException()
        val listener = mockk<AuthListener>(relaxed = true)
        module.addListener(listener)

        val result = module.changePassword("old", "new")

        assertTrue(result.isFailure)
        verify { listener.onSessionExpired() }
    }

    @Test
    fun `requestPasswordReset success`() {
        val result = module.requestPasswordReset("a@b.c")

        assertTrue(result.isSuccess)
        verify { authClient.requestPasswordReset("a@b.c") }
        verify { notifier.notifySuccess("Password reset email sent") }
    }

    @Test
    fun `requestPasswordReset failure`() {
        every { authClient.requestPasswordReset(any()) } throws RuntimeException("Fail")

        val result = module.requestPasswordReset("a@b.c")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset request failed: Fail") }
    }

    @Test
    fun `resetPassword success`() {
        val result = module.resetPassword("token123", "newpass")

        assertTrue(result.isSuccess)
        verify { authClient.resetPassword("token123", "newpass") }
        verify { notifier.notifySuccess("Password has been reset") }
    }

    @Test
    fun `resetPassword failure`() {
        every { authClient.resetPassword(any(), any()) } throws RuntimeException("Expired")

        val result = module.resetPassword("tok", "pw")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Password reset failed: Expired") }
    }

    @Test
    fun `initial state has defaults`() {
        assertFalse(module.authState.isLoggedIn)
        assertEquals("", module.authState.userName)
        assertNull(module.authState.userRole)
    }

    @Test
    fun `removeListener stops receiving events`() {
        val listener = mockk<AuthListener>(relaxed = true)
        module.addListener(listener)
        module.removeListener(listener)

        every { authClient.login("a", "b") } returns AuthTokenResponse("t", "a", "USER")
        module.login("a", "b")

        verify(exactly = 0) { listener.onAuthStateChanged(any()) }
    }
}
```

- [ ] **Step 2: Run AuthModuleTest**

Run: `mvn -pl platform-sync-client test -Dtest=AuthModuleTest -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS (15 tests)

- [ ] **Step 3: Create `SyncDataModuleTest.kt`**

Migrates coverage from `DesktopSyncEngineSyncTest` (11 tests) + `DesktopSyncEngineDataTest` (8 tests) + relevant listener tests (connectivity, resolveConflict, initial state).

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import io.github.rygel.outerstellar.platform.sync.SyncStats
import io.github.rygel.outerstellar.platform.sync.client.ApiSession
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncDataModuleTest {
    private lateinit var syncClient: SyncClient
    private lateinit var messageService: MessageService
    private lateinit var contactService: ContactService
    private lateinit var analytics: AnalyticsService
    private lateinit var repository: MessageRepository
    private lateinit var transactionManager: TransactionManager
    private lateinit var session: ApiSession
    private lateinit var connectivityChecker: ConnectivityChecker
    private lateinit var notifier: ModuleNotifier
    private var connectivityObserver: ((Boolean) -> Unit)? = null
    private var authState = AuthState()

    private lateinit var module: SyncDataModule

    @BeforeEach
    fun setUp() {
        syncClient = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        contactService = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        transactionManager = mockk(relaxed = true)
        session = ApiSession()
        connectivityChecker = mockk(relaxed = true)
        notifier = mockk(relaxed = true)

        val observerSlot = slot<(Boolean) -> Unit>()
        every { connectivityChecker.addObserver(capture(observerSlot)) } answers {
            connectivityObserver = observerSlot.captured
        }

        module = SyncDataModuleImpl(
            syncClient = syncClient,
            messageService = messageService,
            contactService = contactService,
            analytics = analytics,
            repository = repository,
            transactionManager = transactionManager,
            session = session,
            authStateProvider = { authState },
            notifier = notifier,
            connectivityChecker = connectivityChecker,
        )
    }

    private fun stubLoggedIn() {
        authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")
    }

    private fun stubSyncSuccess(pushed: Int = 0, pulled: Int = 0, conflicts: Int = 0): SyncStats {
        val pullResp = SyncPullResponse(messages = emptyList(), serverTimestamp = 100L, hasMore = false)
        every { syncClient.pull(any()) } returns pullResp
        val pushResp = SyncPushResponse(appliedCount = pushed, conflicts = (0 until conflicts).map { io.github.rygel.outerstellar.platform.sync.SyncConflict("c$it", "reason") })
        every { syncClient.push(any()) } returns pushResp
        return SyncStats(pushedCount = pushed, pulledCount = pulled, conflictCount = conflicts)
    }

    @Test
    fun `sync success updates state and tracks analytics`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 3, pulled = 0, conflicts = 1)

        val result = module.sync()

        assertTrue(result.isSuccess)
        assertFalse(module.syncDataState.isSyncing)
        assertEquals("Synced: pushed 3, 1 conflict(s)", module.syncDataState.syncStatus)
        verify { analytics.track("user", "manual_sync") }
        verify { notifier.notifySuccess(module.syncDataState.syncStatus) }
    }

    @Test
    fun `sync when offline returns failure`() {
        stubLoggedIn()
        connectivityObserver?.invoke(false)

        val result = module.sync()

        assertTrue(result.isFailure)
        assertEquals("Offline", result.exceptionOrNull()?.message)
        verify { notifier.notifyFailure("Cannot sync while offline") }
    }

    @Test
    fun `sync when not logged in returns failure`() {
        val result = module.sync()

        assertTrue(result.isFailure)
        assertEquals("Not logged in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sync when already syncing is no-op`() {
        stubLoggedIn()
        every { syncClient.pull(any()) } answers {
            Thread.sleep(100)
            SyncPullResponse(messages = emptyList(), serverTimestamp = 100L, hasMore = false)
        }
        every { syncClient.push(any()) } returns SyncPushResponse()

        val results = mutableListOf<Result<Unit>>()
        val t1 = Thread { results.add(module.sync()) }
        val t2 = Thread { results.add(module.sync()) }
        t1.start()
        Thread.sleep(20)
        t2.start()
        t1.join()
        t2.join()

        assertTrue(results.all { it.isSuccess })
        verify(exactly = 1) { syncClient.pull(any()) }
    }

    @Test
    fun `sync failure calls notifier with failure`() {
        stubLoggedIn()
        every { syncClient.pull(any()) } throws RuntimeException("Network error")

        val result = module.sync()

        assertTrue(result.isFailure)
        assertFalse(module.syncDataState.isSyncing)
        assertEquals("Sync failed: Network error", module.syncDataState.syncStatus)
        verify { notifier.notifyFailure("Sync failed: Network error") }
    }

    @Test
    fun `sync auto mode does not notify`() {
        stubLoggedIn()
        clearMocks(notifier)
        stubSyncSuccess(pushed = 1)

        val result = module.sync(isAuto = true)

        assertTrue(result.isSuccess)
        verify(exactly = 0) { notifier.notifySuccess(any()) }
        verify { analytics.track("user", "auto_sync") }
    }

    @Test
    fun `sync with zero stats shows up to date`() {
        stubLoggedIn()
        stubSyncSuccess(pushed = 0, pulled = 0, conflicts = 0)

        module.sync()

        assertEquals("Everything up to date", module.syncDataState.syncStatus)
    }

    @Test
    fun `startAutoSync creates executor and stopAutoSync shuts down`() {
        module.startAutoSync()
        module.stopAutoSync()
    }

    @Test
    fun `loadData fetches messages and contacts`() {
        every { messageService.listMessages(any()) } returns
            PagedResult(items = listOf(MessageSummary("s1", "a", "c", 1L, false)), metadata = PaginationMetadata(1, 100, 1))
        every { contactService.listContacts(any()) } returns
            listOf(ContactSummary("c1", "Alice", emptyList(), emptyList(), emptyList(), "", "", "", 1L, false))

        module.loadData()

        assertEquals(1, module.syncDataState.messages.size)
        assertEquals(1, module.syncDataState.contacts.size)
    }

    @Test
    fun `loadData with search query passes query to services`() {
        every { messageService.listMessages(query = "test") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.setSearchQuery("test")
        module.loadData()

        verify { messageService.listMessages(query = "test") }
        verify { contactService.listContacts(query = "test") }
    }

    @Test
    fun `createLocalMessage success`() {
        every { messageService.createLocalMessage("author", "content") } returns mockk(relaxed = true)
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        val result = module.createLocalMessage("author", "content")

        assertTrue(result.isSuccess)
        verify { messageService.createLocalMessage("author", "content") }
        verify { messageService.listMessages(any()) }
    }

    @Test
    fun `createLocalMessage validation failure returns Result failure`() {
        every { messageService.createLocalMessage("", "content") } throws
            ValidationException(listOf("Author is required."))

        val result = module.createLocalMessage("", "content")

        assertTrue(result.isFailure)
    }

    @Test
    fun `createContact success`() {
        stubLoggedIn()
        every { contactService.createContact(any(), any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { contactService.listContacts(any()) } returns emptyList()

        val result = module.createContact("Alice", listOf("a@b.c"), emptyList(), emptyList(), "Co", "Addr", "Dept")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "contact_created", mapOf("name" to "Alice")) }
    }

    @Test
    fun `createContact without contact service returns failure`() {
        val moduleNoContact = SyncDataModuleImpl(
            syncClient = syncClient,
            messageService = messageService,
            contactService = null,
            analytics = analytics,
            repository = repository,
            transactionManager = transactionManager,
            session = session,
            authStateProvider = { authState },
        )

        val result = moduleNoContact.createContact("A", emptyList(), emptyList(), emptyList(), "", "", "")

        assertTrue(result.isFailure)
        assertEquals("Contact service not available", result.exceptionOrNull()?.message)
    }

    @Test
    fun `setSearchQuery updates state and triggers loadData`() {
        every { messageService.listMessages(query = "hello") } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.setSearchQuery("hello")

        assertEquals("hello", module.syncDataState.searchQuery)
        verify { messageService.listMessages(query = "hello") }
    }

    @Test
    fun `loadMessages with blank query passes null`() {
        every { messageService.listMessages(query = null) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.loadMessages()

        verify { messageService.listMessages(query = null) }
    }

    @Test
    fun `loadContacts without contactService is no-op`() {
        val moduleNoContact = SyncDataModuleImpl(
            syncClient = syncClient,
            messageService = messageService,
            contactService = null,
            analytics = analytics,
            repository = repository,
            transactionManager = transactionManager,
            session = session,
            authStateProvider = { authState },
        )

        moduleNoContact.loadContacts()

        verify(exactly = 0) { contactService.listContacts(any()) }
    }

    @Test
    fun `resolveConflict success`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.MINE) } returns Unit
        every { messageService.listMessages(any()) } returns
            PagedResult(items = emptyList(), metadata = PaginationMetadata(1, 100, 0))

        module.resolveConflict("s1", ConflictStrategy.MINE)

        verify { messageService.resolveConflict("s1", ConflictStrategy.MINE) }
    }

    @Test
    fun `resolveConflict failure fires error`() {
        every { messageService.resolveConflict("s1", ConflictStrategy.SERVER) } throws
            RuntimeException("No conflict data")

        val listener = mockk<SyncDataListener>(relaxed = true)
        module.addListener(listener)

        module.resolveConflict("s1", ConflictStrategy.SERVER)

        verify { listener.onSyncError("resolveConflict", "No conflict data") }
    }

    @Test
    fun `connectivity observer updates isOnline state`() {
        val listener = mockk<SyncDataListener>(relaxed = true)
        module.addListener(listener)

        connectivityObserver?.invoke(false)

        assertFalse(module.syncDataState.isOnline)
        verify { listener.onSyncDataStateChanged(any()) }

        connectivityObserver?.invoke(true)

        assertTrue(module.syncDataState.isOnline)
    }

    @Test
    fun `initial state has defaults`() {
        val freshModule = SyncDataModuleImpl(
            syncClient = syncClient,
            messageService = messageService,
            contactService = null,
            analytics = analytics,
            repository = repository,
            transactionManager = transactionManager,
            session = session,
            authStateProvider = { AuthState() },
        )

        assertFalse(freshModule.syncDataState.isSyncing)
        assertTrue(freshModule.syncDataState.isOnline)
        assertEquals("", freshModule.syncDataState.searchQuery)
        assertTrue(freshModule.syncDataState.messages.isEmpty())
        assertTrue(freshModule.syncDataState.contacts.isEmpty())
    }
}
```

- [ ] **Step 4: Run SyncDataModuleTest**

Run: `mvn -pl platform-sync-client test -Dtest=SyncDataModuleTest -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS (20 tests)

- [ ] **Step 5: Create `ProfileModuleTest.kt`, `AdminModuleTest.kt`, `NotificationModuleTest.kt`**

These follow the same pattern — migrate tests from the corresponding DesktopSyncEngine test files. Each creates the module impl with mocked dependencies and replicates the same assertions.

Create `ProfileModuleTest.kt` (migrating `DesktopSyncEngineProfileTest` — 9 tests):

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserProfileResponse
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfileModuleTest {
    private lateinit var profileClient: ProfileClient
    private lateinit var analytics: AnalyticsService
    private lateinit var notifier: ModuleNotifier
    private var authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")
    private lateinit var module: ProfileModule

    @BeforeEach
    fun setUp() {
        profileClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        notifier = mockk(relaxed = true)
        module = ProfileModuleImpl(
            profileClient = profileClient,
            analytics = analytics,
            authStateProvider = { authState },
            onLoadData = {},
            onStopAutoSync = {},
            onLogout = {},
            notifier = notifier,
        )
    }

    @Test
    fun `loadProfile success`() {
        every { profileClient.fetchProfile() } returns UserProfileResponse("alice", "a@b.c", "http://avatar", true, false)

        module.loadProfile()

        assertEquals("a@b.c", module.profileState.userEmail)
        assertEquals("http://avatar", module.profileState.userAvatarUrl)
        assertTrue(module.profileState.emailNotificationsEnabled)
        assertFalse(module.profileState.pushNotificationsEnabled)
    }

    @Test
    fun `loadProfile session expired`() {
        val listener = mockk<ProfileListener>(relaxed = true)
        module.addListener(listener)
        every { profileClient.fetchProfile() } throws SessionExpiredException()

        module.loadProfile()

        verify { listener.onSessionExpired() }
    }

    @Test
    fun `updateProfile success`() {
        every { profileClient.updateProfile("new@b.c", any(), any()) } returns Unit
        every { profileClient.fetchProfile() } returns UserProfileResponse("user", "new@b.c", null, true, true)

        val result = module.updateProfile("new@b.c")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "profile_updated") }
        verify { notifier.notifySuccess("Profile updated") }
    }

    @Test
    fun `updateProfile session expired`() {
        every { profileClient.updateProfile("x@b.c", any(), any()) } throws SessionExpiredException()

        val result = module.updateProfile("x@b.c")

        assertTrue(result.isFailure)
    }

    @Test
    fun `updateNotificationPreferences success`() {
        val result = module.updateNotificationPreferences(emailEnabled = false, pushEnabled = true)

        assertTrue(result.isSuccess)
        assertFalse(module.profileState.emailNotificationsEnabled)
        assertTrue(module.profileState.pushNotificationsEnabled)
    }

    @Test
    fun `updateNotificationPreferences session expired`() {
        every { profileClient.updateNotificationPreferences(any(), any()) } throws SessionExpiredException()

        val result = module.updateNotificationPreferences(true, true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteAccount success`() {
        var logoutCalled = false
        val mod = ProfileModuleImpl(
            profileClient = profileClient,
            analytics = analytics,
            authStateProvider = { authState },
            onLoadData = {},
            onStopAutoSync = {},
            onLogout = { logoutCalled = true },
            notifier = notifier,
        )

        val result = mod.deleteAccount("secret")

        assertTrue(result.isSuccess)
        verify { profileClient.deleteAccount("secret") }
        verify { analytics.track("user", "account_deleted") }
        verify { notifier.notifySuccess("Account deleted") }
        assertTrue(logoutCalled)
    }

    @Test
    fun `deleteAccount failure`() {
        every { profileClient.deleteAccount("secret") } throws RuntimeException("Fail")

        val result = module.deleteAccount("secret")

        assertTrue(result.isFailure)
        verify { notifier.notifyFailure("Account deletion failed: Fail") }
    }

    @Test
    fun `changePassword success`() {
        val result = module.changePassword("old", "new")

        assertTrue(result.isSuccess)
        verify { profileClient.changePassword("old", "new") }
        verify { analytics.track("user", "password_changed") }
    }
}
```

> **Note:** The `changePassword` test above requires `ProfileModule` to expose `changePassword`. Since we placed `changePassword` on `AuthModule` in the interface design, move this test to `AuthModuleTest` instead (it's already there from Step 1). Delete this test from `ProfileModuleTest`.

Create `AdminModuleTest.kt` (migrating `DesktopSyncEngineAdminTest` — 5 tests):

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminModuleTest {
    private lateinit var adminClient: AdminClient
    private lateinit var analytics: AnalyticsService
    private var authState = AuthState(isLoggedIn = true, userName = "user", userRole = "USER")
    private lateinit var module: AdminModule

    @BeforeEach
    fun setUp() {
        adminClient = mockk(relaxed = true)
        analytics = mockk(relaxed = true)
        module = AdminModuleImpl(
            adminClient = adminClient,
            analytics = analytics,
            authStateProvider = { authState },
            onStopAutoSync = {},
            onLogout = {},
        )
    }

    @Test
    fun `loadUsers success updates state`() {
        val users = listOf(UserSummary("1", "alice", "a@b.c", UserRole.USER, true))
        every { adminClient.listUsers() } returns users

        module.loadUsers()

        assertEquals(1, module.adminState.adminUsers.size)
        assertEquals("alice", module.adminState.adminUsers[0].username)
    }

    @Test
    fun `loadUsers session expired fires session expired`() {
        every { adminClient.listUsers() } throws SessionExpiredException()
        val listener = mockk<AdminListener>(relaxed = true)
        module.addListener(listener)

        module.loadUsers()

        verify { listener.onSessionExpired() }
    }

    @Test
    fun `setUserEnabled success`() {
        every { adminClient.listUsers() } returns emptyList()

        val result = module.setUserEnabled("1", false)

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_enabled_changed", mapOf("userId" to "1", "enabled" to false)) }
    }

    @Test
    fun `setUserEnabled session expired`() {
        every { adminClient.setUserEnabled("1", true) } throws SessionExpiredException()

        val result = module.setUserEnabled("1", true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `setUserRole success`() {
        every { adminClient.listUsers() } returns emptyList()

        val result = module.setUserRole("1", "ADMIN")

        assertTrue(result.isSuccess)
        verify { analytics.track("user", "user_role_changed", mapOf("userId" to "1", "role" to "ADMIN")) }
    }

    @Test
    fun `setUserRole failure`() {
        every { adminClient.setUserRole("1", "ADMIN") } throws RuntimeException("Fail")

        val result = module.setUserRole("1", "ADMIN")

        assertTrue(result.isFailure)
    }
}
```

Create `NotificationModuleTest.kt` (migrating `DesktopSyncEngineNotificationTest` — 6 tests):

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationModuleTest {
    private lateinit var notificationClient: NotificationClient
    private lateinit var module: NotificationModule

    @BeforeEach
    fun setUp() {
        notificationClient = mockk(relaxed = true)
        module = NotificationModuleImpl(
            notificationClient = notificationClient,
            onStopAutoSync = {},
            onLogout = {},
        )
    }

    @Test
    fun `loadNotifications success`() {
        val notifs = listOf(NotificationSummary("n1", "Title", "Body", "INFO", false, "2025-01-01"))
        every { notificationClient.listNotifications() } returns notifs

        module.loadNotifications()

        assertEquals(1, module.notificationState.notifications.size)
        assertEquals("n1", module.notificationState.notifications[0].id)
    }

    @Test
    fun `loadNotifications session expired`() {
        every { notificationClient.listNotifications() } throws SessionExpiredException()
        val listener = mockk<NotificationListener>(relaxed = true)
        module.addListener(listener)

        module.loadNotifications()

        verify { listener.onSessionExpired() }
    }

    @Test
    fun `markNotificationRead reloads notifications`() {
        every { notificationClient.listNotifications() } returns emptyList()

        module.markNotificationRead("n1")

        verify { notificationClient.markNotificationRead("n1") }
        verify { notificationClient.listNotifications() }
    }

    @Test
    fun `markAllNotificationsRead reloads notifications`() {
        every { notificationClient.listNotifications() } returns emptyList()

        module.markAllNotificationsRead()

        verify { notificationClient.markAllNotificationsRead() }
        verify { notificationClient.listNotifications() }
    }

    @Test
    fun `markNotificationRead session expired`() {
        every { notificationClient.markNotificationRead("n1") } throws SessionExpiredException()
        val listener = mockk<NotificationListener>(relaxed = true)
        module.addListener(listener)

        module.markNotificationRead("n1")

        verify { listener.onSessionExpired() }
    }

    @Test
    fun `unreadCount counts unread`() {
        every { notificationClient.listNotifications() } returns listOf(
            NotificationSummary("1", "A", "B", "INFO", false, "2025-01-01"),
            NotificationSummary("2", "C", "D", "INFO", true, "2025-01-01"),
            NotificationSummary("3", "E", "F", "INFO", false, "2025-01-01"),
        )

        module.loadNotifications()

        assertEquals(2, module.notificationState.unreadCount)
    }
}
```

- [ ] **Step 6: Run all module tests**

Run: `mvn -pl platform-sync-client test -Dtest="AuthModuleTest,SyncDataModuleTest,ProfileModuleTest,AdminModuleTest,NotificationModuleTest" -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS

- [ ] **Step 7: Delete old DesktopSyncEngine test files and SyncServiceTest**

Delete the 9 old test files (`DesktopSyncEngineTestBase.kt`, `DesktopSyncEngineAuthTest.kt`, `DesktopSyncEngineSyncTest.kt`, `DesktopSyncEngineDataTest.kt`, `DesktopSyncEngineProfileTest.kt`, `DesktopSyncEngineAdminTest.kt`, `DesktopSyncEngineNotificationTest.kt`, `DesktopSyncEngineListenerTest.kt`, `SyncServiceTest.kt`).

Run: `mvn -pl platform-sync-client test -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS (no old tests remain, new module + client tests pass)

- [ ] **Step 8: Commit**

```bash
git add -A platform-sync-client/src/test/
git commit -m "test(sync): migrate DesktopSyncEngine tests to focused module tests"
```

---

## Task 6: Update DI wiring and delete old files

**Files:**
- Modify: `platform-sync-client/src/main/kotlin/.../di/ApiClientModule.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../sync/engine/SyncEngine.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../sync/engine/DesktopSyncEngine.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../sync/engine/EngineState.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../sync/engine/EngineListener.kt`
- Delete: `platform-sync-client/src/main/kotlin/.../sync/SyncService.kt`
- Delete: `platform-core/src/main/kotlin/.../service/SyncProvider.kt`
- Modify: `platform-sync-client/src/test/.../di/KoinModuleTest.kt`

- [ ] **Step 1: Update `ApiClientModule.kt`**

Replace the current content with:

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.client.AdminClient
import io.github.rygel.outerstellar.platform.sync.client.ApiSession
import io.github.rygel.outerstellar.platform.sync.client.AuthClient
import io.github.rygel.outerstellar.platform.sync.client.HttpAdminClient
import io.github.rygel.outerstellar.platform.sync.client.HttpAuthClient
import io.github.rygel.outerstellar.platform.sync.client.HttpNotificationClient
import io.github.rygel.outerstellar.platform.sync.client.HttpProfileClient
import io.github.rygel.outerstellar.platform.sync.client.HttpSyncClient
import io.github.rygel.outerstellar.platform.sync.client.NotificationClient
import io.github.rygel.outerstellar.platform.sync.client.ProfileClient
import io.github.rygel.outerstellar.platform.sync.client.SyncClient
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModuleImpl
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModuleImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module

val apiClientModule
    get() = module {
        single { ApiSession() }
        single<AuthClient> { HttpAuthClient(get(named("serverBaseUrl")), get(), get()) }
        single<SyncClient> { HttpSyncClient(get(named("serverBaseUrl")), get(), get()) }
        single<ProfileClient> { HttpProfileClient(get(named("serverBaseUrl")), get(), get()) }
        single<AdminClient> { HttpAdminClient(get(named("serverBaseUrl")), get(), get()) }
        single<NotificationClient> { HttpNotificationClient(get(named("serverBaseUrl")), get(), get()) }

        single<AuthModule> {
            AuthModuleImpl(
                authClient = get(),
                analytics = get(),
                onLoadData = { get<SyncDataModule>().loadData() },
                onStartAutoSync = { get<SyncDataModule>().startAutoSync() },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                notifier = getOrNull(),
            )
        }
        single<SyncDataModule> {
            SyncDataModuleImpl(
                syncClient = get(),
                messageService = get(),
                contactService = getOrNull(),
                analytics = get(),
                repository = get(),
                transactionManager = get(),
                session = get(),
                authStateProvider = { get<AuthModule>().authState },
                notifier = getOrNull(),
                connectivityChecker = getOrNull(),
            )
        }
        single<ProfileModule> {
            ProfileModuleImpl(
                profileClient = get(),
                analytics = get(),
                authStateProvider = { get<AuthModule>().authState },
                onLoadData = { get<SyncDataModule>().loadData() },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
                notifier = getOrNull(),
            )
        }
        single<AdminModule> {
            AdminModuleImpl(
                adminClient = get(),
                analytics = get(),
                authStateProvider = { get<AuthModule>().authState },
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
            )
        }
        single<NotificationModule> {
            NotificationModuleImpl(
                notificationClient = get(),
                onStopAutoSync = { get<SyncDataModule>().stopAutoSync() },
                onLogout = { get<AuthModule>().logout() },
            )
        }
    }
```

- [ ] **Step 2: Delete old files**

Delete:
- `platform-sync-client/.../sync/engine/SyncEngine.kt`
- `platform-sync-client/.../sync/engine/DesktopSyncEngine.kt`
- `platform-sync-client/.../sync/engine/EngineState.kt`
- `platform-sync-client/.../sync/engine/EngineListener.kt`
- `platform-sync-client/.../sync/SyncService.kt`
- `platform-core/.../service/SyncProvider.kt`

- [ ] **Step 3: Update `KoinModuleTest.kt` in platform-sync-client**

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.sync.client.ApiSession
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModuleTest : KoinTest {

    @Test
    fun `sync-client modules should be valid`() {
        apiClientModule.verify(
            extraTypes =
                listOf(
                    String::class,
                    MessageRepository::class,
                    OutboxRepository::class,
                    MessageCache::class,
                    TransactionManager::class,
                    io.github.rygel.outerstellar.platform.service.MessageService::class,
                    io.github.rygel.outerstellar.platform.analytics.AnalyticsService::class,
                    io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker::class,
                    io.github.rygel.outerstellar.platform.sync.engine.module.ModuleNotifier::class,
                    io.github.rygel.outerstellar.platform.service.ContactService::class,
                )
        )
    }
}
```

- [ ] **Step 4: Run sync-client module build**

Run: `mvn -pl platform-sync-client clean test -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add -A platform-sync-client/ platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/SyncProvider.kt
git commit -m "refactor(sync): delete SyncEngine, DesktopSyncEngine, SyncService, SyncProvider; wire new modules in DI"
```

---

## Task 7: Update platform-desktop consumers

**Files:**
- Modify: `platform-desktop/src/main/kotlin/.../di/DesktopModule.kt`
- Modify: `platform-desktop/src/main/kotlin/.../swing/viewmodel/SyncViewModel.kt`
- Modify: `platform-desktop/src/main/kotlin/.../swing/SwingSyncApp.kt`
- Modify: `platform-desktop/src/test/.../di/KoinModuleTest.kt`

This is the final wiring step. SyncViewModel now depends on 5 module interfaces instead of 1 SyncEngine. The `updateContact` bypass (calling ContactService directly) is closed — it now goes through `SyncDataModule.updateContact()`.

- [ ] **Step 1: Update `SyncViewModel.kt`**

Replace the entire file. The key changes:
- Constructor takes 5 module interfaces instead of SyncEngine + ContactService
- Each module's listener updates the corresponding `@Volatile` properties
- `updateContact()` delegates to `syncDataModule.updateContact()` instead of calling ContactService directly
- The 16 SwingWorker instances remain but now call the appropriate module method

```kotlin
@file:Suppress("TooManyFunctions")

package io.github.rygel.outerstellar.platform.swing.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.OuterstellarException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminListener
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthListener
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationListener
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileListener
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataListener
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingWorker
import javax.swing.Timer

class SyncViewModel(
    private val authModule: AuthModule,
    private val syncDataModule: SyncDataModule,
    private val profileModule: ProfileModule,
    private val adminModule: AdminModule,
    private val notificationModule: NotificationModule,
    private var i18nService: I18nService,
) {
    private val observers = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isOnline: Boolean = syncDataModule.syncDataState.isOnline
        private set

    @Volatile
    var messages = syncDataModule.syncDataState.messages
        private set

    @Volatile
    var contacts = syncDataModule.syncDataState.contacts
        private set

    @Volatile
    var status: String = i18nService.translate("swing.status.ready")
        private set

    @Volatile
    var isSyncing: Boolean = syncDataModule.syncDataState.isSyncing
        private set

    @Volatile
    var userName: String = authModule.authState.userName
        private set

    @Volatile
    var isLoggedIn: Boolean = authModule.authState.isLoggedIn
        private set

    @Volatile
    var userRole: String? = authModule.authState.userRole
        private set

    @Volatile
    var adminUsers = adminModule.adminState.adminUsers
        private set

    @Volatile
    var notifications = notificationModule.notificationState.notifications
        private set

    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }

    @Volatile
    var userEmail: String = profileModule.profileState.userEmail
        private set

    @Volatile
    var userAvatarUrl: String? = profileModule.profileState.userAvatarUrl
        private set

    @Volatile
    var emailNotificationsEnabled: Boolean = profileModule.profileState.emailNotificationsEnabled
        private set

    @Volatile
    var pushNotificationsEnabled: Boolean = profileModule.profileState.pushNotificationsEnabled
        private set

    var author: String = i18nService.translate("swing.author.default")
    var content: String = ""
    var searchQuery: String = ""
        set(value) {
            if (field == value) return
            field = value
            searchDebounceTimer?.stop()
            searchDebounceTimer =
                Timer(300) { loadMessages() }
                    .apply {
                        isRepeats = false
                        start()
                    }
        }

    private var searchDebounceTimer: Timer? = null

    init {
        syncDataModule.addListener(object : SyncDataListener {
            override fun onSyncDataStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataState) {
                isOnline = state.isOnline
                messages = state.messages
                contacts = state.contacts
                isSyncing = state.isSyncing
                if (state.syncStatus.isNotBlank()) {
                    status = state.syncStatus
                }
                notifyObservers()
            }
        })

        authModule.addListener(object : AuthListener {
            override fun onAuthStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.AuthState) {
                isLoggedIn = state.isLoggedIn
                userName = state.userName
                userRole = state.userRole
                notifyObservers()
            }

            override fun onSessionExpired() {
                isLoggedIn = false
                userName = ""
                userRole = null
                status = i18nService.translate("swing.session.expired")
                notifyObservers()
            }
        })

        profileModule.addListener(object : ProfileListener {
            override fun onProfileStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.ProfileState) {
                userEmail = state.userEmail
                userAvatarUrl = state.userAvatarUrl
                emailNotificationsEnabled = state.emailNotificationsEnabled
                pushNotificationsEnabled = state.pushNotificationsEnabled
                notifyObservers()
            }
        })

        adminModule.addListener(object : AdminListener {
            override fun onAdminStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.AdminState) {
                adminUsers = state.adminUsers
                notifyObservers()
            }
        })

        notificationModule.addListener(object : NotificationListener {
            override fun onNotificationStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.NotificationState) {
                notifications = state.notifications
                notifyObservers()
            }
        })
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    private fun notifyObservers() {
        observers.forEach { it() }
    }

    fun refreshTranslations(newI18n: I18nService) {
        this.i18nService = newI18n
        status = i18nService.translate("swing.status.ready")
        loadMessages()
    }

    fun loadMessages() {
        syncDataModule.loadMessages()
    }

    fun createMessage(onValidationError: (String) -> Unit) {
        val result = syncDataModule.createLocalMessage(author, content)
        if (result.isSuccess) {
            content = ""
            status = i18nService.translate("swing.status.created")
            notifyObservers()
        } else {
            val ex = result.exceptionOrNull()
            when (ex) {
                is ValidationException ->
                    onValidationError(ex.message ?: i18nService.translate("swing.validation.messageRequired"))
                is OuterstellarException -> onValidationError(ex.message ?: "Action failed")
                else -> onValidationError(ex?.message ?: "Action failed")
            }
        }
    }

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
        onValidationError: (String) -> Unit,
    ) {
        val result = syncDataModule.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
        if (result.isSuccess) {
            status = i18nService.translate("swing.status.created")
        } else {
            onValidationError(result.exceptionOrNull()?.message ?: "Action failed")
        }
    }

    fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
        onValidationError: (String) -> Unit,
    ) {
        val result = syncDataModule.updateContact(syncId, name, emails, phones, socialMedia, company, companyAddress, department)
        if (result.isSuccess) {
            status = i18nService.translate("swing.status.contactUpdated")
        } else {
            onValidationError(result.exceptionOrNull()?.message ?: "Action failed")
        }
    }

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.login(user, pass)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = i18nService.translate("swing.status.loggedIn", userName)
                        author = userName
                    }
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun register(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.register(user, pass)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = i18nService.translate("swing.status.registered", userName)
                        author = userName
                    }
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun logout() {
        authModule.logout()
        author = i18nService.translate("swing.author.default")
        status = i18nService.translate("swing.status.loggedOut")
        notifyObservers()
    }

    fun startAutoSync() {
        syncDataModule.startAutoSync()
    }

    fun stopAutoSync() {
        syncDataModule.stopAutoSync()
    }

    fun sync(isAuto: Boolean = false) {
        if (isSyncing) return
        if (!isOnline) {
            status = i18nService.translate("swing.status.offline")
            notifyObservers()
            return
        }

        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = syncDataModule.sync(isAuto)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = syncDataModule.syncDataState.syncStatus
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown error"
                        status = i18nService.translate("swing.status.failed", msg)
                    }
                    notifyObservers()
                }
            }
            .execute()
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.changePassword(currentPassword, newPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadUsers() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.loadUsers()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserEnabled(userId: String, currentEnabled: Boolean) {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.setUserEnabled(userId, !currentEnabled)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserRole(userId: String, currentRole: String) {
        val newRole = if (currentRole == UserRole.ADMIN.name) UserRole.USER.name else UserRole.ADMIN.name
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.setUserRole(userId, newRole)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadNotifications() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.loadNotifications()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun markNotificationRead(notificationId: String) {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.markNotificationRead(notificationId)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun markAllNotificationsRead() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.markAllNotificationsRead()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadProfile(onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    val listener =
                        object : SyncDataListener {
                            override fun onSyncError(operation: String, message: String) {
                                if (operation == "loadProfile") notifyObservers()
                            }
                        }
                    syncDataModule.addListener(listener)
                    try {
                        profileModule.loadProfile()
                    } finally {
                        syncDataModule.removeListener(listener)
                    }
                    val loggedIn = authModule.authState.isLoggedIn
                    return if (!loggedIn) false to status
                    else true to null
                }

                override fun done() {
                    val (success, error) =
                        try {
                            get()
                        } catch (e: Exception) {
                            false to (e.message ?: "Failed to load profile")
                        }
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun updateProfile(email: String, username: String?, avatarUrl: String?, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = profileModule.updateProfile(email, username, avatarUrl)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun updateNotificationPreferences(
        emailEnabled: Boolean,
        pushEnabled: Boolean,
        onResult: (Boolean, String?) -> Unit,
    ) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> =
                    profileModule.updateNotificationPreferences(emailEnabled, pushEnabled)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun deleteAccount(currentPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = profileModule.deleteAccount(currentPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun requestPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.requestPasswordReset(email)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resetPassword(token: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.resetPassword(token, newPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        syncDataModule.resolveConflict(syncId, strategy)
        status = i18nService.translate("swing.status.conflictResolved", strategy.name)
        notifyObservers()
    }
}
```

- [ ] **Step 2: Update `DesktopModule.kt`**

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.swing.SwingAppConfig
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.swing.analytics.PersistentBatchingAnalyticsService
import io.github.rygel.outerstellar.platform.swing.viewmodel.SyncViewModel
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import java.nio.file.Path
import org.koin.core.qualifier.named
import org.koin.dsl.module

val desktopModule
    get() = module {
        single { DesktopAppConfig.fromEnvironment() }
        single(named("jdbcUrl")) { get<SwingAppConfig>().jdbcUrl }
        single(named("serverBaseUrl")) { get<SwingAppConfig>().serverBaseUrl }

        single<AnalyticsService> {
            val cfg = get<SwingAppConfig>()
            if (cfg.analyticsEnabled && cfg.segmentWriteKey.isNotBlank())
                PersistentBatchingAnalyticsService(
                    writeKey = cfg.segmentWriteKey,
                    dataDir = Path.of("./data"),
                    maxFileSizeBytes = cfg.analyticsMaxFileSizeKb * 1024,
                    maxEventAgeDays = cfg.analyticsMaxEventAgeDays,
                )
            else NoOpAnalyticsService()
        }
        single { SyncViewModel(get(), get(), get(), get(), get(), get()) }
        single { SystemTrayNotifier(get()) }
        single<I18nService> { I18nService.create("messages") }
    }
```

- [ ] **Step 3: Update `SwingSyncApp.kt`**

Find all references to `SyncEngine`, `DesktopSyncEngine`, `EngineListener`, `EngineState`, `EngineNotifier`, `SyncService` and replace with the new module types. The `swingRuntimeModules()` function must include `apiClientModule` instead of wiring SyncService directly. The `DesktopComponent` object and `SyncWindow` must use `AuthModule`, `SyncDataModule`, `ProfileModule`, `AdminModule`, `NotificationModule` as needed.

The specific changes depend on how `SwingSyncApp.kt` uses `SyncEngine` — check the imports and replace:
- `SyncEngine` references → use the specific module interface needed for each operation
- `EngineListener` → register with individual module listeners
- `EngineState` → read from individual module states
- `SyncService` import → remove (no longer used directly)

- [ ] **Step 4: Update `KoinModuleTest.kt` in platform-desktop**

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import org.junit.jupiter.api.Test
import org.koin.test.KoinTest
import org.koin.test.verify.verify

class KoinModuleTest : KoinTest {

    @Test
    fun `desktop application modules should be valid`() {
        desktopModule.verify(
            extraTypes =
                listOf(
                    AuthModule::class,
                    SyncDataModule::class,
                    ProfileModule::class,
                    AdminModule::class,
                    NotificationModule::class,
                    MessageService::class,
                    I18nService::class,
                    SystemTrayNotifier::class,
                    DesktopAppConfig::class,
                    String::class,
                    Boolean::class,
                    Int::class,
                )
        )
    }
}
```

- [ ] **Step 5: Run platform-desktop compile check**

Run: `mvn -pl platform-desktop compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add -A platform-desktop/
git commit -m "refactor(desktop): wire SyncViewModel to new module interfaces, close ContactService bypass"
```

---

## Task 8: Update desktop tests and verify full build

**Files:**
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelAuthTest.kt`
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelProfileTest.kt`
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelAdminOperationsTest.kt`
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelSessionExpiryTest.kt`
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelPasswordResetTest.kt`
- Modify: `platform-desktop/src/test/.../swing/SyncViewModelSearchTest.kt`

Each test file currently creates a `SyncEngine` mock. It must be updated to create mocks of the 5 module interfaces instead and pass them to the new `SyncViewModel` constructor.

- [ ] **Step 1: Update desktop test files**

In each test file:
- Replace `SyncEngine` mock with appropriate module interface mocks (`AuthModule`, `SyncDataModule`, `ProfileModule`, `AdminModule`, `NotificationModule`)
- Update the `SyncViewModel` constructor call from `(engine, i18n, contactService?)` to `(authModule, syncDataModule, profileModule, adminModule, notificationModule, i18n)`
- Replace `engine.*` calls with `authModule.*`, `syncDataModule.*`, `profileModule.*`, etc.
- Remove `ContactService` direct mock from `SyncViewModel` constructor

- [ ] **Step 2: Run full build excluding desktop UI tests**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS

- [ ] **Step 3: Run platform-desktop compile (tests run in Podman only)**

Run: `mvn -pl platform-desktop test-compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add -A platform-desktop/src/test/
git commit -m "test(desktop): update SyncViewModel tests to use new module interfaces"
```

---

## Task 9: Final cleanup and verification

- [ ] **Step 1: Search for stale imports**

Search the entire codebase for references to deleted types:
- `SyncEngine` (interface)
- `DesktopSyncEngine`
- `EngineState`
- `EngineListener`
- `EngineNotifier`
- `SyncService` (the concrete class, NOT the client interfaces)
- `SyncProvider`

Run: `rg "import.*SyncEngine|import.*DesktopSyncEngine|import.*EngineState|import.*EngineListener|import.*EngineNotifier|import.*SyncService\b|import.*SyncProvider" platform-core/ platform-sync-client/ platform-desktop/ platform-web/`

Fix any remaining references.

- [ ] **Step 2: Run quality checks**

Run: `mvn -pl platform-sync-client,platform-desktop verify -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true`
Expected: ALL PASS (desktop tests run in Podman per project rules)

- [ ] **Step 3: Run full reactor build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jooq,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: final cleanup after SyncEngine split — remove stale references"
```
