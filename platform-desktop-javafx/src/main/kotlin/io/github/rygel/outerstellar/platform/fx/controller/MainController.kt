package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class MainController : KoinComponent {

    private val logger = LoggerFactory.getLogger(MainController::class.java)
    private val syncService: SyncService by inject()
    private val themeManager: FxThemeManager by inject()

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
        val loader = FXMLLoader(javaClass.getResource("/fxml/SettingsDialog.fxml"))
        val root = loader.load<javafx.scene.Parent>()
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Settings"
        val scene = Scene(root)
        themeManager.setScene(scene)
        stage.scene = scene
        stage.showAndWait()
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
}
