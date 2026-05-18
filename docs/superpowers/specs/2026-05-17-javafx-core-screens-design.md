# JavaFX Desktop: Core Screens Implementation

## Goal

Bring the JavaFX desktop module to feature parity with the Swing application for core screens: messages list + composer, contacts table, and login/register. Uses programmatic JavaFX UI (no FXML).

## Architecture

### Navigation
- `JavaFxApp` manages the primary `Stage`
- Scene-based navigation: `LoginScene` → `MainScene`
- `MainScene` has a sidebar nav (left, fixed width) and a content area (right) that swaps between views
- Views are `Region` subclasses built programmatically

### Dependency Injection
- Koin DI via `FxModule.kt` (already exists)
- `FxSyncViewModel` bridges to `DesktopSyncEngine` (already exists)

### State Updates
- `FxSyncViewModel` already uses `Platform.runLater` for thread-safe UI updates
- Controllers observe state via `FxSyncViewModel` properties

## Screens

### 1. Login/Register (extend existing)
- Tabbed dialog or toggle: Login | Register
- **Login**: Username field, Password field, Sign In button, error label
- **Register**: Username field, Password field, Confirm Password field, Register button, error label
- On success → transition to MainScene

### 2. Sidebar
- Left panel with navigation buttons stacked vertically
- Buttons: Messages, Contacts
- Active button highlighted
- Bottom area: sync status, online indicator, logout button

### 3. Messages View
- **Search bar**: Text field (debounced, triggers `viewModel.searchQuery`)
- **Message list**: `ListView<MessageSummary>` with custom `ListCell`
  - Shows: author (bold), content preview, timestamp
  - Badges: "(Local)" in accent color for dirty messages, "[CONFLICT]" in red for conflicted
  - Double-click conflicted message → conflict resolution dialog
- **Composer** (bottom):
  - Author text field
  - Content text area (word wrap, 3-4 line height)
  - Create button
  - Sync button (refresh icon)

### 4. Contacts View
- **Header**: "Contacts" title + Create Contact button
- **Table**: `TableView<ContactSummary>`
  - Columns: Name, Emails, Phones, Company, Department
  - Double-click row → opens contact form in edit mode
- **Contact Form Dialog**: Modal dialog
  - Fields: Name (required), Emails, Phones, Social Media, Company, Department, Address
  - Save / Cancel buttons
  - Validation: Name required

### 5. Conflict Resolution Dialog
- Modal dialog shown on double-clicking conflicted message
- Two panels side-by-side: local version vs server version
- Buttons: "Keep Mine" | "Accept Server"

## Files to Create/Modify

| File | Action |
|------|--------|
| `JavaFxApp.kt` | Modify scene management, add MainScene transition |
| `LoginController.kt` | Extend with register tab, shared transition callback |
| `MainController.kt` | Rewrite with sidebar + content area |
| `MessagesController.kt` | Full messages list + composer implementation |
| `ContactsController.kt` | Full contacts table + CRUD dialogs |
| `FxSyncViewModel.kt` | Already wired, ensure all needed properties exposed |
| (New) `ContactFormDialog.kt` | Create/edit contact modal dialog |
| `FxModule.kt` | No changes needed (DI already wired) |

## Testing

- Compile check: `mvn -pl platform-desktop-javafx compile -q`
- No automated UI tests in this phase (JavaFX headless testing is complex)
- Manual verification via Podman/Docker with Xvfb

## Out of Scope (Phase 1)

- Admin user management (Users view)
- Notifications view
- Profile view (email, avatar, notification prefs)
- Settings dialog (theme/language)
- Deep link handler
- Spell checker
- System tray
- Update checker
- Window state persistence
