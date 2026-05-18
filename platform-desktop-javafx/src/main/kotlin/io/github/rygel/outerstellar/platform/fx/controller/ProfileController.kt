package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.geometry.Insets
import javafx.scene.Parent
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class ProfileController : KoinComponent {

    private val logger = LoggerFactory.getLogger(ProfileController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    fun createView(): Parent {
        viewModel.loadProfile().runInBackground()

        return VBox(15.0).apply {
            padding = Insets(15.0)
            children.addAll(createProfileSection(), createNotificationSection(), createDangerSection())
        }
    }

    private fun createProfileSection(): VBox {
        val emailField = TextField().apply { textProperty().bindBidirectional(viewModel.userEmail) }
        val usernameField = TextField().apply { textProperty().bindBidirectional(viewModel.userName) }
        val avatarUrlField = TextField().apply { text = viewModel.userAvatarUrl.value ?: "" }

        val saveProfileBtn = Button("Save Profile")
        saveProfileBtn.setOnAction {
            val email = emailField.text.trim()
            if (email.isBlank()) return@setOnAction
            val username = usernameField.text.trim().ifBlank { null }
            val avatarUrl = avatarUrlField.text.trim().ifBlank { null }
            viewModel
                .updateProfile(email, username, avatarUrl)
                .also { task ->
                    task.setOnSucceeded {
                        task.value.onFailure { logger.warn("Update profile failed: {}", it.message) }
                    }
                }
                .runInBackground()
        }

        return VBox(5.0).apply {
            children.addAll(
                Label("Profile Information").apply { style = "-fx-font-weight: bold" },
                Label("Email:"),
                emailField,
                Label("Username:"),
                usernameField,
                Label("Avatar URL:"),
                avatarUrlField,
                saveProfileBtn,
            )
        }
    }

    private fun createNotificationSection(): VBox {
        val emailNotifCheckbox =
            CheckBox("Email notifications").apply {
                selectedProperty().bindBidirectional(viewModel.emailNotificationsEnabled)
            }
        val pushNotifCheckbox =
            CheckBox("Push notifications").apply {
                selectedProperty().bindBidirectional(viewModel.pushNotificationsEnabled)
            }

        val savePrefsBtn = Button("Save Preferences")
        savePrefsBtn.setOnAction {
            viewModel
                .updateNotificationPreferences(emailNotifCheckbox.isSelected, pushNotifCheckbox.isSelected)
                .also { task ->
                    task.setOnSucceeded {
                        task.value.onFailure { logger.warn("Update notification preferences failed: {}", it.message) }
                    }
                }
                .runInBackground()
        }

        return VBox(5.0).apply {
            children.addAll(
                Label("Notification Preferences").apply { style = "-fx-font-weight: bold" },
                emailNotifCheckbox,
                pushNotifCheckbox,
                savePrefsBtn,
            )
        }
    }

    private fun createDangerSection(): VBox {
        val deleteBtn = Button("Delete Account").apply { style = "-fx-text-fill: red" }
        deleteBtn.setOnAction {
            val alert = Alert(Alert.AlertType.CONFIRMATION)
            alert.title = "Delete Account"
            alert.headerText = null
            alert.contentText = "Are you sure you want to delete your account? This cannot be undone."
            alert.buttonTypes.setAll(ButtonType.YES, ButtonType.NO)
            val result = alert.showAndWait()
            if (result.isPresent && result.get() == ButtonType.YES) {
                viewModel
                    .deleteAccount()
                    .also { task ->
                        task.setOnSucceeded {
                            task.value.onSuccess {}.onFailure { logger.warn("Delete account failed: {}", it.message) }
                        }
                    }
                    .runInBackground()
            }
        }

        return VBox(5.0).apply {
            style = "-fx-border-color: red; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10"
            children.addAll(
                Label("Danger Zone").apply { style = "-fx-font-weight: bold" },
                Label("This action cannot be undone."),
                deleteBtn,
            )
        }
    }
}
