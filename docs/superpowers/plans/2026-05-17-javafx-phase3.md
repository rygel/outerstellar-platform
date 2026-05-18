# JavaFX Desktop: Phase 3 — Dialogs, Infrastructure, Polish

**Goal:** Add remaining Swing-parity features: conflict resolution, help/about/feedback/change-password dialogs, window state persistence, keyboard shortcuts, status bar, connectivity checker, system tray, deep links, spell checker.

**Architecture:** Each dialog is a programmatic `Stage` with `Modality.APPLICATION_MODAL`. Infrastructure services (connectivity, deep links, tray) follow Swing patterns. FxSyncViewModel already provides all needed methods.

---

### Task 1: Conflict Resolution Dialog

**Files:**
- Rewrite: `ConflictController.kt`

Rewrite to programmatic dialog with `showAndWait()`:
- Two-column layout: left panel shows local message, right panel shows server version
- "Keep Mine" and "Accept Server" buttons
- Sets `conflictStrategy` field
- Loads conflict data from viewModel state

### Task 2: Help/About/Feedback Dialogs

**Files:**
- Rewrite: `HelpController.kt`, `AboutController.kt`, `FeedbackController.kt`

Simple info dialogs with icon, title, and message text. Use Alert or custom Stage.

### Task 3: Change Password Dialog

**Files:**
- Rewrite: `ChangePasswordController.kt`

Programmatic dialog: current password, new password, confirm password, "Change Password" button. Calls `viewModel.changePassword()`.

### Task 4: Window State Persistence

**Files:**
- Modify: `JavaFxApp.kt`

Save/restore window bounds, maximized state, theme, language when window opens/closes. Use `FxStateProvider` (already has `saveState`/`loadState`).

### Task 5: Keyboard Shortcuts

**Files:**
- Modify: `MainController.kt`

Add keyboard accelerators to menu items (Ctrl+N new message, F5 sync, Ctrl+, preferences, etc.) in `createScene()`.

### Task 6: Status Bar + Connectivity Checker

**Files:**
- Modify: `MainController.kt`

The status bar already exists in FXML. Ensure offline badge shows when `viewModel.isOnline` is false. Start connectivity checker on main window creation.

### Task 7: System Tray Notifier

**Files:**
- Modify: `FxTrayNotifier.kt` (already exists, verify it works)
- Modify: `JavaFxApp.kt` — start tray notifier

### Task 8: Deep Link Handler

**Files:**
- No JavaFX equivalent of `java.awt.Desktop.setOpenURIHandler` — skip for now (platform limitation)

### Task 9: Spell Checker Integration

**Files:**
- Skip for now — requires Swing JTextArea components or custom JavaFX implementation
