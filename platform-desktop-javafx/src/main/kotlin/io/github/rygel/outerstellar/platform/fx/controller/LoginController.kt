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
            VBox(15.0).apply {
                alignment = Pos.CENTER
                padding = Insets(40.0)
                children.addAll(
                    Label("Outerstellar").apply { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
                    Label("Sign in to your account").apply { style = "-fx-font-size: 14px;" },
                    usernameField.apply {
                        promptText = "Username"
                        prefWidth = 250.0
                    },
                    passwordField.apply {
                        promptText = "Password"
                        prefWidth = 250.0
                        onAction = { onLogin() }
                    },
                    errorLabel.apply {
                        style = "-fx-text-fill: red;"
                        isVisible = false
                    },
                    loginButton.apply {
                        prefWidth = 250.0
                        setOnAction { onLogin() }
                    },
                    cancelButton.apply {
                        prefWidth = 250.0
                        isVisible = onCancel != null
                        setOnAction { onCancel?.invoke() }
                    },
                )
            }
        return Scene(root, 400.0, 350.0)
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
}
