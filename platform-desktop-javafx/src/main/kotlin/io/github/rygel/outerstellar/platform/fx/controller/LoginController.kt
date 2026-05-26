package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.FxAppContext
import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Hyperlink
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.layout.VBox

class LoginController(private val onLoginSuccess: () -> Unit, private val onCancel: (() -> Unit)? = null) {

    private val viewModel: FxSyncViewModel
        get() = FxAppContext.viewModel

    private val errorLabel = Label()
    private val usernameField = TextField()
    private val passwordField = PasswordField()
    private val confirmPassField = PasswordField()
    private val actionButton = Button("Sign In")
    private val cancelButton = Button("Cancel")
    private val toggleLink = Hyperlink("Don't have an account? Register")
    private var isRegisterMode = false

    private companion object {
        const val VBOX_SPACING = 15.0
        const val FORM_PADDING = 40.0
        const val FIELD_PREF_WIDTH = 250.0
        const val LOGIN_SCENE_WIDTH = 400.0
        const val LOGIN_SCENE_HEIGHT = 420.0
    }

    fun createScene(): Scene {
        confirmPassField.isVisible = false
        confirmPassField.isManaged = false

        val root =
            VBox(VBOX_SPACING).apply {
                alignment = Pos.CENTER
                padding = Insets(FORM_PADDING)
                children.addAll(
                    Label("Outerstellar").apply { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
                    Label("Sign in to your account").apply {
                        id = "modeLabel"
                        style = "-fx-font-size: 14px;"
                    },
                    usernameField.apply {
                        promptText = "Username"
                        prefWidth = FIELD_PREF_WIDTH
                    },
                    passwordField.apply {
                        promptText = "Password"
                        prefWidth = FIELD_PREF_WIDTH
                        onAction = { onAction() }
                    },
                    confirmPassField.apply {
                        promptText = "Confirm Password"
                        prefWidth = FIELD_PREF_WIDTH
                        onAction = { onAction() }
                    },
                    errorLabel.apply {
                        style = "-fx-text-fill: red;"
                        isVisible = false
                    },
                    actionButton.apply {
                        prefWidth = FIELD_PREF_WIDTH
                        setOnAction { onAction() }
                    },
                    cancelButton.apply {
                        prefWidth = FIELD_PREF_WIDTH
                        isVisible = onCancel != null
                        setOnAction { onCancel?.invoke() }
                    },
                    toggleLink.apply { setOnAction { toggleMode() } },
                )
            }
        return Scene(root, LOGIN_SCENE_WIDTH, LOGIN_SCENE_HEIGHT)
    }

    private fun toggleMode() {
        isRegisterMode = !isRegisterMode
        val showConfirm = isRegisterMode
        confirmPassField.isVisible = showConfirm
        confirmPassField.isManaged = showConfirm
        actionButton.text = if (isRegisterMode) "Register" else "Sign In"
        toggleLink.text = if (isRegisterMode) "Already have an account? Sign In" else "Don't have an account? Register"
        (actionButton.scene?.lookup("#modeLabel") as? Label)?.text =
            if (isRegisterMode) "Create a new account" else "Sign in to your account"
        errorLabel.isVisible = false
    }

    private fun onAction() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        if (username.isBlank() || password.isBlank()) {
            showError("Username and password are required")
            return
        }
        if (isRegisterMode) {
            val confirm = confirmPassField.text
            if (password != confirm) {
                showError("Passwords do not match")
                return
            }
        }
        actionButton.isDisable = true
        errorLabel.isVisible = false
        val task = if (isRegisterMode) viewModel.register(username, password) else viewModel.login(username, password)
        task
            .also { t ->
                t.setOnSucceeded {
                    actionButton.isDisable = false
                    t.value.onSuccess { onLoginSuccess() }.onFailure { e -> showError(e.message ?: "Failed") }
                }
                t.setOnFailed {
                    actionButton.isDisable = false
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
