# TOTP Two-Factor Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional TOTP two-factor authentication to the web application — secret generation, QR code setup, two-step login, backup codes.

**Architecture:** Two-step login with in-memory partial-auth tokens. `dev.samstevens.totp:totp` for TOTP logic. Settings tab for setup.

**Tech Stack:** Kotlin, http4k, JTE, HTMX, dev.samstevens.totp, ZXing, Flyway, jOOQ/JDBI

---

## File Map

### New files:
- `platform-persistence-jooq/src/main/resources/db/migration/V9__add_totp.sql`
- `platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/TOTPService.kt`
- `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/TotpModels.kt`
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPRoutes.kt`
- `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPApiRoutes.kt`
- `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/TotpChallengeForm.kte`
- `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/TotpSetupFragment.kte`
- `platform-web/src/test/kotlin/.../web/TOTPRoutesTest.kt`
- `platform-security/src/test/kotlin/.../security/TOTPServiceTest.kt`
- `platform-security/src/test/kotlin/.../security/SecurityServiceTotpTest.kt`

### Modified files:
- `pom.xml` — add `dev.samstevens.totp:totp:1.7.1` dependency and ZXing
- `platform-security/.../security/Models.kt` — User fields, AuthResult, UserRepository
- `platform-security/.../security/SecurityService.kt` — partial auth, TOTP verification
- `platform-security/.../security/SecurityModule.kt` — register TOTPService
- `platform-persistence-jooq/.../persistence/JooqUserRepository.kt` — TOTP methods
- `platform-persistence-jdbi/.../persistence/JdbiUserRepository.kt` — TOTP methods
- `platform-security/.../security/CachingUserRepository.kt` — delegate TOTP methods
- `platform-web/.../web/App.kt` — register TOTP routes
- `platform-web/.../web/AuthRoutes.kt` — TOTP challenge in login form
- `platform-web/.../web/AuthApi.kt` — TOTP verify endpoint in login
- `platform-web/.../web/SettingsPageFactory.kt` — security tab
- `platform-web/.../web/ViewModels.kt` — TOTP fields on SettingsPage
- `platform-web/.../auth/AuthPageFactory.kt` — totp_required mode
- `platform-web/src/main/jte/.../web/AuthPage.kte` — TOTP code input
- `platform-web/src/main/jte/.../web/SettingsPage.kte` — security tab

---

### Task 1: Flyway migration + User model + repository changes

**Files:**
- Create: `platform-persistence-jooq/src/main/resources/db/migration/V9__add_totp.sql`
- Modify: `platform-security/.../security/Models.kt`
- Modify: `platform-persistence-jooq/.../persistence/JooqUserRepository.kt`
- Modify: `platform-persistence-jdbi/.../persistence/JdbiUserRepository.kt`
- Modify: `platform-security/.../security/CachingUserRepository.kt`

- [ ] **Step 1: Create V9 migration**

```sql
-- V9__add_totp.sql
ALTER TABLE plt_users ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE plt_users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE plt_users ADD COLUMN totp_backup_codes TEXT;
```

- [ ] **Step 2: Add TOTP fields to User model**

Read current `Models.kt`, add fields to `User`:
```kotlin
data class User(
    // ... existing fields ...
    val totpSecret: String? = null,
    val totpEnabled: Boolean = false,
    val totpBackupCodes: String? = null,
)
```

- [ ] **Step 3: Add TOTP methods to UserRepository**

```kotlin
interface UserRepository {
    // ... existing methods ...
    fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>?
    fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?)
    fun enableTotp(userId: UUID)
}
```

- [ ] **Step 4: Implement in JooqUserRepository**

```kotlin
override fun findTotpSecretByUserId(userId: UUID): Triple<String?, Boolean, String?>? {
    return dsl.select(PLT_USERS.TOTP_SECRET, PLT_USERS.TOTP_ENABLED, PLT_USERS.TOTP_BACKUP_CODES)
        .from(PLT_USERS)
        .where(PLT_USERS.ID.eq(userId))
        .fetchOne { record ->
            Triple(record[PLT_USERS.TOTP_SECRET], record[PLT_USERS.TOTP_ENABLED] ?: false, record[PLT_USERS.TOTP_BACKUP_CODES])
        }
}

override fun updateTotpSecret(userId: UUID, secret: String?, backupCodes: String?) {
    dsl.update(PLT_USERS)
        .set(PLT_USERS.TOTP_SECRET, secret)
        .set(PLT_USERS.TOTP_BACKUP_CODES, backupCodes)
        .where(PLT_USERS.ID.eq(userId))
        .execute()
}

override fun enableTotp(userId: UUID) {
    dsl.update(PLT_USERS)
        .set(PLT_USERS.TOTP_ENABLED, true)
        .where(PLT_USERS.ID.eq(userId))
        .execute()
}
```

- [ ] **Step 5: Implement in JdbiUserRepository** (similar pattern using JDBI `handle.createUpdate`)

- [ ] **Step 6: Implement in CachingUserRepository** (delegate to wrapped repo, invalidate cache on writes)

- [ ] **Step 7: Verify compilation**

Run: `mvn compile -pl platform-persistence-jooq,platform-persistence-jdbi,platform-security -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 8: Commit**

```bash
git add platform-persistence-jooq/src/main/resources/db/migration/V9__add_totp.sql platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt platform-persistence-jooq/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JooqUserRepository.kt platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiUserRepository.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/CachingUserRepository.kt
git commit -m "feat(totp): add DB migration, User model fields, repository methods for TOTP"
```

---

### Task 2: Add TOTP dependency and create TOTPService

**Files:**
- Modify: `pom.xml` (root)
- Create: `platform-security/.../security/TOTPService.kt`
- Modify: `platform-security/.../security/SecurityModule.kt`

- [ ] **Step 1: Add dependency to root pom.xml**

```xml
<!-- In dependencyManagement -->
<totp.version>1.7.1</totp.version>
<zxing.version>3.5.3</zxing.version>

<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
    <version>${totp.version}</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
    <version>${zxing.version}</version>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
    <version>${zxing.version}</version>
</dependency>
```

Add to `platform-security/pom.xml`:
```xml
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp</artifactId>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>core</artifactId>
</dependency>
<dependency>
    <groupId>com.google.zxing</groupId>
    <artifactId>javase</artifactId>
</dependency>
```

- [ ] **Step 2: Create TOTPService**

```kotlin
package io.github.rygel.outerstellar.platform.security

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.qr.ZxingPngQrGenerator
import dev.samstevens.totp.recovery.RecoveryCodeGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.util.Utils
import java.security.MessageDigest

class TOTPService {

    private val secretGenerator = DefaultSecretGenerator(32)
    private val codeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val timeProvider = SystemTimeProvider()
    private val codeVerifier = DefaultCodeVerifier(codeGenerator, timeProvider).apply {
        setTimePeriod(30)
        setAllowedTimePeriodDiscrepancy(1)
    }
    private val qrGenerator = ZxingPngQrGenerator()
    private val recoveryCodeGenerator = RecoveryCodeGenerator()

    fun generateSecret(): String = secretGenerator.generate()

    fun generateQrDataUri(secret: String, email: String): String {
        val data = QrData.Builder()
            .label(email)
            .secret(secret)
            .issuer("Outerstellar")
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()
        val imageData = qrGenerator.generate(data)
        return Utils.getDataUriForImage(imageData, qrGenerator.imageMimeType)
    }

    fun verifyCode(secret: String, code: String): Boolean =
        codeVerifier.isValidCode(secret, code)

    fun generateBackupCodes(): Pair<List<String>, String> {
        val codes = recoveryCodeGenerator.generateCodes(16).toList()
        val hashed = codes.map { hash(it) }
        return codes to serializeJson(hashed)
    }

    fun verifyBackupCode(code: String, hashedCodesJson: String): String? {
        val hashedCodes = parseJsonList(hashedCodesJson).toMutableList()
        val hashed = hash(code)
        val idx = hashedCodes.indexOfFirst { it == hashed }
        if (idx == -1) return null
        hashedCodes.removeAt(idx)
        return if (hashedCodes.isEmpty()) "" else serializeJson(hashedCodes)
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun serializeJson(list: List<String>): String =
        list.joinToString(",", "[", "]") { "\"$it\"" }

    private fun parseJsonList(json: String): List<String> =
        json.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
}
```

- [ ] **Step 3: Register TOTPService in SecurityModule**

```kotlin
single { TOTPService() }
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl platform-security -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 5: Commit**

```bash
git add pom.xml platform-security/pom.xml platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/TOTPService.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityModule.kt
git commit -m "feat(totp): add dev.samstevens.totp dependency and TOTPService"
```

---

### Task 3: Create TotpModels and add partial-auth to SecurityService

**Files:**
- Create: `platform-core/.../model/TotpModels.kt`
- Modify: `platform-security/.../security/Models.kt` (AuthResult)
- Modify: `platform-security/.../security/SecurityService.kt`

- [ ] **Step 1: Create TotpModels**

```kotlin
package io.github.rygel.outerstellar.platform.model

import kotlinx.serialization.Serializable

@Serializable
data class TotpVerifyRequest(val partialToken: String, val code: String)

@Serializable
data class TotpVerifyResponse(
    val status: String,  // "success", "invalid_code", "expired"
    val token: String? = null,
    val username: String? = null,
    val role: String? = null,
)

@Serializable
data class TotpSetupResponse(
    val secret: String,
    val qrDataUri: String,
)

@Serializable
data class TotpConfirmRequest(val secret: String, val code: String)

@Serializable
data class TotpConfirmResponse(
    val status: String,  // "success", "invalid_code"
    val backupCodes: List<String>? = null,
)

@Serializable
data class TotpDisableRequest(val password: String)
```

- [ ] **Step 2: Add AuthResult.TotpRequired to Models.kt**

```kotlin
sealed class AuthResult {
    data class Authenticated(val user: User, val sessionToken: String? = null) : AuthResult()
    data class TotpRequired(val token: String) : AuthResult()
    data class AuthenticationFailed(val reason: String) : AuthResult()
}
```

- [ ] **Step 3: Add partial-auth to SecurityService**

Read current `SecurityService.kt`, then:

```kotlin
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

data class PartialAuth(
    val userId: UUID,
    val createdAt: Instant,
    var attemptCount: Int = 0,
)

// Add to SecurityService class body:
private val partialAuthStore = ConcurrentHashMap<String, PartialAuth>()
private val random = SecureRandom()

private fun generatePartialAuthToken(userId: UUID): String {
    val token = "pt_" + random.generateSecureToken(32)
    partialAuthStore[token] = PartialAuth(userId = userId, createdAt = Instant.now())
    return token
}

fun verifyTotp(partialToken: String, code: String): TotpVerifyResponse {
    val partial = partialAuthStore[partialToken] ?: return TotpVerifyResponse("expired")
    if (Duration.between(partial.createdAt, Instant.now()).toMinutes() >= 5) {
        partialAuthStore.remove(partialToken)
        return TotpVerifyResponse("expired")
    }
    if (partial.attemptCount >= 5) {
        partialAuthStore.remove(partialToken)
        return TotpVerifyResponse("expired")
    }
    partial.attemptCount++

    val user = userRepository.findById(partial.userId) ?: return TotpVerifyResponse("expired")
    val secret = user.totpSecret ?: return TotpVerifyResponse("expired")

    if (totpService.verifyCode(secret, code)) {
        partialAuthStore.remove(partialToken)
        val session = createSession(user.id)
        return TotpVerifyResponse("success", token = session.token, username = user.username, role = user.role.name)
    }

    // Try backup codes
    if (user.totpBackupCodes != null) {
        val updatedCodes = totpService.verifyBackupCode(code, user.totpBackupCodes)
        if (updatedCodes != null) {
            userRepository.updateTotpSecret(user.id, user.totpSecret, updatedCodes.ifEmpty { null })
            partialAuthStore.remove(partialToken)
            val session = createSession(user.id)
            return TotpVerifyResponse("success", token = session.token, username = user.username, role = user.role.name)
        }
    }

    return TotpVerifyResponse("invalid_code")
}
```

Also modify `authenticate()`:
```kotlin
fun authenticate(username: String, password: String): AuthResult {
    // ... existing checks ...
    val user = userRepository.findByUsername(username)
    // ... password check, lockout check ...
    if (user.totpEnabled) {
        return AuthResult.TotpRequired(generatePartialAuthToken(user.id))
    }
    // ... existing session creation ...
}

// Helper: SecureRandom token generation
private fun SecureRandom.generateSecureToken(length: Int): String {
    val bytes = ByteArray(length)
    nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
```

Add `totpService` dependency to SecurityService:
```kotlin
class SecurityService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val config: SecurityConfig,
    private val totpService: TOTPService,
    private val analytics: AnalyticsService,
)
```

- [ ] **Step 4: Update SecurityModule constructor call**

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl platform-security,platform-core -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 6: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/model/TotpModels.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/Models.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt
git commit -m "feat(totp): add TotpModels, partial-auth token system, TOTP verification in SecurityService"
```

---

### Task 4: TOTP auth routes (HTMX login flow)

**Files:**
- Create: `platform-web/.../web/TOTPRoutes.kt`
- Create: `platform-web/src/main/jte/.../web/TotpChallengeForm.kte`
- Modify: `platform-web/.../web/AuthRoutes.kt`
- Modify: `platform-web/.../auth/AuthPageFactory.kt`
- Modify: `platform-web/src/main/jte/.../web/AuthPage.kte`
- Modify: `platform-web/.../web/App.kt`

- [ ] **Step 1: Create TotpChallengeForm.kte**

```html
@import io.github.rygel.outerstellar.platform.web.TotpChallengeForm
@param model: TotpChallengeForm

<div id="auth-form-slot" class="card-body">
    <h2 class="card-title">Two-Factor Authentication</h2>
    <p class="text-sm opacity-70">Enter the 6-digit code from your authenticator app.</p>
    <form hx-post="/auth/components/totp-verify" hx-target="#auth-form-slot" hx-swap="outerHTML">
        <input type="hidden" name="partialToken" value="${model.partialToken}"/>
        <label class="form-control w-full">
            <span class="label-text">Authentication Code</span>
            <input type="text" name="code" class="input input-bordered w-full" placeholder="000000" maxlength="6" pattern="[0-9]{6}" inputmode="numeric" autocomplete="one-time-code" required/>
        </label>
        <button class="btn btn-primary mt-4 w-full" type="submit">Verify</button>
    </form>
    <p class="text-xs opacity-50 text-center mt-2">
        <a href="/auth" class="link">Back to login</a>
    </p>
</div>
```

- [ ] **Step 2: Create TOTPRoutes**

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.TotpVerifyRequest
import io.github.rygel.outerstellar.platform.security.AuthResult
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.web.auth.AuthPageFactory
import org.http4k.core.Body
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.webForm
import org.http4k.routing.bind
import org.http4k.routing.routes

data class TotpChallengeForm(val partialToken: String, val error: String? = null)

class TOTPRoutes(
    private val securityService: SecurityService,
    private val pageFactory: AuthPageFactory,
    private val renderer: Http4kRenderer,
) {
    private val webForm = Body.webForm().toLens()

    val routes = routes(
        "/auth/components/totp-verify" bind POST to { request ->
            val form = webForm(request)
            val partialToken = form("partialToken") ?: return@to Response(OK).body("Missing token")
            val code = form("code") ?: return@to Response(OK).body("Missing code")

            val result = securityService.verifyTotp(partialToken, code)
            when (result.status) {
                "success" -> Response(OK).body(
                    """<div id="auth-form-slot" hx-trigger="load" hx-get="/auth/components/result?mode=check" hx-target="#auth-form-slot" hx-swap="outerHTML"></div>"""
                )
                "invalid_code" -> Response(OK).body(
                    renderer.render(TotpChallengeForm(partialToken, "Invalid code. Try again."))
                )
                else -> Response(OK).body(
                    renderer.render(TotpChallengeForm(partialToken, "Session expired. Please log in again."))
                )
            }
        },
    )
}
```

- [ ] **Step 3: Register routes in App.kt**

Add `TOTPRoutes` instantiation and route binding in `buildBaseApp()` or `buildUiRoutes()`.

- [ ] **Step 4: AuthPage template**

Read current `AuthPage.kte`, add TOTP challenge mode that shows the code input form instead of the login form.

- [ ] **Step 5: Verify compilation**

Run: `mvn compile -pl platform-web -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 6: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPRoutes.kt platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/TotpChallengeForm.kte platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/App.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/AuthRoutes.kt platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/AuthPage.kte
git commit -m "feat(totp): add HTMX TOTP challenge form and routes"
```

---

### Task 5: TOTP API routes (JSON)

**Files:**
- Create: `platform-web/.../web/TOTPApiRoutes.kt`
- Modify: `platform-web/.../web/AuthApi.kt`

- [ ] **Step 1: Create TOTPApiRoutes**

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.LoginRequest
import io.github.rygel.outerstellar.platform.model.TotpConfirmRequest
import io.github.rygel.outerstellar.platform.model.TotpConfirmResponse
import io.github.rygel.outerstellar.platform.model.TotpDisableRequest
import io.github.rygel.outerstellar.platform.model.TotpSetupResponse
import io.github.rygel.outerstellar.platform.model.TotpVerifyRequest
import io.github.rygel.outerstellar.platform.model.TotpVerifyResponse
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.web.auth.AuthPageFactory
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.with
import org.http4k.format.KotlinxSerialization.auto
import org.http4k.routing.bind
import org.http4k.routing.routes

class TOTPApiRoutes(
    private val securityService: SecurityService,
    private val totpService: TOTPService,
) {
    private val totpVerifyRequest = Body.auto<TotpVerifyRequest>().toLens()
    private val totpVerifyResponse = Body.auto<TotpVerifyResponse>().toLens()
    private val totpSetupResponse = Body.auto<TotpSetupResponse>().toLens()
    private val totpConfirmRequest = Body.auto<TotpConfirmRequest>().toLens()
    private val totpConfirmResponse = Body.auto<TotpConfirmResponse>().toLens()

    val routes = routes(
        "/api/v1/auth/totp/verify" bind POST to { request ->
            val body = totpVerifyRequest(request)
            val result = securityService.verifyTotp(body.partialToken, body.code)
            when (result.status) {
                "success" -> Response(OK).with(totpVerifyResponse of result)
                "invalid_code" -> Response(UNAUTHORIZED).with(totpVerifyResponse of result)
                else -> Response(UNAUTHORIZED).with(totpVerifyResponse of result)
            }
        },

        "/api/v1/auth/totp/setup" bind POST to { request ->
            val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
            val secret = totpService.generateSecret()
            val qrDataUri = totpService.generateQrDataUri(secret, user.email)
            Response(OK).with(totpSetupResponse of TotpSetupResponse(secret, qrDataUri))
        },

        "/api/v1/auth/totp/confirm" bind POST to { request ->
            val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
            val body = totpConfirmRequest(request)
            if (!totpService.verifyCode(body.secret, body.code)) {
                return@to Response(OK).with(totpConfirmResponse of TotpConfirmResponse("invalid_code"))
            }
            val (rawCodes, hashedCodes) = totpService.generateBackupCodes()
            securityService.enableTotp(user.id, body.secret, hashedCodes)
            Response(CREATED).with(totpConfirmResponse of TotpConfirmResponse("success", rawCodes))
        },

        "/api/v1/auth/totp/disable" bind POST to { request ->
            val user = SecurityRules.USER_KEY(request) ?: return@to Response(UNAUTHORIZED)
            val body = Body.auto<TotpDisableRequest>().toLens()(request)
            val authResult = securityService.authenticate(user.username, body.password)
            if (authResult !is AuthResult.Authenticated) {
                return@to Response(UNAUTHORIZED)
            }
            securityService.disableTotp(user.id)
            Response(OK)
        },
    )
}
```

- [ ] **Step 2: Register routes in App.kt**

Add TOTPApiRoutes routes alongside other API routes.

- [ ] **Step 3: EnableTotp and DisableTotp in SecurityService**

```kotlin
fun enableTotp(userId: UUID, secret: String, backupCodes: String) {
    userRepository.updateTotpSecret(userId, secret, backupCodes)
    userRepository.enableTotp(userId)
}

fun disableTotp(userId: UUID) {
    userRepository.updateTotpSecret(userId, null, null)
    // totp_enabled stays false because secret is null — checked via user.totpEnabled
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl platform-web -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/TOTPApiRoutes.kt platform-security/src/main/kotlin/io/github/rygel/outerstellar/platform/security/SecurityService.kt
git commit -m "feat(totp): add JSON API routes for TOTP setup, verify, disable"
```

---

### Task 6: Settings security tab for TOTP setup

**Files:**
- Modify: `platform-web/.../web/ViewModels.kt`
- Modify: `platform-web/.../web/SettingsPageFactory.kt`
- Create: `platform-web/src/main/jte/.../web/TotpSetupFragment.kte`
- Modify: `platform-web/src/main/jte/.../web/SettingsPage.kte`

- [ ] **Step 1: Add TOTP fields to SettingsPage ViewModel**

In `ViewModels.kt`:
```kotlin
data class SettingsPage(
    val title: String,
    val tabs: List<SettingsTab>,
    val activeTab: String,
    val totpEnabled: Boolean = false,
    val totpQrDataUri: String? = null,
    val totpSecret: String? = null,
    val totpBackupCodes: List<String>? = null,
    val totpRemainingBackupCodes: Int = 0,
)
```

- [ ] **Step 2: Add security tab to SettingsPageFactory**

Add `"security"` to tab list between `"password"` and `"api-keys"`. In `buildSettingsPage()`, populate TOTP fields:

```kotlin
"security" -> {
    val user = ctx.user
    val totpSecret = user?.let { securityService.getTotpStatus(it.id) }
    SettingsPage(
        title = i18n.translate("web.settings.security.title"),
        tabs = tabs,
        activeTab = "security",
        totpEnabled = user?.totpEnabled ?: false,
        totpRemainingBackupCodes = countRemainingBackupCodes(user?.totpBackupCodes),
    )
}
```

- [ ] **Step 3: Create TotpSetupFragment.kte**

```html
@import io.github.rygel.outerstellar.platform.web.SettingsPage
@param model: SettingsPage

<div id="totp-setup">
    @if (model.totpEnabled) {
        <div class="alert alert-success mb-4">
            <span>Two-factor authentication is enabled.</span>
        </div>
        <form hx-post="/auth/components/totp-disable" hx-target="#totp-setup" hx-swap="outerHTML">
            <label class="form-control w-full">
                <span class="label-text">Enter your password to disable</span>
                <input type="password" name="password" class="input input-bordered w-full" required/>
            </label>
            <button class="btn btn-error mt-3" type="submit">Disable Two-Factor Auth</button>
        </form>
        @if (model.totpRemainingBackupCodes > 0) {
            <p class="text-sm mt-4">${model.totpRemainingBackupCodes} backup codes remaining</p>
        }
    } @else {
        <p class="mb-4">Scan the QR code below with your authenticator app, then enter the 6-digit code.</p>
        @if (model.totpQrDataUri != null) {
            <img src="${model.totpQrDataUri}" alt="TOTP QR Code" class="mb-4"/>
            <p class="text-xs mb-4">Or enter this key manually: <code>${model.totpSecret}</code></p>
            <form hx-post="/auth/components/totp-verify-setup" hx-target="#totp-setup" hx-swap="outerHTML">
                <input type="hidden" name="secret" value="${model.totpSecret}"/>
                <label class="form-control w-full">
                    <span class="label-text">Authentication Code</span>
                    <input type="text" name="code" class="input input-bordered w-full" placeholder="000000" maxlength="6" pattern="[0-9]{6}" required/>
                </label>
                <button class="btn btn-primary mt-3" type="submit">Verify and Enable</button>
            </form>
        } @else {
            <button class="btn btn-primary" hx-post="/auth/components/totp-setup" hx-target="#totp-setup" hx-swap="outerHTML">
                Enable Two-Factor Auth
            </button>
        }
    }
</div>
```

- [ ] **Step 4: Verify compilation**

Run: `mvn compile -pl platform-web -am "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/ViewModels.kt platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/SettingsPageFactory.kt platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/TotpSetupFragment.kte platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/SettingsPage.kte
git commit -m "feat(totp): add security tab to settings with TOTP setup/disable flow"
```

---

### Task 7: Testing

**Files:**
- Create: `platform-security/src/test/kotlin/.../security/TOTPServiceTest.kt`
- Create: `platform-security/src/test/kotlin/.../security/SecurityServiceTotpTest.kt`
- Create: `platform-web/src/test/kotlin/.../web/TOTPRoutesTest.kt`

- [ ] **Step 1: Write TOTPServiceTest**

```kotlin
package io.github.rygel.outerstellar.platform.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TOTPServiceTest {

    private lateinit var totpService: TOTPService

    @BeforeEach
    fun setUp() {
        totpService = TOTPService()
    }

    @Test
    fun `generateSecret returns a non-blank base32 string`() {
        val secret = totpService.generateSecret()
        assertTrue(secret.isNotBlank(), "Secret should not be blank")
        assertTrue(secret.matches(Regex("^[A-Z2-7]+=*\$")), "Secret should be valid base32")
    }

    @Test
    fun `generateQrDataUri returns a valid data URI`() {
        val secret = totpService.generateSecret()
        val uri = totpService.generateQrDataUri(secret, "test@example.com")
        assertTrue(uri.startsWith("data:image/png;base64,"), "Should return PNG data URI")
    }

    @Test
    fun `verifyCode returns true for valid code`() {
        val secret = totpService.generateSecret()
        // Generate a real TOTP code using the same algorithm
        val code = generateTotpCode(secret)
        assertTrue(totpService.verifyCode(secret, code), "Valid code should verify")
    }

    @Test
    fun `verifyCode returns false for invalid code`() {
        val secret = totpService.generateSecret()
        assertFalse(totpService.verifyCode(secret, "000000"), "Invalid code should not verify")
    }

    @Test
    fun `generateBackupCodes returns 16 codes`() {
        val (codes, _) = totpService.generateBackupCodes()
        assertEquals(16, codes.size, "Should generate 16 backup codes")
        codes.forEach { code ->
            assertTrue(code.length >= 8, "Each code should be at least 8 chars")
        }
    }

    @Test
    fun `verifyBackupCode returns null for invalid code`() {
        val (_, hashed) = totpService.generateBackupCodes()
        val result = totpService.verifyBackupCode("invalid-code", hashed)
        assertNull(result, "Invalid code should return null")
    }

    @Test
    fun `verifyBackupCode consumes valid code`() {
        val (rawCodes, hashed) = totpService.generateBackupCodes()
        val result = totpService.verifyBackupCode(rawCodes[0], hashed)
        assertNotNull(result, "Valid code should return updated JSON")
        // The same code should not work again
        val result2 = totpService.verifyBackupCode(rawCodes[0], result!!)
        assertNull(result2, "Consumed code should not verify again")
    }

    private fun generateTotpCode(secret: String): String {
        // Use the same TOTP library to generate a valid code for testing
        val codeGenerator = dev.samstevens.totp.code.DefaultCodeGenerator(
            dev.samstevens.totp.code.HashingAlgorithm.SHA1, 6
        )
        val timeProvider = dev.samstevens.totp.time.SystemTimeProvider()
        return codeGenerator.generate(secret, timeProvider.time)
    }
}
```

- [ ] **Step 2: Write SecurityServiceTotpTest**

```kotlin
package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuthTokenResponse
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.service.AnalyticsService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SecurityServiceTotpTest {

    private lateinit var userRepository: UserRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var totpService: TOTPService
    private lateinit var analytics: AnalyticsService
    private lateinit var securityService: SecurityService

    @BeforeEach
    fun setUp() {
        userRepository = mockk(relaxed = true)
        sessionRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        totpService = TOTPService()
        analytics = mockk(relaxed = true)
        securityService = SecurityService(
            userRepository = userRepository,
            sessionRepository = sessionRepository,
            passwordEncoder = passwordEncoder,
            config = SecurityConfig(),
            totpService = totpService,
            analytics = analytics,
        )
    }

    @Test
    fun `authenticate returns TotpRequired when totp is enabled`() {
        val user = User(id = UUID.randomUUID(), username = "alice", email = "alice@test.com",
            passwordHash = "hash", role = UserRole.USER, totpEnabled = true, totpSecret = "secret")
        every { userRepository.findByUsername("alice") } returns user
        every { passwordEncoder.matches("pass", "hash") } returns true

        val result = securityService.authenticate("alice", "pass")
        assertTrue(result is AuthResult.TotpRequired, "Should require TOTP")
        assertNotNull((result as AuthResult.TotpRequired).token, "Should have partial token")
    }

    @Test
    fun `authenticate returns Authenticated when totp is disabled`() {
        val user = User(id = UUID.randomUUID(), username = "alice", email = "alice@test.com",
            passwordHash = "hash", role = UserRole.USER)
        every { userRepository.findByUsername("alice") } returns user
        every { passwordEncoder.matches("pass", "hash") } returns true
        every { sessionRepository.save(any()) } returns Unit

        val result = securityService.authenticate("alice", "pass")
        assertTrue(result is AuthResult.Authenticated, "Should authenticate directly")
    }
}
```

- [ ] **Step 3: Run the tests**

Run: `mvn test -pl platform-security "-Dtest=TOTPServiceTest,SecurityServiceTotpTest" "-Dexec.skip=true" --no-transfer-progress`

- [ ] **Step 4: Commit**

```bash
git add platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/TOTPServiceTest.kt platform-security/src/test/kotlin/io/github/rygel/outerstellar/platform/security/SecurityServiceTotpTest.kt
git commit -m "test(totp): add TOTPServiceTest and SecurityServiceTotpTest"
```

---

## Spec Coverage Check

- [x] **DB migration** — Task 1 (V9 migration + model + repo)
- [x] **TOTPService** — Task 2
- [x] **Partial-auth token system** — Task 3
- [x] **Two-step login (HTMX)** — Task 4
- [x] **Two-step login (JSON API)** — Task 5
- [x] **Settings security tab** — Task 6
- [x] **Backup codes** — Task 2 (generate) + Task 3 (verify in verifyTotp)
- [x] **Error handling / rate limiting** — Task 3 (attemptCount in PartialAuth)
- [x] **Password reset preserves TOTP** — No change needed (passwords and TOTP are separate)
- [x] **OAuth interaction** — No change needed (AuthResult.Authenticated from OAuth doesn't trigger TotpRequired)
- [x] **Testing** — Task 7
