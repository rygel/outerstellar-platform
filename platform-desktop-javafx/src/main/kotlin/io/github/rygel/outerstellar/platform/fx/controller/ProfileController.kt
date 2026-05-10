package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ProfileController : KoinComponent {

    private val logger = LoggerFactory.getLogger(ProfileController::class.java)
    private val engine: DesktopSyncEngine by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var emailField: TextField
    @FXML private lateinit var emailNotifCheckbox: CheckBox
    @FXML private lateinit var pushNotifCheckbox: CheckBox
    @FXML private lateinit var deleteAccountBtn: Button

    @FXML
    fun initialize() {
        loadProfile()
    }

    @FXML
    fun onSaveProfile() {
        val email = emailField.text.trim()
        if (email.isBlank()) return
        Thread {
                engine
                    .updateProfile(email)
                    .onSuccess { Platform.runLater { refreshProfileUi() } }
                    .onFailure { logger.warn("Update profile failed: {}", it.message) }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onSavePreferences() {
        val emailEnabled = emailNotifCheckbox.isSelected
        val pushEnabled = pushNotifCheckbox.isSelected
        Thread {
                engine.updateNotificationPreferences(emailEnabled, pushEnabled).onFailure {
                    logger.warn("Update notification preferences failed: {}", it.message)
                }
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onDeleteAccount() {
        val ownerStage = usernameField.scene.window as? Stage
        val alert =
            javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete your account? This cannot be undone.",
                javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.NO,
            )
        if (ownerStage != null) alert.initOwner(ownerStage)
        alert.title = "Delete Account"
        val result = alert.showAndWait()
        if (result.isPresent && result.get() == javafx.scene.control.ButtonType.YES) {
            Thread {
                    engine
                        .deleteAccount()
                        .onSuccess { Platform.runLater { ownerStage?.close() } }
                        .onFailure { logger.warn("Delete account failed: {}", it.message) }
                }
                .also { it.isDaemon = true }
                .start()
        }
    }

    private fun loadProfile() {
        Thread {
                engine.loadProfile()
                Platform.runLater { refreshProfileUi() }
            }
            .also { it.isDaemon = true }
            .start()
    }

    private fun refreshProfileUi() {
        val s = engine.state
        usernameField.text = s.userName
        emailField.text = s.userEmail
        emailNotifCheckbox.isSelected = s.emailNotificationsEnabled
        pushNotifCheckbox.isSelected = s.pushNotificationsEnabled
    }
}
