# Desktop Sync Engine Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract all business logic from SyncViewModel into a framework-agnostic DesktopSyncEngine in platform-sync-client, making both desktop modules thin UI adapters.

**Architecture:** DesktopSyncEngine holds all state via AtomicReference<EngineState>, exposes synchronous blocking methods, and notifies EngineListeners on state changes. Swing and JavaFX adapters wrap engine calls in SwingWorker/Thread respectively.

**Tech Stack:** Kotlin/JVM, Koin DI, JUnit 5, MockK, platform-sync-client module

---

## File Map

### New files (platform-sync-client)

| File | Responsibility |
|------|---------------|
| `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/EngineState.kt` | Immutable state snapshot |
| `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/EngineListener.kt` | Listener interface + EngineNotifier interface |
| `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngine.kt` | Core engine — all state + operations |
| `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopAppConfig.kt` | Shared config (replaces SwingAppConfig + FxAppConfig) |
| `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/ConnectivityChecker.kt` | Interface + HttpConnectivityChecker (moved from platform-desktop) |
| `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngineTest.kt` | Engine unit tests |

### Modified files (platform-desktop)

| File | Change |
|------|--------|
| `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingAppConfig.kt` | Replace with typealias |
| `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/viewmodel/SyncViewModel.kt` | Shrink to thin adapter (~150 lines) |
| `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt` | Wire DesktopSyncEngine |
| `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingSyncApp.kt` | Update engine wiring |
| `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/ConnectivityChecker.kt` | Delete (moved to sync-client) |

### Modified files (platform-desktop-javafx)

| File | Change |
|------|--------|
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/app/FxAppConfig.kt` | Replace with typealias |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/di/FxModule.kt` | Wire DesktopSyncEngine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/LoginController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/RegisterController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/MessagesController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/UsersController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/NotificationsController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ProfileController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ChangePasswordController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ConflictController.kt` | Use engine |
| `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ContactsController.kt` | Use engine |

---

### Task 1: Create EngineState, EngineListener, EngineNotifier

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/EngineState.kt`
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/EngineListener.kt`

- [ ] **Step 1: Create EngineState data class**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.UserSummary

data class EngineState(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userRole: String? = null,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val status: String = "",
    val messages: List<MessageSummary> = emptyList(),
    val contacts: List<ContactSummary> = emptyList(),
    val adminUsers: List<UserSummary> = emptyList(),
    val notifications: List<NotificationSummary> = emptyList(),
    val userEmail: String = "",
    val userAvatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val searchQuery: String = "",
) {
    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }
}
```

- [ ] **Step 2: Create EngineListener and EngineNotifier interfaces**

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine

interface EngineListener {
    fun onStateChanged(newState: EngineState) {}
    fun onSessionExpired() {}
    fun onError(operation: String, message: String) {}
}

interface EngineNotifier {
    fun notifySuccess(message: String)
    fun notifyFailure(message: String)
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl platform-sync-client compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/
git commit -m "feat(sync-client): add EngineState, EngineListener, EngineNotifier"
```

---

### Task 2: Create DesktopAppConfig (shared config)

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopAppConfig.kt`

- [ ] **Step 1: Create DesktopAppConfig**

This is extracted verbatim from `SwingAppConfig.kt` (78 lines) and `FxAppConfig.kt` (78 lines) which are identical except for the class name.

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

data class DesktopAppConfig(
    val serverBaseUrl: String = "http://localhost:8080",
    val jdbcUrl: String = "jdbc:postgresql://localhost:5432/outerstellar",
    val jdbcUser: String = "outerstellar",
    val jdbcPassword: String = "outerstellar",
    val version: String = "1.0.0",
    val updateUrl: String = "",
    val devMode: Boolean = false,
    val devUsername: String = "",
    val devPassword: String = "",
    val segmentWriteKey: String = "",
    val analyticsEnabled: Boolean = false,
    val analyticsFlushIntervalHours: Long = 24,
    val analyticsMaxFileSizeKb: Long = 2048,
    val analyticsMaxEventAgeDays: Long = 30,
) {
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): DesktopAppConfig {
            val profile = environment["APP_PROFILE"] ?: "default"
            val yamlData = loadYaml(profile)
            return buildFromYaml(yamlData, environment)
        }

        private fun loadYaml(profile: String): Map<String, Any>? {
            val loader = Load(LoadSettings.builder().build())
            if (profile != "default") {
                val result = readResource(loader, "/application-$profile.yaml")
                if (result != null) return result
            }
            return readResource(loader, "/application.yaml")
        }

        private fun readResource(loader: Load, path: String): Map<String, Any>? {
            val stream = DesktopAppConfig::class.java.getResourceAsStream(path) ?: return null
            return try {
                loader.loadFromInputStream(stream) as? Map<String, Any>
            } finally {
                stream.close()
            }
        }

        private fun buildFromYaml(yaml: Map<String, Any>?, env: Map<String, String>): DesktopAppConfig {
            if (yaml == null) return DesktopAppConfig()
            return DesktopAppConfig(
                serverBaseUrl = yaml.str("serverBaseUrl", env, "SERVER_BASE_URL", "http://localhost:8080"),
                jdbcUrl = yaml.str("jdbcUrl", env, "JDBC_URL", "jdbc:postgresql://localhost:5432/outerstellar"),
                jdbcUser = yaml.str("jdbcUser", env, "JDBC_USER", "outerstellar"),
                jdbcPassword = yaml.str("jdbcPassword", env, "JDBC_PASSWORD", "outerstellar"),
                version = yaml.str("version", env, "VERSION", "1.0.0"),
                updateUrl = yaml.str("updateUrl", env, "UPDATE_URL", ""),
                devMode = yaml.bool("devMode", env, "DEV_MODE", false),
                devUsername = yaml.str("devUsername", env, "DEV_USERNAME", ""),
                devPassword = yaml.str("devPassword", env, "DEV_PASSWORD", ""),
                segmentWriteKey = yaml.str("segmentWriteKey", env, "SEGMENT_WRITEKEY", ""),
                analyticsEnabled = yaml.bool("analyticsEnabled", env, "ANALYTICS_ENABLED", false),
                analyticsFlushIntervalHours =
                    yaml.long("analyticsFlushIntervalHours", env, "ANALYTICS_FLUSH_INTERVAL_HOURS", 24L),
                analyticsMaxFileSizeKb = yaml.long("analyticsMaxFileSizeKb", env, "ANALYTICS_MAX_FILE_SIZE_KB", 2048L),
                analyticsMaxEventAgeDays =
                    yaml.long("analyticsMaxEventAgeDays", env, "ANALYTICS_MAX_EVENT_AGE_DAYS", 30L),
            )
        }
    }
}

private fun Map<String, Any>.str(key: String, env: Map<String, String>, envKey: String, default: String): String =
    env[envKey] ?: (this[key] as? String) ?: default

private fun Map<String, Any>.bool(key: String, env: Map<String, String>, envKey: String, default: Boolean): Boolean =
    env[envKey]?.toBoolean() ?: (this[key] as? Boolean) ?: default

private fun Map<String, Any>.long(key: String, env: Map<String, String>, envKey: String, default: Long): Long =
    env[envKey]?.toLong() ?: (this[key] as? Long) ?: default
```

Note: `platform-sync-client` pom.xml already depends on `snakeyaml-engine` via `platform-core` or directly — verify before compiling. If missing, add `<dependency>org.snakeyaml:snakeyaml-engine</dependency>` to platform-sync-client pom.xml.

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-sync-client compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopAppConfig.kt
git commit -m "feat(sync-client): add shared DesktopAppConfig"
```

---

### Task 3: Move ConnectivityChecker to platform-sync-client

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/ConnectivityChecker.kt`

- [ ] **Step 1: Create ConnectivityChecker interface + HttpConnectivityChecker**

Extracted from `platform-desktop/.../ConnectivityChecker.kt` (72 lines). The implementation is identical but uses the new package.

```kotlin
package io.github.rygel.outerstellar.platform.sync.engine

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

interface ConnectivityChecker {
    val isOnline: Boolean
    fun addObserver(fn: (Boolean) -> Unit)
    fun start()
    fun stop()
}

class HttpConnectivityChecker(
    private val healthUrl: String,
    private val intervalSeconds: Long = 30L,
    private val timeoutSeconds: Long = 5L,
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : ConnectivityChecker {
    private val logger = LoggerFactory.getLogger(HttpConnectivityChecker::class.java)

    private val _isOnline = AtomicBoolean(true)
    override val isOnline: Boolean
        get() = _isOnline.get()

    private val observers = mutableListOf<(Boolean) -> Unit>()
    private var scheduler: ScheduledExecutorService? = null

    override fun addObserver(fn: (Boolean) -> Unit) {
        observers.add(fn)
    }

    override fun start() {
        if (scheduler != null) return
        scheduler =
            Executors.newSingleThreadScheduledExecutor { r ->
                    Thread(r, "connectivity-checker").also { it.isDaemon = true }
                }
                .also { s -> s.scheduleAtFixedRate(::check, 0L, intervalSeconds, TimeUnit.SECONDS) }
    }

    override fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    fun check() {
        val online =
            try {
                val request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(healthUrl))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .GET()
                        .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
                response.statusCode() in 200..299
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.debug("Connectivity check failed for {}", healthUrl, e)
                false
            }

        val previous = _isOnline.getAndSet(online)
        if (previous != online) {
            observers.forEach { it(online) }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-sync-client compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/ConnectivityChecker.kt
git commit -m "feat(sync-client): add ConnectivityChecker interface and HttpConnectivityChecker"
```

---

### Task 4: Create DesktopSyncEngine

**Files:**
- Create: `platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngine.kt`

- [ ] **Step 1: Write the engine class**

This is the core extraction from `SyncViewModel.kt` (695 lines), removing all SwingWorker usage, Swing imports, and i18n dependency. The engine returns `Result<T>` from operations that can fail. All methods are synchronous and blocking.

```kotlin
@file:Suppress("TooManyFunctions")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactNotFoundException
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class DesktopSyncEngine(
    private val syncService: SyncService,
    private val messageService: MessageService,
    private val contactService: ContactService? = null,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    private val connectivityChecker: ConnectivityChecker? = null,
    private val notifier: EngineNotifier? = null,
) {
    private val logger = LoggerFactory.getLogger(DesktopSyncEngine::class.java)

    private val _state = AtomicReference(EngineState())
    val state: EngineState get() = _state.get()

    private val listeners = CopyOnWriteArrayList<EngineListener>()
    private var autoSyncExecutor: ScheduledExecutorService? = null

    init {
        connectivityChecker?.addObserver { online ->
            updateState { it.copy(isOnline = online) }
        }
    }

    fun addListener(listener: EngineListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: EngineListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (EngineState) -> EngineState) {
        val newState = transform(_state.get())
        _state.set(newState)
        listeners.forEach { it.onStateChanged(newState) }
    }

    fun login(user: String, pass: String): Result<Unit> {
        return try {
            val result = syncService.login(user, pass)
            updateState {
                it.copy(
                    isLoggedIn = true,
                    userName = result.username,
                    userRole = result.role,
                    status = "Logged in as ${result.username}",
                )
            }
            analytics.identify(result.username, mapOf("role" to (result.role ?: "user"), "platform" to "desktop"))
            analytics.track(result.username, "User Logged In", mapOf("platform" to "desktop"))
            startAutoSync()
            Result.success(Unit)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e.cause ?: e)
        }
    }

    fun register(user: String, pass: String): Result<Unit> {
        return try {
            val result = syncService.register(user, pass)
            updateState {
                it.copy(
                    isLoggedIn = true,
                    userName = result.username,
                    userRole = result.role,
                    status = "Registered as ${result.username}",
                )
            }
            startAutoSync()
            Result.success(Unit)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e.cause ?: e)
        }
    }

    fun logout() {
        stopAutoSync()
        syncService.logout()
        updateState {
            it.copy(
                isLoggedIn = false,
                userRole = null,
                userName = "",
                userEmail = "",
                userAvatarUrl = null,
                adminUsers = emptyList(),
                status = "Logged out",
            )
        }
    }

    fun loadData() {
        val s = _state.get()
        val messages = messageService.listMessages(s.searchQuery.takeIf { it.isNotBlank() }).items
        val contacts = contactService?.listContacts(s.searchQuery.takeIf { it.isNotBlank() }) ?: emptyList()
        updateState { it.copy(messages = messages, contacts = contacts) }
    }

    fun createMessage(author: String, content: String): Result<Unit> {
        return try {
            messageService.createLocalMessage(author, content)
            loadData()
            updateState { it.copy(status = "Message created", content = "") }
            Result.success(Unit)
        } catch (e: ValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
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
    ): Result<Unit> {
        return try {
            contactService?.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
            loadData()
            updateState { it.copy(status = "Contact created") }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
    ): Result<Unit> {
        return try {
            val stored = contactService?.getContactBySyncId(syncId) ?: throw ContactNotFoundException(syncId)
            val updated =
                stored.copy(
                    name = name,
                    emails = emails,
                    phones = phones,
                    socialMedia = socialMedia,
                    company = company,
                    companyAddress = companyAddress,
                    department = department,
                    dirty = true,
                )
            contactService.updateContact(updated)
            loadData()
            updateState { it.copy(status = "Contact updated") }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sync(isAuto: Boolean = false): Result<Unit> {
        val s = _state.get()
        if (s.isSyncing) return Result.success(Unit)
        if (!s.isOnline) {
            updateState { it.copy(status = "Offline — cannot sync") }
            return Result.failure(IllegalStateException("Offline"))
        }

        updateState { it.copy(isSyncing = true, status = if (isAuto) "Auto-syncing..." else "Syncing...") }

        return try {
            val stats = syncService.sync()
            val statusMsg = "Sync complete: ${stats.pushedCount} pushed, ${stats.pulledCount} pulled, ${stats.conflictCount} conflicts"
            analytics.track(
                s.userName,
                "Sync Completed",
                mapOf(
                    "pushed" to stats.pushedCount,
                    "pulled" to stats.pulledCount,
                    "conflicts" to stats.conflictCount,
                    "platform" to "desktop",
                ),
            )
            if (!isAuto) {
                notifier?.notifySuccess(statusMsg)
            }
            loadData()
            updateState { it.copy(isSyncing = false, status = statusMsg) }
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = "Sync failed: ${e.cause?.message ?: e.message ?: "unknown error"}"
            if (!isAuto) {
                notifier?.notifyFailure(errorMsg)
            }
            updateState { it.copy(isSyncing = false, status = errorMsg) }
            Result.failure(e)
        }
    }

    fun startAutoSync(intervalMinutes: Long = 1) {
        if (autoSyncExecutor != null) return
        autoSyncExecutor =
            Executors.newSingleThreadScheduledExecutor { r ->
                    Thread(r, "auto-sync").also { it.isDaemon = true }
                }
                .also { executor ->
                    executor.scheduleAtFixedRate(
                        {
                            val s = _state.get()
                            if (!s.isSyncing && s.isLoggedIn) {
                                sync(isAuto = true)
                            }
                        },
                        intervalMinutes,
                        intervalMinutes,
                        TimeUnit.MINUTES,
                    )
                }
    }

    fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            syncService.changePassword(currentPassword, newPassword)
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadUsers() {
        try {
            val users = syncService.listUsers()
            updateState { it.copy(adminUsers = users) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            updateState { it.copy(status = e.message ?: "Failed to load users") }
        }
    }

    fun toggleUserEnabled(userId: String, currentEnabled: Boolean) {
        try {
            syncService.setUserEnabled(userId, !currentEnabled)
            val users = syncService.listUsers()
            updateState { it.copy(adminUsers = users) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            updateState { it.copy(status = e.message ?: "Failed to toggle user") }
        }
    }

    fun toggleUserRole(userId: String, currentRole: String) {
        try {
            val newRole = if (currentRole == "ADMIN") "USER" else "ADMIN"
            syncService.setUserRole(userId, newRole)
            val users = syncService.listUsers()
            updateState { it.copy(adminUsers = users) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            updateState { it.copy(status = e.message ?: "Failed to toggle role") }
        }
    }

    fun loadNotifications() {
        try {
            val notifs = syncService.listNotifications()
            updateState { it.copy(notifications = notifs) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            logger.debug("Failed to load notifications", e)
        }
    }

    fun markNotificationRead(notificationId: String) {
        try {
            syncService.markNotificationRead(notificationId)
            val notifs = syncService.listNotifications()
            updateState { it.copy(notifications = notifs) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            updateState { it.copy(status = e.message ?: "Failed to mark notification as read") }
        }
    }

    fun markAllNotificationsRead() {
        try {
            syncService.markAllNotificationsRead()
            val notifs = syncService.listNotifications()
            updateState { it.copy(notifications = notifs) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
        } catch (e: Exception) {
            updateState { it.copy(status = e.message ?: "Failed to mark all notifications as read") }
        }
    }

    fun loadProfile(): Result<Unit> {
        return try {
            val profile = syncService.fetchProfile()
            updateState {
                it.copy(
                    userEmail = profile.email,
                    userAvatarUrl = profile.avatarUrl,
                    emailNotificationsEnabled = profile.emailNotificationsEnabled,
                    pushNotificationsEnabled = profile.pushNotificationsEnabled,
                )
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateProfile(email: String, username: String?, avatarUrl: String?): Result<Unit> {
        return try {
            syncService.updateProfile(email, username, avatarUrl)
            val profile = syncService.fetchProfile()
            updateState {
                it.copy(
                    userName = profile.username,
                    userEmail = profile.email,
                    userAvatarUrl = profile.avatarUrl,
                )
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit> {
        return try {
            syncService.updateNotificationPreferences(emailEnabled, pushEnabled)
            updateState {
                it.copy(
                    emailNotificationsEnabled = emailEnabled,
                    pushNotificationsEnabled = pushEnabled,
                )
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteAccount(): Result<Unit> {
        return try {
            syncService.deleteAccount()
            stopAutoSync()
            updateState {
                it.copy(
                    isLoggedIn = false,
                    userRole = null,
                    userName = "",
                    userEmail = "",
                )
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            syncService.requestPasswordReset(email)
            Result.success(Unit)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            syncService.resetPassword(token, newPassword)
            Result.success(Unit)
        } catch (e: SyncException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        try {
            messageService.resolveConflict(syncId, strategy)
            loadData()
            updateState { it.copy(status = "Conflict resolved: ${strategy.name}") }
        } catch (e: Exception) {
            updateState { it.copy(status = "Conflict resolution failed: ${e.message ?: "unknown"}") }
        }
    }

    fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadData()
    }

    private fun handleSessionExpired() {
        stopAutoSync()
        updateState {
            it.copy(
                isLoggedIn = false,
                userRole = null,
                userName = "",
                userEmail = "",
                userAvatarUrl = null,
                status = "Session expired",
            )
        }
        listeners.forEach { it.onSessionExpired() }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-sync-client compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-sync-client/src/main/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngine.kt
git commit -m "feat(sync-client): add DesktopSyncEngine"
```

---

### Task 5: Write DesktopSyncEngine tests

**Files:**
- Create: `platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngineTest.kt`

- [ ] **Step 1: Write tests for core operations**

Test each engine operation: login, register, logout, sync, admin ops, notifications, profile, session expiry, state transitions, listener notifications. Use MockK for SyncService, MessageService, ContactService, AnalyticsService.

Key tests:
- `login succeeds and updates state` — mocks `syncService.login()` returning `AuthTokenResponse`, asserts `state.isLoggedIn == true`, `state.userName` matches, `onStateChanged` was called
- `login failure returns Result.failure` — mocks `syncService.login()` throwing `SyncException`, asserts `Result.isFailure`
- `register succeeds and updates state` — same pattern as login
- `logout clears auth state and stops auto-sync` — asserts `isLoggedIn == false`, `userName == ""`
- `loadData loads messages and contacts` — mocks both services, asserts state
- `sync succeeds and tracks analytics` — mocks `syncService.sync()`, verifies `analytics.track()` called
- `sync when offline returns failure` — sets `isOnline = false`, asserts failure
- `sync when already syncing returns success without calling service` — sets `isSyncing = true`, verifies no service call
- `changePassword success` and `changePassword session expired`
- `loadUsers success` and `loadUsers session expired`
- `toggleUserEnabled` and `toggleUserRole`
- `loadNotifications`, `markNotificationRead`, `markAllNotificationsRead`
- `loadProfile`, `updateProfile`, `updateNotificationPreferences`, `deleteAccount`
- `requestPasswordReset`, `resetPassword`
- `resolveConflict`
- `handleSessionExpired fires onSessionExpired on all listeners`
- `autoSync starts and stops`
- `connectivityChecker observer updates isOnline`
- `setSearchQuery triggers loadData`

Run: `mvn -pl platform-sync-client test -Dtest=DesktopSyncEngineTest -q`
Expected: All tests pass

- [ ] **Step 2: Commit**

```bash
git add platform-sync-client/src/test/kotlin/io/github/rygel/outerstellar/platform/sync/engine/DesktopSyncEngineTest.kt
git commit -m "test(sync-client): add DesktopSyncEngine unit tests"
```

---

### Task 6: Update Swing adapter — replace SwingAppConfig with typealias

**Files:**
- Modify: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingAppConfig.kt`
- Modify: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt`

- [ ] **Step 1: Replace SwingAppConfig.kt with typealias**

Replace the entire 78-line file with:

```kotlin
package io.github.rygel.outerstellar.platform.swing

import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig

typealias SwingAppConfig = DesktopAppConfig
```

- [ ] **Step 2: Update DesktopModule.kt to reference DesktopAppConfig**

Update the `desktopModule` in `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt`:
- Change `single { SwingAppConfig.fromEnvironment() }` to `single { DesktopAppConfig.fromEnvironment() }`
- Add import for `io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig`
- Add import for `io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine`
- Add `single { DesktopSyncEngine(get(), get(), getOrNull(), get(), getOrNull(), getOrNull()) }` before the SyncViewModel line
- Update SyncViewModel construction to pass the engine: `single { SyncViewModel(get(), get()) }`

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl platform-desktop compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingAppConfig.kt platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/di/DesktopModule.kt
git commit -m "refactor(desktop): replace SwingAppConfig with shared DesktopAppConfig typealias"
```

---

### Task 7: Update SyncViewModel to delegate to DesktopSyncEngine

**Files:**
- Modify: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/viewmodel/SyncViewModel.kt`

This is the biggest change in the Swing adapter. SyncViewModel goes from 695 lines to ~150 lines. Every method becomes a thin SwingWorker wrapper around the engine.

- [ ] **Step 1: Rewrite SyncViewModel as thin adapter**

The new SyncViewModel:
- Constructor takes `DesktopSyncEngine` and `I18nService`
- Exposes the same observable properties (for backward compatibility with SyncWindow binding), delegating to `engine.state`
- Every async method wraps `engine.xxx()` in a `SwingWorker`
- Registers as an `EngineListener` to propagate state changes to Swing observers
- Keeps `author`, `content`, `searchQuery` as local mutable fields (they are UI-only state, not in EngineState)

The key mapping:
- `SyncViewModel.isLoggedIn` → `engine.state.isLoggedIn`
- `SyncViewModel.messages` → `engine.state.messages`
- `SyncViewModel.login(user, pass, onResult)` → `SwingWorker { engine.login(user, pass) }`
- `SyncViewModel.addObserver(observer)` → register an `EngineListener` that calls observers
- `SyncViewModel.searchQuery = value` → `engine.setSearchQuery(value)`

Keep the same public API surface so existing tests and SyncWindow binding code continues to work unchanged.

- [ ] **Step 2: Run existing desktop tests**

Run: `mvn -pl platform-desktop test -q`
Expected: All existing tests pass (may need minor adjustments to test setup)

- [ ] **Step 3: Commit**

```bash
git add platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/viewmodel/SyncViewModel.kt
git commit -m "refactor(desktop): SyncViewModel delegates to DesktopSyncEngine"
```

---

### Task 8: Update SwingSyncApp wiring

**Files:**
- Modify: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingSyncApp.kt`
- Delete: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/ConnectivityChecker.kt`

- [ ] **Step 1: Update imports in SwingSyncApp**

Replace `import io.github.rygel.outerstellar.platform.swing.ConnectivityChecker` with `import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker` and `import io.github.rygel.outerstellar.platform.sync.engine.HttpConnectivityChecker`.

Update the `ConnectivityChecker` constructor call to `HttpConnectivityChecker`:
```kotlin
val checker = HttpConnectivityChecker("${cfg.serverBaseUrl}/health")
```

- [ ] **Step 2: Delete old ConnectivityChecker**

Delete `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/ConnectivityChecker.kt` — it has been replaced by the one in platform-sync-client.

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl platform-desktop compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A platform-desktop/src/main/kotlin/
git commit -m "refactor(desktop): use shared ConnectivityChecker from sync-client"
```

---

### Task 9: Update JavaFX adapter — FxAppConfig and FxModule

**Files:**
- Modify: `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/app/FxAppConfig.kt`
- Modify: `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/di/FxModule.kt`

- [ ] **Step 1: Replace FxAppConfig.kt with typealias**

Replace the entire 78-line file with:

```kotlin
package io.github.rygel.outerstellar.platform.fx.app

import io.github.rygel.outerstellar.platform.sync.engine.DesktopAppConfig

typealias FxAppConfig = DesktopAppConfig
```

- [ ] **Step 2: Update FxModule.kt to provide DesktopSyncEngine**

Add `DesktopSyncEngine` as a singleton. Update `SyncService` and `SyncProvider` to continue working. Add imports for engine classes.

```kotlin
single<DesktopSyncEngine> {
    DesktopSyncEngine(
        syncService = get(),
        messageService = get(),
        contactService = getOrNull(),
        analytics = NoOpAnalyticsService(),
    )
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -pl platform-desktop-javafx compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/
git commit -m "refactor(javafx): replace FxAppConfig with shared DesktopAppConfig, wire engine"
```

---

### Task 10: Update JavaFX controllers to use DesktopSyncEngine

**Files:**
- Modify: All controller files in `platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/`

For each controller that currently injects `SyncService` directly:
1. Replace `SyncService` with `DesktopSyncEngine` injection
2. Replace direct `syncService.login()` calls with `engine.login()` wrapped in `Thread` + `Platform.runLater`
3. Use `engine.state.xxx` instead of local state variables

Pattern for each controller:

```kotlin
class XxxController {
    private lateinit var engine: DesktopSyncEngine

    fun onXxx() {
        Thread {
            val result = engine.someOperation(args)
            Platform.runLater {
                result.onSuccess { updateUi() }
                    .onFailure { showError(it.message) }
            }
        }.start()
    }
}
```

Controllers to update (in order):
1. `LoginController.kt` — `engine.login()`
2. `RegisterController.kt` — `engine.register()`
3. `MessagesController.kt` — `engine.loadData()`, `engine.sync()`, `engine.createMessage()`, `engine.resolveConflict()`
4. `ContactsController.kt` — `engine.createContact()`, `engine.updateContact()`
5. `UsersController.kt` — `engine.loadUsers()`, `engine.toggleUserEnabled()`, `engine.toggleUserRole()`
6. `NotificationsController.kt` — `engine.loadNotifications()`, `engine.markNotificationRead()`, `engine.markAllNotificationsRead()`
7. `ProfileController.kt` — `engine.loadProfile()`, `engine.updateProfile()`, `engine.updateNotificationPreferences()`, `engine.deleteAccount()`
8. `ChangePasswordController.kt` — `engine.changePassword()`
9. `ConflictController.kt` — `engine.resolveConflict()`

- [ ] **Step 1: Update each controller**

For each controller, read the file, identify where `SyncService` is injected/used, and replace with `DesktopSyncEngine`. The engine methods return `Result<T>`, so wrap in `result.onSuccess { } .onFailure { }`.

- [ ] **Step 2: Verify compilation**

Run: `mvn -pl platform-desktop-javafx compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run JavaFX tests**

Run: `mvn -pl platform-desktop-javafx test -q`
Expected: All tests pass (may need test updates for engine injection)

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/
git commit -m "refactor(javafx): controllers use DesktopSyncEngine"
```

---

### Task 11: Full verification

- [ ] **Step 1: Run full reactor verify (excluding desktop)**

Run: `mvn clean verify -T 4 -pl '!platform-desktop,!platform-desktop-javafx'`
Expected: BUILD SUCCESS, all 546+ tests pass

- [ ] **Step 2: Run desktop tests via Podman**

Rebuild image and run:
```powershell
podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .
podman run --rm --network host -v "${HOME}/.m2/settings.xml:/root/.m2/settings.xml:Z" -v "${HOME}/.m2/repository:/root/.m2/repository:Z" -v "/var/run/docker.sock:/var/run/docker.sock:Z" outerstellar-test-desktop
```
Expected: BUILD SUCCESS, all 126+ desktop tests pass

- [ ] **Step 3: Commit any remaining fixes**

```bash
git add -A
git commit -m "fix: resolve test failures from engine extraction"
```
