# Desktop Sync Engine Extraction

## Problem

`SyncViewModel` (695 lines) in `platform-desktop` is a god object holding all app state, all business logic, and all async coordination via `SwingWorker`. The `platform-desktop-javafx` module duplicates every API interaction across 14 FXML controllers with no shared layer. Both modules copy identical config loading (78 lines each) and state persistence (~68 lines each). The JavaFX module is also missing analytics, connectivity monitoring, auto-sync, deep links, and update checks.

## Goal

Extract all business logic from `SyncViewModel` into a framework-agnostic `DesktopSyncEngine` in `platform-sync-client`. Both desktop modules become thin UI adapters that bind UI elements to engine state and wrap engine calls in framework-specific async dispatch.

## Architecture

```
platform-core (models, interfaces)
    ↑
platform-persistence-jooq (DB)
platform-sync-client (SyncService + DesktopSyncEngine + DesktopAppConfig + ConnectivityChecker)
    ↑                        ↑
platform-desktop         platform-desktop-javafx
(Swing + FlatLaf)        (JavaFX + AtlantaFX)
```

No new modules. Everything goes into the existing `platform-sync-client`.

## New classes in platform-sync-client

### EngineState

Immutable snapshot of all observable state. UI always receives a consistent view.

```kotlin
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
    val emailNotificationsEnabled: Boolean = false,
    val pushNotificationsEnabled: Boolean = false,
    val searchQuery: String = "",
    val unreadNotificationCount: Int = 0
)
```

Computed fields (`unreadNotificationCount`) are derived in the engine, not by the UI.

### EngineListener

```kotlin
interface EngineListener {
    fun onStateChanged(newState: EngineState) {}
    fun onSessionExpired() {}
    fun onError(operation: String, message: String) {}
}
```

- `onStateChanged` fires after every mutation to engine state.
- `onSessionExpired` is separate because it requires UI action (show login dialog). Fires when any operation receives a 401.
- `onError` gives the UI adapter the choice of how to display errors (status bar, dialog, toast).

### DesktopSyncEngine

~350 lines. All methods are synchronous and blocking. The UI adapter decides how to dispatch.

```kotlin
class DesktopSyncEngine(
    private val syncService: SyncService,
    private val messageService: MessageService,
    private val contactService: ContactService?,
    private val analytics: AnalyticsService,
    private val connectivityChecker: ConnectivityChecker? = null,
    private val notifier: EngineNotifier? = null
) {
    private val _state = AtomicReference(EngineState())
    val state: EngineState get() = _state.get()
    private val listeners = CopyOnWriteArrayList<EngineListener>()

    fun addListener(listener: EngineListener)
    fun removeListener(listener: EngineListener)

    // Auth
    fun login(user: String, pass: String): Result<Unit>
    fun register(user: String, pass: String): Result<Unit>
    fun logout()

    // Messages & Contacts
    fun loadData()
    fun createMessage(author: String, content: String): Result<Unit>
    fun createContact(name: String, emails: List<String>, phones: List<String>, socials: List<String>): Result<Unit>
    fun updateContact(syncId: String, name: String, emails: List<String>, phones: List<String>, socials: List<String>): Result<Unit>

    // Sync
    fun sync(isAuto: Boolean = false): Result<SyncStats>
    fun startAutoSync(intervalMinutes: Long = 1)
    fun stopAutoSync()

    // Admin
    fun loadUsers()
    fun toggleUserEnabled(userId: String, current: Boolean)
    fun toggleUserRole(userId: String, currentRole: String)

    // Notifications
    fun loadNotifications()
    fun markNotificationRead(id: String)
    fun markAllNotificationsRead()

    // Profile
    fun loadProfile(): Result<UserProfileResponse>
    fun updateProfile(email: String, username: String?, avatarUrl: String?): Result<Unit>
    fun updateNotificationPreferences(email: String, push: Boolean): Result<Unit>
    fun deleteAccount(): Result<Unit>

    // Password
    fun changePassword(current: String, new: String): Result<Unit>
    fun requestPasswordReset(email: String): Result<Unit>
    fun resetPassword(token: String, newPassword: String): Result<Unit>

    // Conflict
    fun resolveConflict(syncId: String, strategy: ConflictStrategy)

    // Connectivity
    fun updateOnlineStatus(isOnline: Boolean)
}
```

Key design decisions:
- `AtomicReference<EngineState>` for lock-free thread-safe state reads.
- `CopyOnWriteArrayList<EngineListener>` for safe iteration without synchronization.
- Auto-sync uses `ScheduledExecutorService` (daemon thread), no framework dependency.
- All operations update `_state` and call `notifyListeners()` before returning.
- Session expiry is detected in any operation returning 401; engine calls `logout()` internally then fires `onSessionExpired`.
- Analytics events are tracked inside the engine (sync started, sync completed, login, etc.).

### DesktopAppConfig

Merges the identical `SwingAppConfig` (78 lines) and `FxAppConfig` (78 lines) into one shared class.

```kotlin
data class DesktopAppConfig(
    val serverBaseUrl: String,
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String,
    val version: String,
    val updateUrl: String,
    val devMode: Boolean,
    val devUsername: String,
    val devPassword: String,
    val segmentWriteKey: String,
    val analyticsEnabled: Boolean,
    val analyticsFlushIntervalHours: Long,
    val analyticsMaxFileSizeKb: Long,
    val analyticsMaxEventAgeDays: Long
) {
    companion object {
        fun fromEnvironment(): DesktopAppConfig
        // Private: loadYaml(), buildFromYaml(), str(), bool(), long()
    }
}
```

`SwingAppConfig` and `FxAppConfig` become typealiases:
```kotlin
typealias SwingAppConfig = DesktopAppConfig
typealias FxAppConfig = DesktopAppConfig
```

### ConnectivityChecker

Moves from `platform-desktop` to `platform-sync-client`. Interface + implementation:

```kotlin
interface ConnectivityChecker {
    val isOnline: Boolean
    fun startPolling()
    fun stopPolling()
    fun addListener(listener: (Boolean) -> Unit)
}

class HttpConnectivityChecker(
    private val healthUrl: String,
    private val intervalMs: Long = 30_000
) : ConnectivityChecker {
    // Current implementation, no Swing dependency
}
```

### EngineNotifier

Abstraction for desktop notifications (system tray):

```kotlin
interface EngineNotifier {
    fun notifySuccess(message: String)
    fun notifyFailure(message: String)
}
```

Swing provides `SystemTrayNotifier`. JavaFX provides `FxTrayNotifier`. The engine doesn't care which.

## Swing adapter changes (platform-desktop)

### SyncViewModel shrinks from 695 → ~150 lines

Every method follows the same pattern:
```kotlin
fun login(user: String, pass: String, onResult: (Boolean) -> Unit) {
    swingWorker {
        engine.login(user, pass)
    } onSuccess {
        onResult(true)
    } onError {
        onResult(false)
    }
}
```

Observable properties delegate to `engine.state`:
```kotlin
val isLoggedIn: Boolean get() = engine.state.isLoggedIn
val messages: List<MessageSummary> get() = engine.state.messages
// ... etc
```

The `addObserver`/`notifyObservers` mechanism is replaced by registering an `EngineListener` on the engine.

### SwingSyncApp changes minimally

- Replaces `SyncViewModel` construction with `DesktopSyncEngine` construction + `SyncViewModel` thin wrapper.
- `swingRuntimeModules()` wires the engine instead of individual services.
- SyncWindow binding code stays the same — it still reads from SyncViewModel properties.
- Removes inline SwingWorker patterns — all live in SyncViewModel's thin wrappers.

### Files removed from platform-desktop

- `ConnectivityChecker.kt` — moved to platform-sync-client
- Inline analytics tracking in SyncViewModel — moved to engine

### Files modified in platform-desktop

| File | Change |
|------|--------|
| `SyncViewModel.kt` | 695 → ~150 lines, delegates to engine |
| `SwingSyncApp.kt` | Wires engine, removes service locator |
| `DesktopModule.kt` | Provides `DesktopSyncEngine` instead of individual services |
| `SwingAppConfig.kt` | Becomes `typealias SwingAppConfig = DesktopAppConfig` |

## JavaFX adapter changes (platform-desktop-javafx)

### Controllers gain features for free

Each controller injects `DesktopSyncEngine` instead of `SyncService` directly:

```kotlin
class LoginController {
    private lateinit var engine: DesktopSyncEngine

    fun onLogin() {
        Thread {
            val result = engine.login(username, password)
            Platform.runLater {
                result.onSuccess { showMainView() }
                    .onFailure { showError(it.message) }
            }
        }.start()
    }
}
```

### New features in JavaFX module

- Connectivity monitoring (online/offline badge)
- Auto-sync scheduler
- Analytics event tracking
- Session expiry handling (auto-logout on 401)
- Deep link support (if desired)

### Files modified in platform-desktop-javafx

| File | Change |
|------|--------|
| `FxAppConfig.kt` | Becomes `typealias FxAppConfig = DesktopAppConfig` |
| `FxModule.kt` | Provides `DesktopSyncEngine` |
| All controllers | Inject `DesktopSyncEngine`, call engine methods instead of `SyncService` directly |

## Dependency changes

### platform-sync-client pom.xml additions

- `outerstellar-platform-core` (already present) — for `AnalyticsService`, `ContactService`
- No new external dependencies — everything is already on the classpath

### platform-desktop pom.xml

- Remove direct dependency on `ConnectivityChecker` (now comes via engine)

### platform-desktop-javafx pom.xml

- No new dependencies needed

## Testing strategy

### DesktopSyncEngine tests (new, in platform-sync-client)

- Unit tests with mocked `SyncService`, `MessageService`, `ContactService`, `AnalyticsService`
- Test every operation: state transitions, listener notifications, error handling, session expiry
- Test auto-sync start/stop
- Test connectivity status updates
- Target: ~40-50 tests

### SyncViewModel tests (existing, in platform-desktop)

- Existing tests should pass with minimal changes (SyncViewModel still exposes the same API)
- `SwingAppE2ETest` and other Swing tests continue to work unchanged

### JavaFX controller tests (existing, in platform-desktop-javafx)

- Update to inject `DesktopSyncEngine` instead of `SyncService`
- Gain coverage for features that were missing (connectivity, session expiry)

## Migration steps

1. Add `EngineState`, `EngineListener`, `EngineNotifier` to platform-sync-client
2. Add `DesktopAppConfig` to platform-sync-client (extracted from SwingAppConfig)
3. Add `ConnectivityChecker` interface + `HttpConnectivityChecker` to platform-sync-client
4. Add `DesktopSyncEngine` to platform-sync-client
5. Add engine unit tests to platform-sync-client
6. Update `SwingAppConfig` to typealias, update `SyncViewModel` to delegate to engine
7. Update `SwingSyncApp` to wire engine in Koin
8. Update JavaFX `FxAppConfig` to typealias, update `FxModule` to provide engine
9. Update all JavaFX controllers to use engine
10. Run full test suite (reactor verify + Podman desktop)

## Size estimates

| Component | Before | After |
|-----------|--------|-------|
| SyncViewModel (Swing) | 695 lines | ~150 lines |
| DesktopSyncEngine (shared) | 0 | ~350 lines |
| EngineState + Listener + Notifier | 0 | ~60 lines |
| DesktopAppConfig (shared) | 0 (was 78×2 duplicated) | 78 lines |
| ConnectivityChecker (shared) | 72 (Swing only) | ~80 lines |
| JavaFX controllers | ~700 lines (missing features) | ~700 lines (complete) |
| **Net change** | — | ~150 fewer lines, no duplication |

## Constraints

- Synchronous blocking I/O only — no coroutines, no reactive, no async/await
- Background work uses `Thread` / `ScheduledExecutorService` (daemon threads)
- All engine methods are blocking; UI adapters dispatch them to background threads
- Follows existing Koin DI patterns
- No comments in code
