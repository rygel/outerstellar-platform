package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.FxAppContext
import io.github.rygel.outerstellar.platform.fx.service.FxThemeManager
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage

object ChangePasswordController {
    private val viewModel: FxSyncViewModel
        get() = FxAppContext.viewModel

    private val themeManager: FxThemeManager
        get() = FxAppContext.themeManager

    fun show(owner: Stage) {
        val currentField = PasswordField()
        val newField = PasswordField()
        val confirmField = PasswordField()

        val errorLabel = Label()
        errorLabel.style = "-fx-text-fill: red;"
        errorLabel.isVisible = false

        val changeBtn = Button("Change Password")
        changeBtn.isDefaultButton = true
        val cancelBtn = Button("Cancel")
        cancelBtn.isCancelButton = true

        val root =
            VBox(
                10.0,
                Label("Current Password"),
                currentField,
                Label("New Password"),
                newField,
                Label("Confirm New Password"),
                confirmField,
                errorLabel,
                HBox(10.0, changeBtn, cancelBtn),
            )
        root.style = "-fx-padding: 20;"

        val scene = Scene(root)
        themeManager.setScene(scene)

        val stage = Stage()
        stage.initOwner(owner)
        stage.initModality(Modality.APPLICATION_MODAL)
        stage.title = "Change Password"
        stage.scene = scene

        changeBtn.setOnAction {
            val current = currentField.text
            val newPass = newField.text
            val confirm = confirmField.text
            if (current.isBlank() || newPass.isBlank()) {
                errorLabel.text = "All fields are required"
                errorLabel.isVisible = true
                return@setOnAction
            }
            if (newPass != confirm) {
                errorLabel.text = "New passwords do not match"
                errorLabel.isVisible = true
                return@setOnAction
            }
            errorLabel.isVisible = false
            changeBtn.isDisable = true
            viewModel
                .changePassword(current, newPass)
                .also { task ->
                    task.setOnSucceeded {
                        val result = task.value
                        if (result != null) {
                            result.fold(
                                onSuccess = { stage.close() },
                                onFailure = { e ->
                                    errorLabel.text = e.message ?: "Password change failed"
                                    errorLabel.isVisible = true
                                    changeBtn.isDisable = false
                                },
                            )
                        } else {
                            errorLabel.text = "Password change failed"
                            errorLabel.isVisible = true
                            changeBtn.isDisable = false
                        }
                    }
                    task.setOnFailed { e ->
                        errorLabel.text = e.source.exception.message ?: "Password change failed"
                        errorLabel.isVisible = true
                        changeBtn.isDisable = false
                    }
                }
                .runInBackground()
        }

        cancelBtn.setOnAction { stage.close() }

        stage.showAndWait()
    }
}
