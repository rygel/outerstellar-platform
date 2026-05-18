package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
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
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var emailField: TextField
    @FXML private lateinit var emailNotifCheckbox: CheckBox
    @FXML private lateinit var pushNotifCheckbox: CheckBox
    @FXML private lateinit var deleteAccountBtn: Button

    @FXML
    fun initialize() {
        usernameField.textProperty().bindBidirectional(viewModel.userName)
        emailField.textProperty().bindBidirectional(viewModel.userEmail)
        emailNotifCheckbox.selectedProperty().bindBidirectional(viewModel.emailNotificationsEnabled)
        pushNotifCheckbox.selectedProperty().bindBidirectional(viewModel.pushNotificationsEnabled)
        loadProfile()
    }

    @FXML
    fun onSaveProfile() {
        val email = emailField.text.trim()
        if (email.isBlank()) return
        viewModel
            .updateProfile(email, null, null)
            .also { task ->
                task.setOnSucceeded { task.value.onFailure { logger.warn("Update profile failed: {}", it.message) } }
            }
            .runInBackground()
    }

    @FXML
    fun onSavePreferences() {
        val emailEnabled = emailNotifCheckbox.isSelected
        val pushEnabled = pushNotifCheckbox.isSelected
        viewModel
            .updateNotificationPreferences(emailEnabled, pushEnabled)
            .also { task ->
                task.setOnSucceeded {
                    task.value.onFailure { logger.warn("Update notification preferences failed: {}", it.message) }
                }
            }
            .runInBackground()
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
            viewModel
                .deleteAccount()
                .also { task ->
                    task.setOnSucceeded {
                        task.value
                            .onSuccess { ownerStage?.close() }
                            .onFailure { logger.warn("Delete account failed: {}", it.message) }
                    }
                }
                .runInBackground()
        }
    }

    private fun loadProfile() {
        viewModel.loadProfile().runInBackground()
    }
}
