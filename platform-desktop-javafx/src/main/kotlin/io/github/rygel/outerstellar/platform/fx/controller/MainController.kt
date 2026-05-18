package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class MainController(private val onLogout: () -> Unit) : KoinComponent {

    private val logger = LoggerFactory.getLogger(MainController::class.java)
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
    private var messagesView: Parent? = null
    private val contactsController = ContactsController()
    private var contactsView: Parent? = null

    fun createScene(): Scene {
        val loader = FXMLLoader(javaClass.getResource("/fxml/MainWindow.fxml"))
        loader.setController(this)
        val root = loader.load<Parent>()
        val scene = Scene(root)
        bindState()
        viewModel.loadMessages().runInBackground()
        viewModel.loadContacts().runInBackground()
        showView("MESSAGES")
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

    @FXML
    fun onNavMessages() {
        showView("MESSAGES")
    }

    @FXML
    fun onNavContacts() {
        showView("CONTACTS")
    }

    @FXML
    fun onNavUsers() {
        showView("USERS")
    }

    @FXML
    fun onNavNotifications() {
        showView("NOTIFICATIONS")
    }

    @FXML
    fun onNavProfile() {
        showView("PROFILE")
    }

    @FXML
    fun onNavSettings() {
        showDialog("/fxml/SettingsDialog.fxml")
    }

    @FXML
    fun onNavLogin() {
        showLogin()
    }

    @FXML
    fun onNavLogout() {
        onLogout()
    }

    @FXML
    fun onNewMessage() {
        showView("MESSAGES")
    }

    @FXML
    fun onNewContact() {
        showDialog("/fxml/ContactFormDialog.fxml")
    }

    @FXML
    fun onSync() {
        viewModel.sync().runInBackground()
    }

    @FXML
    fun onSyncAll() {
        viewModel.sync(true).runInBackground()
    }

    @FXML
    fun onExit() {
        Platform.exit()
    }

    @FXML
    fun onPreferences() {
        showDialog("/fxml/SettingsDialog.fxml")
    }

    @FXML
    fun onChangePassword() {
        showDialog("/fxml/ChangePasswordDialog.fxml")
    }

    @FXML
    fun onSettings() {
        showDialog("/fxml/SettingsDialog.fxml")
    }

    @FXML
    fun onHelp() {
        showDialog("/fxml/HelpDialog.fxml")
    }

    @FXML
    fun onCheckUpdates() {
        checkForUpdate()
    }

    @FXML
    fun onAbout() {
        showDialog("/fxml/AboutDialog.fxml")
    }

    private fun showView(view: String) {
        val content =
            when (view) {
                "MESSAGES" -> messagesView ?: messagesController.createView().also { messagesView = it }
                "CONTACTS" -> contactsView ?: contactsController.createView().also { contactsView = it }
                else -> return
            }
        centerPane.children.setAll(content)
    }

    private fun showLogin() {
        val controller =
            LoginController(
                onLoginSuccess = {
                    navLoginBtn.isVisible = false
                    navLoginBtn.isManaged = false
                    navLogoutBtn.isVisible = true
                    navLogoutBtn.isManaged = true
                    showView("MESSAGES")
                }
            )
        val loginScene = controller.createScene()
        Stage().apply {
            initModality(Modality.APPLICATION_MODAL)
            title = "Login"
            scene = loginScene
            showAndWait()
        }
    }

    private fun showDialog(fxmlPath: String) {
        try {
            val loader = FXMLLoader(javaClass.getResource(fxmlPath))
            val root = loader.load<Parent>()
            val stage =
                Stage().apply {
                    initModality(Modality.APPLICATION_MODAL)
                    title = "Dialog"
                    scene = Scene(root)
                    showAndWait()
                }
        } catch (e: Exception) {
            logger.warn("Failed to open dialog {}: {}", fxmlPath, e.message)
        }
    }

    private fun checkForUpdate() {}
}
