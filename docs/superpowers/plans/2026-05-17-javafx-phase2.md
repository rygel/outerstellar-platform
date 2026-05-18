# JavaFX Desktop: Phase 2 — Admin, Profile, Notifications, Settings

**Goal:** Add admin user management, notifications view, profile view (with danger zone), and settings dialog to the JavaFX client.

**Architecture:** Extend MainController with new views. Each view gets a `createView(): Parent` method. Dialogs are programmatic modal stages. All use FxSyncViewModel bindings.

**Tech Stack:** JavaFX, programmatic UI (no new FXML), Koin DI.

---

### Task 1: Users Admin View

**Files:**
- Create: `platform-desktop-javafx/.../fx/controller/UsersController.kt`

- [ ] **Step 1: Create UsersController.kt**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.CheckBoxTableCell
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UsersController : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()
    private val usersTable = TableView<io.github.rygel.outerstellar.platform.model.UserSummary>()
    private val toggleEnabledBtn = Button("Toggle Enabled")
    private val toggleRoleBtn = Button("Toggle Role")

    fun createView(): Parent = VBox(10.0).apply {
        padding = Insets(10.0)
        children.add(Label("Users Administration").apply { style = "-fx-font-size: 18px; -fx-font-weight: bold;" })
        usersTable.apply {
            VBox.setVgrow(this, Priority.ALWAYS)
            val usernameCol = TableColumn<io.github.rygel.outerstellar.platform.model.UserSummary, String>("Username").apply {
                setCellValueFactory { SimpleStringProperty(it.value.username) }
            }
            val emailCol = TableColumn<io.github.rygel.outerstellar.platform.model.UserSummary, String>("Email").apply {
                setCellValueFactory { SimpleStringProperty(it.value.email) }
            }
            val roleCol = TableColumn<io.github.rygel.outerstellar.platform.model.UserSummary, String>("Role").apply {
                setCellValueFactory { SimpleStringProperty(it.value.role) }
            }
            val enabledCol = TableColumn<io.github.rygel.outerstellar.platform.model.UserSummary, Boolean>("Enabled")
            columns.addAll(usernameCol, emailCol, roleCol, enabledCol)
            setItems(viewModel.adminUsers)
        }
        children.add(usersTable)
        children.add(HBox(10.0).apply {
            children.addAll(toggleEnabledBtn, toggleRoleBtn)
        })
        toggleEnabledBtn.setOnAction {
            usersTable.selectionModel.selectedItem?.let {
                viewModel.setUserEnabled(it.id, !it.enabled).runInBackground()
            }
        }
        toggleRoleBtn.setOnAction {
            usersTable.selectionModel.selectedItem?.let {
                val newRole = if (it.role == "ADMIN") "USER" else "ADMIN"
                viewModel.setUserRole(it.id, newRole).runInBackground()
            }
        }
        viewModel.loadUsers().runInBackground()
    }
}
```

- [ ] **Step 2: Wire into MainController**

In `MainController.kt`, add:
```kotlin
private val usersController = UsersController()
private var usersView: Parent? = null
```

Update `showView("USERS")` section.

- [ ] **Step 3: Compile**

```bash
mvn -pl platform-desktop-javafx compile -q
```

- [ ] **Step 4: Commit**

---

### Task 2: Notifications View

**Files:**
- Rewrite: `platform-desktop-javafx/.../fx/controller/NotificationsController.kt`

- [ ] **Step 1: Rewrite NotificationsController with createView()**

Programmatic view with:
- Header: "Notifications" title + "Mark All Read" button
- ListView<NotificationSummary>
- Custom cell: bold unread dot + title + body (smaller font)
- Bottom: "Mark Read" button (for selected)
- Bind via `viewModel.notifications`

- [ ] **Step 2: Wire into MainController**

- [ ] **Step 3: Compile**

- [ ] **Step 4: Commit**

---

### Task 3: Profile View

**Files:**
- Rewrite: `platform-desktop-javafx/.../fx/controller/ProfileController.kt`

- [ ] **Step 1: Rewrite ProfileController with createView()**

Programmatic view with three sections:
1. **Profile Information** — email, username, avatar URL fields + "Save Profile" button
2. **Notification Preferences** — email notification checkbox, push notification checkbox + "Save Preferences" button
3. **Danger Zone** — "Delete Account" button (red, with confirmation dialog)

Use `viewModel.updateProfile()`, `viewModel.updateNotificationPreferences()`, `viewModel.deleteAccount()`.

- [ ] **Step 2: Wire into MainController**

- [ ] **Step 3: Compile**

- [ ] **Step 4: Commit**

---

### Task 4: Settings Dialog

**Files:**
- Create: `platform-desktop-javafx/.../fx/controller/SettingsController.kt`

- [ ] **Step 1: Create SettingsController with showAndWait()**

Programmatic modal dialog with:
- **Theme** ComboBox (Dark, Light, Darcula, IntelliJ, macOS Dark, macOS Light)
- **Language** ComboBox (English, French)
- Apply and Cancel buttons
- On Apply: change theme via `FxThemeManager`, change locale, refresh state

- [ ] **Step 2: Wire into MainController**

Update `onSettings()` to show the dialog.

- [ ] **Step 3: Compile**

- [ ] **Step 4: Commit**

---

### Task 5: Update menu handlers in MainController

**Files:**
- Modify: `MainController.kt`

- [ ] **Step 1: Implement remaining menu handlers**

Wire `onNewMessage`, `onNewContact`, `onSyncAll`, `onHelp`, `onAbout`, `onCheckUpdates`, `onPreferences` to open the appropriate views or dialogs.

- [ ] **Step 2: Compile**

- [ ] **Step 3: Commit**
