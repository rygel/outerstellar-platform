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

class LoginController : KoinComponent {

    private val logger = LoggerFactory.getLogger(LoginController::class.java)
    private val viewModel: FxSyncViewModel by inject()

    @FXML private lateinit var usernameField: TextField
    @FXML private lateinit var passwordField: PasswordField

    var loginSucceeded: Boolean = false
        private set

    @FXML
    fun onLogin() {
        val username = usernameField.text.trim()
        val password = passwordField.text
        if (username.isBlank() || password.isBlank()) return
        performLogin(username, password)
    }

    @FXML
    fun onCancel() {
        close()
    }

    private fun performLogin(user: String, pass: String) {
        viewModel
            .login(user, pass)
            .also { task ->
                task.setOnSucceeded {
                    task.value
                        .onSuccess {
                            loginSucceeded = true
                            close()
                        }
                        .onFailure {
                            logger.warn("Login failed: {}", it.message)
                            loginSucceeded = false
                        }
                }
            }
            .runInBackground()
    }

    private fun close() {
        (usernameField.scene.window as? Stage)?.close()
    }
}
