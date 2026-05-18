# JavaFX Desktop: Core Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the JavaFX desktop module to feature parity with the Swing application for core screens: messages list + composer, contacts table with CRUD, and login/register.

**Architecture:** Scene-based navigation where `JavaFxApp` manages the primary `Stage`. Login → MainWindow with sidebar navigation and `StackPane` content area that switches between FXML-based views. Koin DI wires services. `FxSyncViewModel` bridges to `DesktopSyncEngine` with JavaFX property bindings.

**Tech Stack:** JavaFX, FXML, AtlanFX theming, Koin DI, kotlinx.serialization

---

### Task 1: Rewrite MainController for sidebar navigation + FXML loading

**Files:**
- Modify: `platform-desktop-javafx/.../fx/controller/MainController.kt`
- Modify: `platform-desktop-javafx/.../fx/app/JavaFxApp.kt`

The current `MainController` builds UI programmatically. The FXML `MainWindow.fxml` already has a complete layout with sidebar, menu bar, center StackPane, and status bar. Rewrite `MainController` to load from FXML and implement the navigation switching.

- [ ] **Step 1: Read the current MainController.kt and MainWindow.fxml**

- [ ] **Step 2: Rewrite MainController to load from FXML**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainController(private val onLogout: () -> Unit) : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var navMessagesBtn: Button
    @FXML private lateinit var navContactsBtn: Button
    @FXML private lateinit var navUsersBtn: Button
    @FXML private lateinit var navNotificationsBtn: Button
    @FXML private lateinit var navProfileBtn: Button
    @FXML private lateinit var navSettingsBtn: Button
    @FXML private lateinit var navLoginBtn: Button
    @FXML private lateinit var navLogoutBtn: Button
    @FXML private lateinit var centerPane: StackPane
    @FXML private lateinit var statusLabel: Label
    @FXML private lateinit var offlineBadge: Label

    private val messagesController = MessagesController()
    private val contactsController = ContactsController()
    private var messagesView: Parent? = null
    private var contactsView: Parent? = null

    fun createScene(): Scene {
        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        loader.setController(this)
        val root = loader.load<Parent>()
        val scene = Scene(root)
        bindState()
        loadMessages()
        return scene
    }

    private fun bindState() {
        statusLabel.textProperty().bind(viewModel.status)
        offlineBadge.visibleProperty().bind(viewModel.isOnline.not())
        viewModel.isLoggedIn.addListener { _, _, loggedIn ->
            navLoginBtn.isVisible = !loggedIn
            navLoginBtn.isManaged = !loggedIn
            navLogoutBtn.isVisible = loggedIn
            navLogoutBtn.isManaged = loggedIn
            navUsersBtn.isVisible = loggedIn && viewModel.userRole.get() == "ADMIN"
            navUsersBtn.isManaged = loggedIn && viewModel.userRole.get() == "ADMIN"
            navNotificationsBtn.isVisible = loggedIn
            navNotificationsBtn.isManaged = loggedIn
            navProfileBtn.isVisible = loggedIn
            navProfileBtn.isManaged = loggedIn
        }
    }

    @FXML private fun onNavMessages() { showView("MESSAGES") }
    @FXML private fun onNavContacts() { showView("CONTACTS") }
    @FXML private fun onNavUsers() { showView("USERS") }
    @FXML private fun onNavNotifications() { showView("NOTIFICATIONS") }
    @FXML private fun onNavProfile() { showView("PROFILE") }
    @FXML private fun onNavSettings() { showDialog("/fxml/SettingsDialog.fxml") }
    @FXML private fun onNavLogin() { showLogin() }
    @FXML private fun onNavLogout() { viewModel.logout() }

    private fun showView(view: String) {
        val content = when (view) {
            "MESSAGES" -> messagesView ?: messagesController.createView().also { messagesView = it }
            "CONTACTS" -> contactsView ?: contactsController.createView().also { contactsView = it }
            else -> return
        }
        centerPane.children.setAll(content)
    }

    private fun showLogin() {
        val loginController = LoginController(onLoginSuccess = {
            navLoginBtn.isVisible = false
            navLoginBtn.isManaged = false
            navLogoutBtn.isVisible = true
            navLogoutBtn.isManaged = true
            showView("MESSAGES")
        })
        loginController.createScene() // ignore — scene used by JavaFxApp
    }

    private fun loadMessages() {
        viewModel.loadMessages()
        viewModel.loadContacts()
    }
}
```

- [ ] **Step 3: Update JavaFxApp.kt to pass onLogout properly**

```kotlin
private fun showMainWindow(primaryStage: Stage, themeManager: FxThemeManager) {
    val mainController = MainController(onLogout = {
        primaryStage.scene = null
        showLogin(primaryStage, themeManager)
    })
    val scene = mainController.createScene()
    primaryStage.title = "Outerstellar"
    primaryStage.minWidth = 1000.0
    primaryStage.minHeight = 750.0
    primaryStage.scene = scene
}
```

- [ ] **Step 4: Remove programmatic UI code** — delete old `VBox`/`BorderPane`/`HBox` layout code from `MainController.createScene()` (the old programmatic version before the FXML version above)

- [ ] **Step 5: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/MainController.kt platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/app/JavaFxApp.kt
git commit -m "feat(javafx): rewrite MainController with FXML-based navigation"
```

---

### Task 2: Rewrite MessagesController for programmatic + FXML-free view

**Files:**
- Modify: `platform-desktop-javafx/.../fx/controller/MessagesController.kt`

The current `MessagesController` uses `@FXML` annotations. Rewrite it to build the messages view programmatically with a `createView(): Parent` method. The view should contain a search bar, message list, and composer (author field, content area, create button).

- [ ] **Step 1: Read the current MessagesController.kt**

- [ ] **Step 2: Rewrite with createView() method**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.MessageSummary
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class MessagesController : KoinComponent {

    private companion object {
        const val MESSAGE_PREVIEW_LENGTH = 80
    }

    private val logger = LoggerFactory.getLogger(MessagesController::class.java)
    private val viewModel: FxSyncViewModel by inject()
    private val searchField = TextField()
    private val messagesList = ListView<MessageSummary>()
    private val authorField = TextField()
    private val contentArea = TextArea()
    private val createButton = Button("Create")

    fun createView(): Parent = VBox(10.0).apply {
        padding = Insets(10.0)

        // Search bar
        children.add(HBox(10.0).apply {
            children.addAll(
                Label("Search:"),
                searchField.apply { HBox.setHgrow(this, Priority.ALWAYS) },
                Button("Sync").apply { setOnAction { onSync() } },
            )
            alignment = Pos.CENTER_LEFT
        })

        // Message list
        children.add(messagesList.apply { VBox.setVgrow(this, Priority.ALWAYS) })

        // Composer
        children.add(VBox(5.0).apply {
            children.addAll(
                HBox(10.0).apply {
                    children.addAll(
                        Label("Author:"),
                        authorField.apply { HBox.setHgrow(this, Priority.ALWAYS) },
                    )
                    alignment = Pos.CENTER_LEFT
                },
                contentArea.apply { prefHeight = 80.0; isWrapText = true },
                HBox(10.0).apply {
                    children.add(createButton)
                    alignment = Pos.CENTER_RIGHT
                },
            )
        })

        // Bindings
        messagesList.items = viewModel.messages
        searchField.textProperty().bindBidirectional(viewModel.searchQuery)
        authorField.textProperty().bindBidirectional(viewModel.author)
        contentArea.textProperty().bindBidirectional(viewModel.content)

        // Cell factory
        messagesList.setCellFactory {
            object : javafx.scene.control.ListCell<MessageSummary>() {
                override fun updateItem(item: MessageSummary?, empty: Boolean) {
                    super.updateItem(item, empty)
                    if (empty || item == null) {
                        text = null; graphic = null
                    } else {
                        val preview = item.content.take(MESSAGE_PREVIEW_LENGTH) +
                            if (item.content.length > MESSAGE_PREVIEW_LENGTH) "..." else ""
                        val label = Label("${item.author}: $preview")
                        when {
                            item.hasConflict -> { label.style = "-fx-text-fill: red;"; label.text = "[CONFLICT] $label" }
                            item.syncId.isBlank() -> { label.style = "-fx-text-fill: gray; -fx-font-style: italic;"; label.text = "(Local) $label" }
                        }
                        graphic = label
                    }
                }
            }
        }

        // Double-click conflict
        messagesList.setOnMouseClicked { event ->
            if (event.clickCount == 2) {
                val selected = messagesList.selectionModel.selectedItem
                if (selected != null && selected.hasConflict) {
                    showConflictDialog(selected)
                }
            }
        }

        // Debounced search
        searchField.textProperty().addListener { _, _, _ -> viewModel.loadMessages().runInBackground() }
        createButton.setOnAction { onCreateMessage() }

        viewModel.loadMessages().runInBackground()
    }

    private fun onCreateMessage() {
        val author = authorField.text.trim()
        val content = contentArea.text.trim()
        if (author.isBlank() || content.isBlank()) return
        viewModel.createLocalMessage(author, content).also { task ->
            task.setOnSucceeded {
                task.value.onSuccess {
                    authorField.clear(); contentArea.clear(); viewModel.loadMessages().runInBackground()
                }.onFailure { logger.warn("Create message failed: {}", it.message) }
            }
        }.runInBackground()
    }

    private fun onSync() {
        viewModel.sync().also { task ->
            task.setOnSucceeded { task.value.onSuccess { viewModel.loadMessages().runInBackground() }.onFailure { logger.warn("Sync failed: {}", it.message) } }
        }.runInBackground()
    }

    private fun showConflictDialog(msg: MessageSummary) {
        val dialog = ConflictController()
        dialog.setMessage(msg)
        dialog.showAndWait()
        if (dialog.conflictStrategy != null) viewModel.loadMessages().runInBackground()
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/MessagesController.kt
git commit -m "feat(javafx): rewrite MessagesController with programmatic UI"
```

---

### Task 3: Rewrite ContactsController with programmatic UI + ContactFormDialog

**Files:**
- Modify: `platform-desktop-javafx/.../fx/controller/ContactsController.kt`
- Modify: `platform-desktop-javafx/.../fx/controller/ContactFormController.kt`

Rewrite ContactsController with a `createView(): Parent` method showing a TableView with columns for Name, Emails, Phones, Company, Department, plus a Create Contact button.

ContactFormController should build its dialog programmatically (modal dialog with form fields for Name, Emails, Phones, Social Media, Company, Department, Address).

- [ ] **Step 1: Read current ContactsController.kt and ContactFormController.kt**

- [ ] **Step 2: Rewrite ContactsController**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.ContactSummary
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ContactsController : KoinComponent {

    private val logger = LoggerFactory.getLogger(ContactsController::class.java)
    private val viewModel: FxSyncViewModel by inject()
    private val contactsTable = TableView<ContactSummary>()

    fun createView(): Parent = VBox(10.0).apply {
        padding = Insets(10.0)

        children.add(HBox(10.0).apply {
            children.addAll(
                Label("Contacts").apply { style = "-fx-font-size: 18px; -fx-font-weight: bold;" },
                HBox().apply { HBox.setHgrow(this, Priority.ALWAYS) },
                Button("Create Contact").apply { setOnAction { onCreateContact() } },
            )
        })

        contactsTable.apply {
            VBox.setVgrow(this, Priority.ALWAYS)
            val nameCol = TableColumn<ContactSummary, String>("Name").apply {
                setCellValueFactory { SimpleStringProperty(it.value.name) }
            }
            val emailCol = TableColumn<ContactSummary, String>("Emails").apply {
                setCellValueFactory { SimpleStringProperty(it.value.emails.joinToString("; ")) }
            }
            val phoneCol = TableColumn<ContactSummary, String>("Phones").apply {
                setCellValueFactory { SimpleStringProperty(it.value.phones.joinToString("; ")) }
            }
            val companyCol = TableColumn<ContactSummary, String>("Company").apply {
                setCellValueFactory { SimpleStringProperty(it.value.company) }
            }
            val deptCol = TableColumn<ContactSummary, String>("Department").apply {
                setCellValueFactory { SimpleStringProperty(it.value.department) }
            }
            columns.addAll(nameCol, emailCol, phoneCol, companyCol, deptCol)
            setOnMouseClicked { event ->
                if (event.clickCount == 2) {
                    contactsTable.selectionModel.selectedItem?.let { showContactFormDialog(it.syncId) }
                }
            }
        }
        children.add(contactsTable)

        viewModel.loadContacts().runInBackground()
    }

    private fun onCreateContact() = showContactFormDialog(null)

    private fun showContactFormDialog(syncId: String?) {
        val controller = ContactFormController(syncId)
        controller.showAndWait()
        viewModel.loadContacts().runInBackground()
    }
}
```

- [ ] **Step 3: Rewrite ContactFormController with programmatic dialog**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.ContactSummary
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.GridPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ContactFormController(private val syncId: String?) : KoinComponent {

    private val logger = LoggerFactory.getLogger(ContactFormController::class.java)
    private val viewModel: FxSyncViewModel by inject()
    private val nameField = TextField()
    private val emailsField = TextField()
    private val phonesField = TextField()
    private val socialField = TextField()
    private val companyField = TextField()
    private val addressField = TextField()
    private val departmentField = TextField()

    private var existingContact: ContactSummary? = null

    fun showAndWait() {
        val stage = Stage().apply {
            initModality(Modality.APPLICATION_MODAL)
            title = if (syncId != null) "Edit Contact" else "Create Contact"
            minWidth = 400.0
            minHeight = 350.0
        }

        // Load existing data if editing
        val existing = syncId?.let { viewModel.engine.state.contacts.find { c -> c.syncId == syncId } }
        existingContact = existing
        existing?.let {
            nameField.setText(it.name)
            emailsField.setText(it.emails.joinToString(", "))
            phonesField.setText(it.phones.joinToString(", "))
            socialField.setText(it.socialMedia.joinToString(", "))
            companyField.setText(it.company)
            addressField.setText(it.companyAddress)
            departmentField.setText(it.department)
        }

        val form = GridPane().apply {
            hgap = 10.0; vgap = 8.0; padding = Insets(20.0)
            addRow(0, Label("Name:"), nameField.apply { prefWidth = 250.0 })
            addRow(1, Label("Emails:"), emailsField)
            addRow(2, Label("Phones:"), phonesField)
            addRow(3, Label("Social:"), socialField)
            addRow(4, Label("Company:"), companyField)
            addRow(5, Label("Address:"), addressField)
            addRow(6, Label("Department:"), departmentField)
        }

        val saveBtn = Button(if (syncId != null) "Update Contact" else "Save Contact")
        val cancelBtn = Button("Cancel")
        val errorLabel = Label()

        saveBtn.setOnAction {
            if (nameField.text.isBlank()) {
                errorLabel.text = "Name is required"; return@setOnAction
            }
            saveBtn.isDisable = true
            val task = if (syncId != null) {
                existingContact?.let { existing ->
                    viewModel.updateContact(
                        existing.syncId, nameField.text.trim(),
                        emailsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        phonesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        socialField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        companyField.text.trim(), addressField.text.trim(), departmentField.text.trim(),
                    )
                }
            } else {
                viewModel.createContact(
                    nameField.text.trim(),
                    emailsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    phonesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    socialField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    companyField.text.trim(), addressField.text.trim(), departmentField.text.trim(),
                )
            }
            task?.also {
                it.setOnSucceeded {
                    it.value.onSuccess { stage.close() }.onFailure { e ->
                        saveBtn.isDisable = false; errorLabel.text = e.message ?: "Error"
                    }
                }
            }?.runInBackground()
        }
        cancelBtn.setOnAction { stage.close() }

        stage.scene = Scene(VBox(10.0, form, errorLabel, HBox(10.0, saveBtn, cancelBtn).apply {
            padding = Insets(0.0, 20.0, 20.0, 20.0)
        }))
        stage.showAndWait()
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ContactsController.kt platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/ContactFormController.kt
git commit -m "feat(javafx): rewrite ContactsController and ContactFormController with programmatic UI"
```

---

### Task 4: Wire login state and navigation in JavaFxApp

**Files:**
- Modify: `platform-desktop-javafx/.../fx/app/JavaFxApp.kt`

- [ ] **Step 1: Read current JavaFxApp.kt**

- [ ] **Step 2: Add main() entry point and wire login→main transition**

The current `JavaFxApp` already has this structure, but `onLoginSuccess` in `JavaFxApp.showLogin()` transitions to `showMainWindow()`. Update the login success callback to properly initialize the main window scene.

No changes needed — the current code already does this correctly. `LoginController` calls `onLoginSuccess()` → `showMainWindow()`.

- [ ] **Step 3: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/app/JavaFxApp.kt
git commit -m "feat(javafx): wire login-to-main navigation"
```

---

### Task 5: Update FxSyncViewModel to expose updateContact and engine state

**Files:**
- Modify: `platform-desktop-javafx/.../fx/viewmodel/FxSyncViewModel.kt`

The view model needs `updateContact()` method and `engine` property access for the ContactFormController.

- [ ] **Step 1: Read current FxSyncViewModel.kt**

- [ ] **Step 2: Add updateContact() method and engine property**

```kotlin
// Add to class body:
val engine: SyncEngine get() = _engine  // expose for direct state access

fun updateContact(
    syncId: String,
    name: String,
    emails: List<String>,
    phones: List<String>,
    socialMedia: List<String>,
    company: String,
    companyAddress: String,
    department: String,
): Task<Result<Unit>> = task("updateContact") {
    engine.updateContact(syncId, name, emails, phones, socialMedia, company, companyAddress, department)
}
```

Change the constructor parameter from `private val engine` to `private val _engine`:
```kotlin
class FxSyncViewModel(private val _engine: SyncEngine) {
    val engine: SyncEngine get() = _engine
    // replace all internal references from `engine` to `_engine`
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/viewmodel/FxSyncViewModel.kt
git commit -m "feat(javafx): expose engine state and updateContact in FxSyncViewModel"
```

---

### Task 6: Add Login/Register tab switching

**Files:**
- Modify: `platform-desktop-javafx/.../fx/controller/LoginController.kt`

The current LoginController has username/password fields and a login button. Add a toggle between Login and Register modes.

- [ ] **Step 1: Read current LoginController.kt**

- [ ] **Step 2: Add register tab toggle**

```kotlin
package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LoginController(
    private val onLoginSuccess: () -> Unit,
    private val onCancel: (() -> Unit)? = null,
) : KoinComponent {

    private val viewModel: FxSyncViewModel by inject()
    private val errorLabel = Label()
    private val usernameField = TextField()
    private val passwordField = PasswordField()
    private val confirmPassField = PasswordField()
    private val actionButton = Button("Sign In")
    private val toggleLink = Hyperlink("Don't have an account? Register")
    private var isRegisterMode = false

    fun createScene(): Scene {
        confirmPassField.isVisible = false
        confirmPassField.isManaged = false

        val root = VBox(15.0).apply {
            alignment = Pos.CENTER
            padding = Insets(40.0)
            children.addAll(
                Label("Outerstellar").apply { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
                Label("Sign in to your account").apply { id = "modeLabel"; style = "-fx-font-size: 14px;" },
                usernameField.apply { promptText = "Username"; prefWidth = 250.0 },
                passwordField.apply { promptText = "Password"; prefWidth = 250.0; onAction = { onAction() } },
                confirmPassField.apply { promptText = "Confirm Password"; prefWidth = 250.0; onAction = { onAction() } },
                errorLabel.apply { style = "-fx-text-fill: red;"; isVisible = false },
                actionButton.apply { prefWidth = 250.0; setOnAction { onAction() } },
                toggleLink.apply { setOnAction { toggleMode() } },
            )
        }
        return Scene(root, 400.0, 420.0)
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        val showConfirm = isRegisterMode
        confirmPassField.isVisible = showConfirm
        confirmPassField.isManaged = showConfirm
        actionButton.text = if (isRegisterMode) "Register" else "Sign In"
        toggleLink.text = if (isRegisterMode) "Already have an account? Sign In" else "Don't have an account? Register"
        (actionButton.scene?.lookup("#modeLabel") as? Label)?.text =
            if (isRegisterMode) "Create a new account" else "Sign in to your account"
        errorLabel.isVisible = false
    }

    private fun onAction() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        if (username.isBlank() || password.isBlank()) {
            showError("Username and password are required"); return
        }
        if (isRegisterMode) {
            val confirm = confirmPassField.text
            if (password != confirm) { showError("Passwords do not match"); return }
        }
        actionButton.isDisable = true
        errorLabel.isVisible = false
        val task = if (isRegisterMode) viewModel.register(username, password) else viewModel.login(username, password)
        task.also {
            it.setOnSucceeded {
                actionButton.isDisable = false
                it.value.onSuccess { onLoginSuccess() }.onFailure { e -> showError(e.message ?: "Failed") }
            }
            it.setOnFailed { actionButton.isDisable = false; showError("Connection error") }
        }.runInBackground()
    }

    private fun showError(message: String) {
        errorLabel.text = message; errorLabel.isVisible = true
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-desktop-javafx/src/main/kotlin/io/github/rygel/outerstellar/platform/fx/controller/LoginController.kt
git commit -m "feat(javafx): add register tab to login screen"
```

---

### Task 7: Verify full module compilation

- [ ] **Step 1: Run full compilation**

```bash
mvn -pl platform-desktop-javafx compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 2: Run the full reactor build to check for cross-module issues**

```bash
mvn -pl platform-desktop-javafx,platform-core,platform-security,platform-sync-client compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat(javafx): implement core screens with FXML and programmatic UI"
git push -u origin feat/javafx-core-screens
```
