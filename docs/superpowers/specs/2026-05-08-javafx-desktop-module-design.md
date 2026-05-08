# JavaFX Desktop Module Design

**Date**: 2026-05-08
**Status**: Approved
**Module**: `platform-desktop-javafx`

## Goal

Add a second desktop client module using JavaFX, FXML, TestFX, and Monocle alongside the existing Swing `platform-desktop` module. Full feature parity with the Swing client. Both modules maintained in parallel.

## Decisions

| Decision | Choice |
|----------|--------|
| Scope | Full parity — all screens, all dialogs |
| UI layout | FXML files + Kotlin controller classes |
| Swing future | Keep both modules in parallel |
| Theming | AtlantaFX CSS themes mapped to same 6 FlatLaf themes |
| Module name | `platform-desktop-javafx` |
| Architecture | Monolithic module (Approach A) — same pattern as `platform-desktop` |
| Testing | TestFX + Monocle for headless E2E, MockK for controller unit tests |

## Module Structure

```
platform-desktop-javafx/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── kotlin/io/github/rygel/outerstellar/platform/fx/
│   │   │   ├── app/                    # Entry point, Koin modules
│   │   │   ├── controller/             # FXML controllers (one per view)
│   │   │   ├── service/                # UI-specific services (theme, state, tray)
│   │   │   └── util/                   # Helpers (icon loading, etc.)
│   │   └── resources/
│   │       ├── fxml/                   # FXML layout files
│   │       ├── css/                    # Theme CSS files
│   │       └── messages.properties     # i18n (reuse existing keys)
│   └── test/
│       └── kotlin/.../fx/
│           ├── controller/             # Controller unit tests (no FX runtime)
│           └── e2e/                    # TestFX + Monocle headless E2E tests
```

## Maven Dependencies

- `platform-persistence-jooq` (transitive: `platform-core`, `platform-security`)
- `platform-sync-client`
- `org.openjfx:javafx-controls:21`, `javafx-fxml:21`, `javafx-web:21`
- `AtlantaFX` (CSS theme library)
- `outerstellar-theme`, `outerstellar-i18n` (shared with Swing)
- `koin-core-jvm`
- `kotlinx-serialization-json-jvm`
- `snakeyaml-engine`
- `logback-classic`
- Test: `testfx-junit5`, `monocle`, `mockk-jvm`, `assertj-core`, `testcontainers-postgresql`, `junit-jupiter`

No dependency on `platform-desktop` (Swing). Both are independent desktop clients sharing the same backend.

## Screens & FXML Layouts

| FXML File | Controller | Purpose |
|-----------|-----------|---------|
| `MainWindow.fxml` | `MainController` | Shell: sidebar nav + StackPane center + status bar |
| `MessagesView.fxml` | `MessagesController` | Message list (ListView), search bar, composer pane |
| `ContactsView.fxml` | `ContactsController` | Contact table (TableView), create/edit |
| `UsersView.fxml` | `UsersController` | Admin user table, enable/disable, role toggle |
| `NotificationsView.fxml` | `NotificationsController` | Notification list, read/unread, mark all |
| `ProfileView.fxml` | `ProfileController` | Profile info, notification prefs, danger zone |
| `LoginDialog.fxml` | `LoginController` | Login dialog (username + password) |
| `RegisterDialog.fxml` | `RegisterController` | Registration dialog |
| `ChangePasswordDialog.fxml` | `ChangePasswordController` | Password change dialog |
| `SettingsDialog.fxml` | `SettingsController` | Language + theme selector with live preview |
| `ConflictDialog.fxml` | `ConflictController` | Side-by-side conflict resolution |
| `ContactFormDialog.fxml` | `ContactFormController` | Full contact editing (name, emails, phones, socials) |
| `AboutDialog.fxml` | `AboutController` | App info |
| `HelpDialog.fxml` | `HelpController` | Help content |

Navigation: `MainController` holds center `StackPane`. Sidebar buttons swap views by loading FXML via `FXMLLoader`. Dialogs use JavaFX `Stage` + `showAndWait()`.

## Theming

| FlatLaf Theme | AtlantaFX Equivalent | CSS File |
|---------------|---------------------|----------|
| Dark | `AtlantaTheme.DARK` | `theme-dark.css` |
| Light | `AtlantaTheme.LIGHT` | `theme-light.css` |
| Darcula | `AtlantaTheme.DARCULA` | `theme-darcula.css` |
| IntelliJ | `AtlantaTheme.INTELLIJ` | `theme-intellij.css` |
| macOS Dark | `AtlantaTheme.CYPRUS_DARK` | `theme-cyprus-dark.css` |
| macOS Light | `AtlantaTheme.CYPRUS` | `theme-cyprus.css` |

`FxThemeManager` service class mirrors `ThemeManager` API but applies AtlantaFX CSS. Each theme CSS imports AtlantaFX base + app-specific overrides. Runtime switching via `scene.stylesheets.clear()` + load new CSS. Theme preference persisted via `DesktopStateProvider`.

## Testing Strategy

### Layer 1: Controller Unit Tests (JUnit 5 + MockK)
- No JavaFX runtime needed
- Test controller logic: button handlers, validation, state updates, service calls
- Run anywhere, fast

### Layer 2: Headless E2E Tests (TestFX + Monocle)
- Full UI flows without a display
- Monocle provides complete software-rendered pipeline — menus, dialogs, popups all work
- No Xvfb needed (unlike Swing tests)
- Surefire argLine: `--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED -Dtestfx.headless=true -Dtestfx.monocle=true`
- At minimum one E2E test per screen

### Layer 3: Integration Tests (TestFX + Testcontainers)
- Full app with real PostgreSQL database
- Run in Docker container only
- Login, create messages, sync, contacts CRUD

### CI
- Same Docker container pattern as existing desktop tests
- Monocle means no Xvfb dependency — tests run purely headless

## Build & Packaging

- `javafx-maven-plugin` — runs app via `mvn javafx:run`, handles module path
- `maven-shade-plugin` — fat JAR (same as `platform-web`)
- `maven-surefire-plugin` — TestFX + Monocle argLine
- Start script: `start-javafx.ps1` (parallels `start-web.ps1`, `start-swing.ps1`)
- No GraalVM native (same as Swing module)

## Non-UI Services Reused Directly

These classes from shared modules are UI-framework independent and need no changes:

- `ConnectivityChecker` (platform-core)
- `DeepLinkHandler` (duplicated in JavaFX module — do not extract from Swing module)
- `DesktopStateProvider` (duplicated in JavaFX module — do not extract from Swing module)
- `PersistentBatchingAnalyticsService` (platform-core)
- `UpdateService` (platform-core)
- `SyncService` (platform-sync-client)
- `MessageService`, `ContactService`, `NotificationService` (platform-core)
- All repository interfaces and implementations (platform-core, platform-persistence-jooq)
- All security services (platform-security)
- Domain models (platform-core)

## Swing-to-JavaFX Migration Map

| Swing Concept | JavaFX Equivalent |
|---------------|-------------------|
| `SyncViewModel` | Reuse directly (UI-framework agnostic) |
| `ThemeManager` + FlatLaf | `FxThemeManager` + AtlantaFX CSS |
| `SyncWindow` (JPanel builders) | FXML layouts |
| `SyncDialogs` (JDialog) | JavaFX Stage/Dialog |
| `SyncProfilePanel` | ProfileView.fxml |
| `MigLayout` | JavaFX layout panes (BorderPane, VBox, HBox, GridPane) |
| `FlatLaf` themes | AtlantaFX CSS themes |
| `assertj-swing` | TestFX |
| `GuiActionRunner.execute` | `Interact` (TestFX) |
| `SwingWorker` | JavaFX Task + ExecutorService |
| `JList` | `ListView` |
| `JTable` | `TableView` |
| `JMenuBar` | `MenuBar` |
| `JComboBox` | `ComboBox` |
| `JTextField` | `TextField` |
| `JTextArea` | `TextArea` |
| `JButton` | `Button` |
| `JLabel` | `Label` |
