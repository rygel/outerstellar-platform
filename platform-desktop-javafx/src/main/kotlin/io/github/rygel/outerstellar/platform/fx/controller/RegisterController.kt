package io.github.rygel.outerstellar.platform.fx.controller

import io.github.rygel.outerstellar.platform.fx.viewmodel.FxSyncViewModel
import io.github.rygel.outerstellar.platform.fx.viewmodel.runInBackground
import javafx.fxml.FXML
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.stage.Stage
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class RegisterController : KoinComponent {

    private val logger = LoggerFactory.getLogger(RegisterController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var passwordField: PasswordField
    @FXML private lateinit var confirmField: PasswordField

    @FXML
    fun onRegister() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        val confirm = confirmField.text
        if (username.isBlank() || password.isBlank()) return
        if (password != confirm) return
        viewModel
            .register(username, password)
            .also { task ->
                task.setOnSucceeded {
                    task.value.onSuccess { close() }.onFailure { logger.warn("Registration failed: {}", it.message) }
                }
            }
            .runInBackground()
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun close() {
        (usernameField.scene.window as? Stage)?.close()
    }
}
