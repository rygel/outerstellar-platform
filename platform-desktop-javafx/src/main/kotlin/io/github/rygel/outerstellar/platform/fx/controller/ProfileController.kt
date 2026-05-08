package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.sync.SyncService
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProfileController : KoinComponent {

    private val syncService: SyncService by inject()

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
                try {
                    syncService.updateProfile(email)
                    Platform.runLater { loadProfile() }
                } catch (_: Exception) {}
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onSavePreferences() {
        val emailEnabled = emailNotifCheckbox.isSelected
        val pushEnabled = pushNotifCheckbox.isSelected
        Thread {
                try {
                    syncService.updateNotificationPreferences(emailEnabled, pushEnabled)
                } catch (_: Exception) {}
            }
            .also { it.isDaemon = true }
            .start()
    }

    @FXML
    fun onDeleteAccount() {
        val stage = usernameField.scene.window as Stage
        val alert =
            javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Are you sure you want to delete your account? This cannot be undone.",
                javafx.scene.control.ButtonType.YES,
                javafx.scene.control.ButtonType.NO,
            )
        alert.initOwner(stage)
        alert.title = "Delete Account"
        val result = alert.showAndWait()
        if (result.isPresent && result.get() == javafx.scene.control.ButtonType.YES) {
            Thread {
                    try {
                        syncService.deleteAccount()
                        syncService.logout()
                        Platform.runLater { stage.close() }
                    } catch (_: Exception) {}
                }
                .also { it.isDaemon = true }
                .start()
        }
    }

    private fun loadProfile() {
        Thread {
                try {
                    val profile = syncService.fetchProfile()
                    Platform.runLater {
                        usernameField.text = profile.username
                        emailField.text = profile.email
                        emailNotifCheckbox.isSelected = profile.emailNotificationsEnabled
                        pushNotifCheckbox.isSelected = profile.pushNotificationsEnabled
                    }
                } catch (_: Exception) {}
            }
            .also { it.isDaemon = true }
            .start()
    }
}
