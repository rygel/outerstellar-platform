package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LoginController(private val onLoginSuccess: () -> Unit, private val onCancel: (() -> Unit)? = null) :
    KoinComponent {

    private val viewModel: FxSyncViewModel by inject()
    private val errorLabel = Label()
    private val usernameField = TextField()
    private val passwordField = PasswordField()
    private val loginButton = Button("Login")
    private val cancelButton = Button("Cancel")

    fun createScene(): Scene {
        val root =
            VBox(SPACING).apply {
                alignment = Pos.CENTER
                padding = Insets(PADDING)
                children.addAll(
                    Label("Outerstellar").apply { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
                    Label("Sign in to your account").apply { style = "-fx-font-size: 14px;" },
                    usernameField.apply {
                        promptText = "Username"
                        prefWidth = FIELD_WIDTH
                    },
                    passwordField.apply {
                        promptText = "Password"
                        prefWidth = FIELD_WIDTH
                        onAction = { onLogin() }
                    },
                    errorLabel.apply {
                        style = "-fx-text-fill: red;"
                        isVisible = false
                    },
                    loginButton.apply {
                        prefWidth = FIELD_WIDTH
                        setOnAction { onLogin() }
                    },
                    cancelButton.apply {
                        prefWidth = FIELD_WIDTH
                        isVisible = onCancel != null
                        setOnAction { onCancel?.invoke() }
                    },
                )
            }
        return Scene(root, SCENE_WIDTH, SCENE_HEIGHT)
    }

    private fun onLogin() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        if (username.isBlank() || password.isBlank()) {
            showError("Username and password are required")
            return
        }
        loginButton.isDisable = true
        errorLabel.isVisible = false
        viewModel
            .login(username, password)
            .also { task ->
                task.setOnSucceeded {
                    loginButton.isDisable = false
                    task.value.onSuccess { onLoginSuccess() }.onFailure { e -> showError(e.message ?: "Login failed") }
                }
                task.setOnFailed {
                    loginButton.isDisable = false
                    showError("Connection error")
                }
            }
            .runInBackground()
    }

    private fun showError(message: String) {
        errorLabel.text = message
        errorLabel.isVisible = true
    }

    companion object {
        private const val SPACING = 15.0
        private const val PADDING = 40.0
        private const val FIELD_WIDTH = 250.0
        private const val SCENE_WIDTH = 400.0
        private const val SCENE_HEIGHT = 350.0
    }
}
