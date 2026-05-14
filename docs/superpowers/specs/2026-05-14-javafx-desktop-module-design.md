# JavaFX Desktop Module Design

Date: 2026-05-14
Status: Approved

## Scope

Full feature parity with the existing Swing desktop client for the `platform-desktop-javafx` module. The module is scaffolded with 14 controllers and FXML views but has ~34 gaps vs the Swing client.

## Architecture

### Design Decision: ViewModel-First Approach

Build a JavaFX-idiomatic `FxSyncViewModel` first, then refactor all 14 controllers to consume it, then layer missing infrastructure and features incrementally.

### Module Structure

```
platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/
├── app/
│   ├── FxAppConfig.kt           (typealias, unchanged)
│   └── JavaFxApp.kt             (enhanced: splash, deep links, connectivity, tray, analytics)
├── controller/
│   └── All 14 controllers       (refactored to use FxSyncViewModel)
├── di/
│   └── FxModule.kt              (enhanced: SyncEngine, FxSyncViewModel, AnalyticsService, etc.)
├── service/
│   ├── FxIconLoader.kt          (enhanced: SVG loading from bundled RemixIcon SVGs)
│   ├── FxStateProvider.kt       (unchanged)
│   ├── FxThemeManager.kt        (unchanged)
│   └── FxTrayNotifier.kt        (enhanced: implements EngineNotifier)
├── viewmodel/
│   └── FxSyncViewModel.kt       (NEW: observable properties, Task-based threading)
└── update/
    └── UpdateService.kt         (NEW: version check against updateUrl)
```

### FxSyncViewModel

Location: `platform-desktop-javafx/.../fx/viewmodel/FxSyncViewModel.kt`

```kotlin
class FxSyncViewModel(private val engine: SyncEngine) {
    // Observable properties
    val userName = SimpleStringProperty("")
    val userEmail = SimpleStringProperty("")
    val userAvatarUrl = SimpleObjectProperty<String?>(null)
    val userRole = SimpleObjectProperty<String?>(null)
    val isLoggedIn = SimpleBooleanProperty(false)
    val isOnline = SimpleBooleanProperty(true)
    val isSyncing = SimpleBooleanProperty(false)
    val status = SimpleStringProperty("Ready")
    val searchQuery = SimpleStringProperty("")
    val author = SimpleStringProperty("")
    val content = SimpleStringProperty("")
    val emailNotificationsEnabled = SimpleBooleanProperty(false)
    val pushNotificationsEnabled = SimpleBooleanProperty(false)

    // Observable lists
    val messages = FXCollections.observableArrayList<MessageSummary>()
    val contacts = FXCollections.observableArrayList<ContactSummary>()
    val adminUsers = FXCollections.observableArrayList<UserSummary>()
    val notifications = FXCollections.observableArrayList<NotificationSummary>()

    val unreadNotificationCount = SimpleIntegerProperty(0)

    // Engine listener syncs EngineState → properties via Platform.runLater
    private val listener: EngineListener

    init {
        // Add listener to engine, initialize properties from engine.state
    }

    // All operations return Task for caller to observe
    fun login(username: String, password: String): Task<Result<Unit>>
    fun register(username: String, password: String): Task<Result<Unit>>
    fun logout(): Task<Void>
    fun sync(isAuto: Boolean = false): Task<Result<Unit>>
    fun changePassword(currentPassword: String, newPassword: String): Task<Result<Unit>>
    fun requestPasswordReset(email: String): Task<Result<Unit>>
    fun resetPassword(token: String, newPassword: String): Task<Result<Unit>>
    fun loadUsers(): Task<Void>
    fun setUserEnabled(userId: String, enabled: Boolean): Task<Result<Unit>>
    fun setUserRole(userId: String, role: String): Task<Result<Unit>>
    fun loadNotifications(): Task<Void>
    fun markNotificationRead(notificationId: String): Task<Void>
    fun markAllNotificationsRead(): Task<Void>
    fun loadProfile(): Task<Void>
    fun updateProfile(email: String, username: String?, avatarUrl: String?): Task<Result<Unit>>
    fun deleteAccount(): Task<Result<Unit>>
    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Task<Result<Unit>>
    fun createLocalMessage(author: String, content: String): Task<Result<Unit>>
    fun resolveConflict(syncId: String, strategy: ConflictStrategy): Task<Void>
    fun createContact(...): Task<Result<Unit>>
    fun loadData(): Task<Void>
    fun loadMessages(): Task<Void>
    fun loadContacts(): Task<Void>
    fun startAutoSync()
    fun stopAutoSync()
    fun startConnectivityChecker()
    fun stopConnectivityChecker()
    fun shutdown()

    // Helper: wraps engine call in Task
    private fun <T> task(operation: () -> T): Task<T>
}
```

Key pattern: `Task.runInBackground()` extension method starts the task on a daemon thread:

```kotlin
fun <T> Task<T>.runInBackground(): Task<T> {
    val thread = Thread(this).also { it.isDaemon = true }
    thread.start()
    return this
}
```

### Controller Refactoring

Each controller:
1. Injects `FxSyncViewModel` instead of `SyncEngine`
2. Binds FXML controls to ViewModel properties
3. Uses `viewModel.method().runInBackground()` for async operations
4. Uses `.setOnSucceeded { ... }` / `.setOnFailed { ... }` for post-operation UI updates

Example (MessagesController):

```kotlin
class MessagesController : KoinComponent {
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var messagesList: ListView<MessageSummary>
    @FXML private lateinit var searchField: TextField
    @FXML private lateinit var statusLabel: Label

    @FXML fun initialize() {
        messagesList.itemsProperty().bind(viewModel.messages)
        searchField.textProperty().bindBidirectional(viewModel.searchQuery)
        statusLabel.textProperty().bind(viewModel.status)
        viewModel.loadMessages().runInBackground()
    }
}
```

## UI Components

### Splash Screen

- `Stage(StageStyle.UNDECORATED)` shown during Koin init
- App logo, "Outerstellar" title, "Starting..." status label
- Disposed when main window finishes rendering

### Menu Bar

| Menu | Item | Accelerator |
|------|------|-------------|
| File | New Message | Ctrl+N |
| File | New Contact | Ctrl+O |
| File | Sync | F5 |
| File | Sync All | Shift+F5 |
| File | Exit | Alt+F4 |
| Edit | Preferences | Ctrl+, |
| Tools | Change Password | |
| Tools | Settings | |
| Help | Help | F1 |
| Help | Check for Updates | |
| Help | About | |

### Icons

- Package ~50 RemixIcon SVGs in `src/main/resources/icons/`
- Icons: system/search, messages/mail, contacts/user, notifications/bell, users/admin, profile/user-settings, settings/settings, login/logout, sync/refresh, create/add, conflict/alert, etc.
- `FxIconLoader` loads SVGs via `SvgPath` parsing
- Used: sidebar nav buttons, action buttons, dialog headers

### Status Bar

- `statusLabel.textProperty().bind(viewModel.status)`
- `offlineBadge.visibleProperty().bind(viewModel.isOnline.not())`

### Unread Badge

```kotlin
notificationsButton.textProperty().bind(
    Bindings.concat("Notifications (", viewModel.unreadNotificationCount.asString(), ")")
)
```

### Conflict Markers

Custom `ListCell<MessageSummary>` renders:
- Red `"[CONFLICT]"` prefix when `msg.hasConflict`
- Gray italic "(Local)" suffix when `syncId.isBlank()`

## Infrastructure

### Koin Module (enhanced FxModule)

```kotlin
val fxModule = module {
    single { FxAppConfig.fromEnvironment() }
    single { FxThemeManager() }
    single<I18nService> { I18nService.create("messages") }
    single { AppConfig(jdbcUrl = get<FxAppConfig>().jdbcUrl, ...) }
    single<MessageCache> { NoOpMessageCache }
    single<SyncService> { SyncService(baseUrl = ..., repository = get(), transactionManager = get()) }
    single<SyncProvider> { get<SyncService>() }
    single<AnalyticsService> {
        val cfg = get<FxAppConfig>()
        if (cfg.analyticsEnabled) PersistentBatchingAnalyticsService(...)
        else NoOpAnalyticsService()
    }
    single<SyncEngine> { DesktopSyncEngine(get(), get(), getOrNull(), get(), getOrNull(), getOrNull()) }
    single { FxSyncViewModel(get()) }
}
```

### Connectivity Checker

- Created in `JavaFxApp.start()` before main window
- `HttpConnectivityChecker(healthUrl = "${config.serverBaseUrl}/health").also { it.start() }`
- Wired into `DesktopSyncEngine` constructor
- Engine notifies ViewModel via `EngineListener.onStateChanged`

### System Tray Notifier

- `FxTrayNotifier` implements `EngineNotifier`
- Shows AWT SystemTray notifications on login, sync, errors, session expiry
- Injected into `DesktopSyncEngine` constructor

### Analytics

- Matches Swing: `PersistentBatchingAnalyticsService` if `config.analyticsEnabled`, else `NoOpAnalyticsService`
- Flush scheduler in `JavaFxApp.start()` with shutdown hook

### application.yaml

- Copy from Swing: `src/main/resources/application.yaml`
- Profile support: `application-dev.yaml`, `application-prod.yaml`

## Feature Additions

### Deep Link Handler

- `java.awt.Desktop.setOpenURIHandler { uri -> ... }` in `JavaFxApp.start()`
- Parses `outerstellar://search?q=...` → `viewModel.searchQuery.set(value)`
- Parses `outerstellar://sync` → `viewModel.sync().runInBackground()`

### Update Checker

- `UpdateService(currentVersion: String, updateUrl: String)` checks URL for version info
- Called from Settings/Help menu, shows "Update available" / "Up to date"

### Password Reset Flow

- `ResetPasswordDialog.fxml` + `ResetPasswordController`
- Email field → `viewModel.requestPasswordReset(email).runInBackground()`
- Token + new password → `viewModel.resetPassword(token, newPassword).runInBackground()`
- Or inline in LoginDialog with "Forgot password?" link

### Avatar URL Field

- Add `avatarUrlField` to `ProfileView.fxml`
- Pass through `viewModel.updateProfile(email, username, avatarUrl)`

### Feedback Dialog

- Simple text area, sends to `mailto:` or server endpoint

## Testing

| Test | Coverage | Notes |
|------|----------|-------|
| `FxSyncViewModelTest` | All engine ops, property updates, error handling | Uses mockk for engine |
| `FxModuleTest` | Koin wiring verification | Verifies all.bindings resolve |
| `FxAppE2ETest` | Login → messages → contacts → logout | TestFX + monocle headless |
| `FxThemeManagerTest` | Theme application | Existing, expand assertions |
| `FxStateProviderTest` | State save/load | Existing, expand assertions |

Test execution: `mvn -pl platform-desktop-javafx -Ptest-headless test` via TestFX monocle.

Desktop tests require Podman for full GUI testing (same as Swing).

## Migration Path

### Phase 1: FxSyncViewModel + Controller Refactoring
- Create FxSyncViewModel
- Refactor all 14 controllers
- Update FxModule

### Phase 2: Infrastructure
- Connectivity checker, tray notifier, analytics
- application.yaml
- Splash screen

### Phase 3: UI Polish
- Icons (bundled SVGs)
- Menu bar + keyboard shortcuts
- Status bar, unread badge, conflict markers

### Phase 4: Features
- Password reset flow
- Deep link handler
- Update checker
- Avatar field
- Feedback dialog

### Phase 5: Testing
- FxSyncViewModel test
- Koin module test
- E2E test expansion
