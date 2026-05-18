package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.app.FxAppConfig
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.update.UpdateService
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class MainController : KoinComponent {

    private val logger = LoggerFactory.getLogger(MainController::class.java)
    private val syncService: SyncService by inject()
    private val themeManager: FxThemeManager by inject()
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var sidebar: VBox
    @FXML private lateinit var centerPane: StackPane
    @FXML private lateinit var statusLabel: Label
    @FXML private lateinit var offlineBadge: Label

    @FXML private lateinit var navMessagesBtn: Button
    @FXML private lateinit var navContactsBtn: Button
    @FXML private lateinit var navUsersBtn: Button
    @FXML private lateinit var navNotificationsBtn: Button
    @FXML private lateinit var navProfileBtn: Button
    @FXML private lateinit var navSettingsBtn: Button
    @FXML private lateinit var navLoginBtn: Button
    @FXML private lateinit var navLogoutBtn: Button

    @FXML
    fun initialize() {
        updateAuthUi()
        navigateTo("MessagesView.fxml")
        statusLabel.textProperty().bind(viewModel.status)
        offlineBadge.visibleProperty().bind(viewModel.isOnline.not())
        navNotificationsBtn
            .textProperty()
            .bind(Bindings.concat("Notifications (", viewModel.unreadNotificationCount.asString(), ")"))
    }

    @FXML
    fun onNavMessages() {
        navigateTo("MessagesView.fxml")
    }

    @FXML
    fun onNavContacts() {
        navigateTo("ContactsView.fxml")
    }

    @FXML
    fun onNavUsers() {
        navigateTo("UsersView.fxml")
    }

    @FXML
    fun onNavNotifications() {
        navigateTo("NotificationsView.fxml")
    }

    @FXML
    fun onNavProfile() {
        navigateTo("ProfileView.fxml")
    }

    @FXML
    fun onNavSettings() {
        showSettingsDialog()
    }

    @FXML
    fun onNavLogin() {
        val loader = FXMLLoader(javaClass.getResource("/fxml/LoginDialog.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Login"
        val scene = Scene(root)
        themeManager.setScene(scene)
        stage.scene = scene
        stage.showAndWait()
        updateAuthUi()
    }

    @FXML
    fun onNavLogout() {
        syncService.logout()
        updateAuthUi()
    }

    @FXML
    fun onNewMessage() {
        navigateTo("MessagesView.fxml")
    }

    @FXML
    fun onNewContact() {
        showCreateContactDialog()
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
        showSettingsDialog()
    }

    @FXML
    fun onChangePassword() {
        showChangePasswordDialog()
    }

    @FXML
    fun onSettings() {
        showSettingsDialog()
    }

    @FXML
    fun onHelp() {
        showHelpDialog()
    }

    @FXML
    fun onCheckUpdates() {
        checkForUpdate()
    }

    @FXML
    fun onAbout() {
        showAboutDialog()
    }

    private fun navigateTo(fxmlFile: String) {
        try {
            val loader = FXMLLoader(javaClass.getResource("/fxml/$fxmlFile"))
            val view = loader.load<javafx.scene.Parent>()
            centerPane.children.setAll(view)
        } catch (e: Exception) {
            logger.warn("Navigate to {} failed: {}", fxmlFile, e.message)
            centerPane.children.clear()
        }
    }

    private fun updateAuthUi() {
        val loggedIn = syncService.userRole != null
        navLoginBtn.isVisible = !loggedIn
        navLoginBtn.isManaged = !loggedIn
        navLogoutBtn.isVisible = loggedIn
        navLogoutBtn.isManaged = loggedIn
        navUsersBtn.isVisible = loggedIn && syncService.userRole == UserRole.ADMIN.name
        navUsersBtn.isManaged = loggedIn && syncService.userRole == UserRole.ADMIN.name
    }

    private fun showDialog(fxmlFile: String, title: String) {
        try {
            val loader = FXMLLoader(javaClass.getResource("/fxml/$fxmlFile"))
            val root = loader.load<javafx.scene.Parent>()
            val stage = Stage()
            stage.initModality(Modality.APPLICATION_MODAL)
            stage.title = title
            val scene = Scene(root)
            themeManager.setScene(scene)
            stage.scene = scene
            stage.showAndWait()
        } catch (e: Exception) {
            logger.warn("Failed to open dialog {}: {}", fxmlFile, e.message)
        }
    }

    private fun showSettingsDialog() {
        showDialog("SettingsDialog.fxml", "Settings")
    }

    private fun showCreateContactDialog() {
        showDialog("ContactFormDialog.fxml", "New Contact")
    }

    private fun showChangePasswordDialog() {
        showDialog("ChangePasswordDialog.fxml", "Change Password")
    }

    private fun showHelpDialog() {
        showDialog("HelpDialog.fxml", "Help")
    }

    private fun showAboutDialog() {
        showDialog("AboutDialog.fxml", "About")
    }

    private fun checkForUpdate() {
        val config = get<FxAppConfig>()
        val service = UpdateService(config.version, config.updateUrl)
        Thread {
                val result = service.checkForUpdate()
                Platform.runLater {
                    val msg =
                        when (result) {
                            is UpdateService.UpdateResult.UpdateAvailable -> "Update available: ${result.version}"
                            is UpdateService.UpdateResult.UpToDate -> "You have the latest version"
                            is UpdateService.UpdateResult.NoUpdateUrl -> "Update checking is not configured"
                            is UpdateService.UpdateResult.CheckFailed -> "Update check failed: ${result.message}"
                        }
                    val dialog = buildInfoDialog("Update", msg)
                    dialog.show()
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun buildInfoDialog(title: String, message: String): Alert {
        val alert = Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK)
        alert.title = title
        alert.headerText = null
        return alert
    }
}
